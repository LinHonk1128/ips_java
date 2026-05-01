package com.example.exam.service;

import com.example.exam.dto.ChatDtos.ChatRequest;
import com.example.exam.dto.ChatDtos.ChatResponse;
import com.example.exam.dto.ChatDtos.Source;
import com.example.exam.model.KnowledgeChunk;
import com.example.exam.model.QuestionMode;
import com.example.exam.model.StudyFolder;
import com.example.exam.repository.KnowledgeChunkRepository;
import com.example.exam.repository.StudyFolderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {
    private final KnowledgeChunkRepository chunkRepository;
    private final StudyFolderRepository folderRepository;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public ChatService(KnowledgeChunkRepository chunkRepository, StudyFolderRepository folderRepository) {
        this.chunkRepository = chunkRepository;
        this.folderRepository = folderRepository;
    }

    @Transactional(readOnly = true)
    public ChatResponse ask(Long userId, ChatRequest request) {
        StudyFolder folder = folderRepository.findByIdAndOwnerId(request.folderId(), userId)
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在，或你没有访问权限"));
        List<KnowledgeChunk> chunks = retrieve(folder, userId, request.question());
        List<Source> sources = chunks.stream()
                .limit(5)
                .map(chunk -> new Source(
                        chunk.getFile().getId(),
                        chunk.getFolder().getId(),
                        chunk.getFile().getOriginalName(),
                        excerpt(chunk.getContent())))
                .toList();
        String prompt = buildPrompt(request.mode(), request.question(), chunks, request.aiRole(), request.systemPrompt());
        String answer = callModel(request, prompt);
        if (answer == null || answer.isBlank()) {
            answer = localAnswer(request.mode(), chunks, hasApiKey(request));
        }
        return new ChatResponse(answer, sources);
    }

    private List<KnowledgeChunk> retrieve(StudyFolder folder, Long userId, String question) {
        List<KnowledgeChunk> all = chunkRepository.findExistingByFolderIdInAndOwnerId(folderScope(folder, userId), userId);
        List<String> terms = searchTerms(question);
        List<KnowledgeChunk> ranked = all.stream()
                .filter(chunk -> isUsableKnowledgeContent(chunk.getContent()))
                .sorted(Comparator
                        .comparingInt((KnowledgeChunk chunk) -> score(chunk.getContent(), terms)).reversed()
                        .thenComparing(chunk -> chunk.getFile().getUploadedAt(), Comparator.reverseOrder())
                        .thenComparing(KnowledgeChunk::getChunkIndex))
                .toList();

        List<KnowledgeChunk> diverse = new ArrayList<>();
        Set<Long> seenFileIds = new HashSet<>();
        for (KnowledgeChunk chunk : ranked) {
            if (seenFileIds.add(chunk.getFile().getId())) {
                diverse.add(chunk);
            }
            if (diverse.size() >= 8) {
                return diverse;
            }
        }
        for (KnowledgeChunk chunk : ranked) {
            boolean alreadySelected = diverse.stream().anyMatch(selected -> selected.getId().equals(chunk.getId()));
            if (!alreadySelected) {
                diverse.add(chunk);
            }
            if (diverse.size() >= 8) {
                break;
            }
        }
        return diverse;
    }

    private List<Long> folderScope(StudyFolder folder, Long userId) {
        List<StudyFolder> ownedFolders = folderRepository.findByOwnerIdOrderByCreatedAtDesc(userId);
        Set<Long> scope = new HashSet<>();
        scope.add(folder.getId());
        boolean changed;
        do {
            changed = false;
            for (StudyFolder candidate : ownedFolders) {
                StudyFolder parent = candidate.getParent();
                if (parent != null && scope.contains(parent.getId()) && scope.add(candidate.getId())) {
                    changed = true;
                }
            }
        } while (changed);
        return new ArrayList<>(scope);
    }

    private List<String> searchTerms(String question) {
        String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT).trim();
        Set<String> terms = new HashSet<>();
        if (!normalized.isBlank()) {
            terms.add(normalized);
        }
        for (String term : normalized.split("\\s+|,|\\.|;|:|，|。|；|：|、|\\?|？|!|！")) {
            if (!term.isBlank()) {
                terms.add(term);
            }
        }
        String compact = normalized.replaceAll("\\s+", "");
        for (int i = 0; i + 2 <= compact.length(); i++) {
            terms.add(compact.substring(i, i + 2));
        }
        return terms.stream().filter(term -> !term.isBlank()).toList();
    }

    private int score(String content, List<String> terms) {
        String lower = content.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            if (lower.contains(term)) {
                score += Math.max(1, term.length());
            }
        }
        return score;
    }

    private String buildPrompt(QuestionMode mode, String question, List<KnowledgeChunk> chunks, String aiRole, String systemPrompt) {
        String context = String.join("\n---\n", chunks.stream().map(KnowledgeChunk::getContent).toList());
        String role = aiRole == null || aiRole.isBlank() ? "严谨的考研答疑老师" : aiRole.trim();
        String customInstruction = systemPrompt == null || systemPrompt.isBlank()
                ? "优先依据当前知识库回答；给出可追溯依据；如果资料不足，明确说明无法从知识库确认。"
                : systemPrompt.trim();
        if (mode == QuestionMode.TEACHER) {
            return """
                    角色：
                    %s

                    补充要求：
                    %s

                    你正在进行考研复习抽问。请只依据下方知识库内容，用简体中文向用户提出 1 个循序渐进的问题。
                    如果知识库不足以出题，请直接说明“当前知识库资料不足，无法确认出题依据”，不要编造。

                    知识库：
                    %s

                    用户输入：
                    %s
                    """.formatted(role, customInstruction, context, question);
        }
        return """
                角色：
                %s

                补充要求：
                %s

                你是考研知识库答疑助手。请只依据下方知识库内容，用简体中文回答用户问题。
                回答要清晰、分点、有结论，并在合适位置说明依据来自哪些资料片段。
                如果知识库里没有足够依据，请明确说“无法从当前知识库确认”，不要用常识或猜测补全。

                知识库：
                %s

                用户问题：
                %s
                """.formatted(role, customInstruction, context, question);
    }

    private String callModel(ChatRequest request, String prompt) {
        if (!hasApiKey(request)) {
            return null;
        }
        try {
            String endpoint = normalizeChatCompletionsEndpoint(request.endpoint());
            Map<String, Object> payload = Map.of(
                    "model", request.model() == null || request.model().isBlank() ? "gpt-4o-mini" : request.model(),
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "temperature", 0.3
            );
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(endpoint))
                    .header("Authorization", "Bearer " + request.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode root = mapper.readTree(response.body());
                return root.at("/choices/0/message/content").asText();
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private String normalizeChatCompletionsEndpoint(String endpoint) {
        String value = endpoint == null || endpoint.isBlank()
                ? "https://api.openai.com/v1/chat/completions"
                : endpoint.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.endsWith("/chat/completions")) {
            return value;
        }
        if (value.endsWith("/v1") || value.endsWith("/compatible-mode/v1")) {
            return value + "/chat/completions";
        }
        return value;
    }

    private String localAnswer(QuestionMode mode, List<KnowledgeChunk> chunks, boolean modelConfigured) {
        if (chunks.isEmpty()) {
            return "当前知识库还没有可用内容。请先上传资料，检查自动抽取文本是否有效，再保存为知识库。";
        }
        String first = excerpt(chunks.get(0).getContent());
        String prefix = modelConfigured
                ? "大模型接口暂时没有返回有效内容，请检查 API Key、模型名和 Endpoint。下面先给出本地检索摘要：\n\n"
                : "";
        if (mode == QuestionMode.TEACHER) {
            return prefix + "教师模式建议先围绕这段资料追问：请你先解释其中的核心概念，再说明它常见的考法。\n\n依据：" + first;
        }
        List<String> lines = new ArrayList<>();
        if (!prefix.isBlank()) {
            lines.add(prefix.trim());
        }
        lines.add("基于当前知识库，检索到的最相关内容是：");
        lines.add(first);
        lines.add("你可以继续追问：这部分有哪些易错点，或者让它整理成考试答题模板。");
        return String.join("\n\n", lines);
    }

    private boolean hasApiKey(ChatRequest request) {
        return request.apiKey() != null && !request.apiKey().isBlank();
    }

    private boolean isUsableKnowledgeContent(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        return !lower.contains("automatic extraction failed:")
                && !lower.contains("cannot run tesseract")
                && !lower.contains("ocr did not finish")
                && !lower.contains("this file type is not supported for automatic extraction yet")
                && !lower.contains("edit the text manually, then save it to the knowledge base")
                && !content.contains("自动抽取失败：")
                && !content.contains("OCR 没有完成")
                && !content.contains("当前文件类型暂不支持自动抽取")
                && !content.contains("请手动编辑正文后，再保存为知识库")
                && !content.contains("请在这里粘贴或编辑正文，然后保存为知识库");
    }

    private String excerpt(String content) {
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() > 220 ? normalized.substring(0, 220) + "..." : normalized;
    }
}
