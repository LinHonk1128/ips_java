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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {
    private static final int MAX_RETRIEVED_CHUNKS = 8;
    private static final int MAX_INITIAL_CHUNKS_PER_FILE = 2;
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[(?:来源|片段)?(\\d+)]");

    private final KnowledgeChunkRepository chunkRepository;
    private final StudyFolderRepository folderRepository;
    private final AiSettingsService aiSettingsService;
    private final ElasticsearchService elasticsearchService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public ChatService(KnowledgeChunkRepository chunkRepository,
                       StudyFolderRepository folderRepository,
                       AiSettingsService aiSettingsService,
                       ElasticsearchService elasticsearchService) {
        this.chunkRepository = chunkRepository;
        this.folderRepository = folderRepository;
        this.aiSettingsService = aiSettingsService;
        this.elasticsearchService = elasticsearchService;
    }

    @Transactional(readOnly = true)
    public ChatResponse ask(Long userId, ChatRequest request) {
        StudyFolder folder = folderRepository.findByIdAndOwnerId(request.folderId(), userId)
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在，或你没有访问权限"));
        var settings = aiSettingsService.merge(
                userId,
                request.aiRole(),
                request.systemPrompt(),
                request.chatModel() == null ? request.model() : request.chatModel(),
                request.chatEndpoint() == null ? request.endpoint() : request.chatEndpoint(),
                request.chatApiKey() == null ? request.apiKey() : request.chatApiKey(),
                request.embeddingModel(),
                request.embeddingEndpoint(),
                request.embeddingApiKey(),
                request.embeddingDimensions()
        );
        List<KnowledgeChunk> chunks = retrieve(folder, userId, request.question(), settings);
        String prompt = buildPrompt(request.mode(), request.question(), chunks, settings.aiRole(), settings.systemPrompt());
        String answer = callModel(settings, prompt);
        if (needsCitationRewrite(answer, chunks)) {
            String rewritten = callModel(settings, buildCitationRewritePrompt(prompt, answer, chunks));
            if (rewritten != null && !rewritten.isBlank()) {
                answer = rewritten;
            }
        }
        if (answer == null || answer.isBlank()) {
            answer = localAnswer(request.mode(), chunks, hasChatApiKey(settings));
        }
        List<Source> sources = buildSources(chunks, request.question(), answer);
        return new ChatResponse(answer, sources);
    }

    private List<Source> buildSources(List<KnowledgeChunk> chunks, String question, String answer) {
        List<String> terms = searchTerms((question == null ? "" : question) + " " + (answer == null ? "" : answer));
        return IntStream.range(0, chunks.size())
                .mapToObj(index -> {
                    KnowledgeChunk chunk = chunks.get(index);
                    return new Source(
                        index + 1,
                        chunk.getFile().getId(),
                        chunk.getFolder().getId(),
                        chunk.getFile().getOriginalName(),
                        contextualExcerpt(chunk.getContent(), terms));
                })
                .toList();
    }

    private List<KnowledgeChunk> retrieve(StudyFolder folder, Long userId, String question, com.example.exam.dto.AiSettingsDtos.AiSettingsResponse settings) {
        List<Long> folderIds = folderScope(folder, userId);
        List<Long> elasticChunkIds = elasticsearchService.hybridSearch(userId, folderIds, question, settings);
        if (!elasticChunkIds.isEmpty()) {
            Map<Long, KnowledgeChunk> byId = chunkRepository.findAllById(elasticChunkIds).stream()
                    .filter(chunk -> chunk.getFile().isKnowledgeEnabled())
                    .filter(chunk -> isUsableKnowledgeContent(chunk.getContent()))
                    .collect(java.util.stream.Collectors.toMap(KnowledgeChunk::getId, chunk -> chunk));
            List<KnowledgeChunk> chunks = elasticChunkIds.stream()
                    .map(byId::get)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (!chunks.isEmpty()) {
                return diversifyChunks(chunks);
            }
        }

        List<KnowledgeChunk> all = chunkRepository.findExistingByFolderIdInAndOwnerId(folderIds, userId);
        List<String> terms = searchTerms(question);
        List<KnowledgeChunk> ranked = all.stream()
                .filter(chunk -> isUsableKnowledgeContent(chunk.getContent()))
                .sorted(Comparator
                        .comparingInt((KnowledgeChunk chunk) -> score(chunk.getContent(), terms)).reversed()
                        .thenComparing(chunk -> chunk.getFile().getUploadedAt(), Comparator.reverseOrder())
                        .thenComparing(KnowledgeChunk::getChunkIndex))
                .toList();
        int bestScore = ranked.stream()
                .mapToInt(chunk -> score(chunk.getContent(), terms))
                .max()
                .orElse(0);
        int minimumScore = bestScore <= 0 ? 0 : Math.max(1, bestScore / 5);

        List<KnowledgeChunk> candidates = ranked.stream()
                .filter(chunk -> score(chunk.getContent(), terms) >= minimumScore)
                .toList();
        return diversifyChunks(candidates.isEmpty() ? ranked : candidates);
    }

    private List<KnowledgeChunk> diversifyChunks(List<KnowledgeChunk> ranked) {
        List<KnowledgeChunk> selected = new ArrayList<>();
        Set<Long> selectedChunkIds = new HashSet<>();
        Map<Long, Integer> byFile = new LinkedHashMap<>();

        for (KnowledgeChunk chunk : ranked) {
            Long fileId = chunk.getFile().getId();
            if (byFile.getOrDefault(fileId, 0) == 0 && selectedChunkIds.add(chunk.getId())) {
                selected.add(chunk);
                byFile.put(fileId, 1);
            }
            if (selected.size() >= MAX_RETRIEVED_CHUNKS) {
                return selected;
            }
        }

        for (KnowledgeChunk chunk : ranked) {
            Long fileId = chunk.getFile().getId();
            int count = byFile.getOrDefault(fileId, 0);
            if (count < MAX_INITIAL_CHUNKS_PER_FILE && selectedChunkIds.add(chunk.getId())) {
                selected.add(chunk);
                byFile.put(fileId, count + 1);
            }
            if (selected.size() >= MAX_RETRIEVED_CHUNKS) {
                return selected;
            }
        }

        for (KnowledgeChunk chunk : ranked) {
            if (selectedChunkIds.add(chunk.getId())) {
                selected.add(chunk);
            }
            if (selected.size() >= MAX_RETRIEVED_CHUNKS) {
                return selected;
            }
        }
        return selected;
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
        String context = numberedContext(chunks);
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
                    每个关键判断后必须紧跟对应资料编号，例如：[1] 或 [2]。不要把引用集中放在末尾。
                    如果下方有多个资料片段或多个文件支持不同要点，必须优先使用不同编号交叉佐证，不要整段只引用同一个编号。
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
                回答要清晰、分点、有结论。每个定义、分类、结论或例子后必须紧跟对应资料编号，例如：[1] 或 [2]。
                不要把引用集中放在回答末尾；引用必须贴在它支持的句子或条目后面。
                如果多个资料片段或多个文件分别支持不同要点，必须在对应句子后使用不同编号交叉引用；不要整篇回答只使用同一个编号。
                如果知识库里没有足够依据，请明确说“无法从当前知识库确认”，不要用常识或猜测补全。

                知识库：
                %s

                用户问题：
                %s
                """.formatted(role, customInstruction, context, question);
    }

    private String numberedContext(List<KnowledgeChunk> chunks) {
        return IntStream.range(0, chunks.size())
                .mapToObj(index -> {
                    KnowledgeChunk chunk = chunks.get(index);
                    return "资料片段 [%d]\n文件：%s\n内容：\n%s".formatted(
                            index + 1,
                            chunk.getFile().getOriginalName(),
                            chunk.getContent()
                    );
                })
                .collect(java.util.stream.Collectors.joining("\n---\n"));
    }

    private boolean needsCitationRewrite(String answer, List<KnowledgeChunk> chunks) {
        if (answer == null || answer.isBlank() || chunks.size() <= 1) {
            return false;
        }
        int expectedCitationCount = Math.min(3, chunks.size());
        if (chunks.stream().map(chunk -> chunk.getFile().getId()).distinct().count() > 1) {
            expectedCitationCount = Math.min(expectedCitationCount, 2);
        }
        return distinctCitationCount(answer) < expectedCitationCount;
    }

    private int distinctCitationCount(String answer) {
        Set<Integer> citations = new HashSet<>();
        var matcher = CITATION_PATTERN.matcher(answer == null ? "" : answer);
        while (matcher.find()) {
            citations.add(Integer.parseInt(matcher.group(1)));
        }
        return citations.size();
    }

    private String buildCitationRewritePrompt(String originalPrompt, String answer, List<KnowledgeChunk> chunks) {
        int requiredCitationCount = Math.min(3, chunks.size());
        if (chunks.stream().map(chunk -> chunk.getFile().getId()).distinct().count() > 1) {
            requiredCitationCount = Math.min(requiredCitationCount, 2);
        }
        return originalPrompt + """

                上一次回答只引用了很少的资料编号，证据覆盖不足。请在保留原意的基础上重写回答：
                1. 至少使用 %d 个不同资料编号；
                2. 不同要点引用不同片段，尤其是定义、分类、性能指标、层次结构等要点；
                3. 引用仍然必须贴在对应句子或条目后，不要集中放在末尾；
                4. 不要引用资料中没有支持的内容。

                上一次回答：
                %s
                """.formatted(requiredCitationCount, answer);
    }

    private String callModel(com.example.exam.dto.AiSettingsDtos.AiSettingsResponse settings, String prompt) {
        if (!hasChatApiKey(settings)) {
            return null;
        }
        try {
            String endpoint = normalizeChatCompletionsEndpoint(settings.chatEndpoint());
            Map<String, Object> payload = Map.of(
                    "model", settings.chatModel() == null || settings.chatModel().isBlank() ? "gpt-4o-mini" : settings.chatModel(),
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "temperature", 0.3
            );
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(endpoint))
                    .header("Authorization", "Bearer " + settings.chatApiKey())
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
            return prefix + "教师模式建议先围绕这段资料追问：请你先解释其中的核心概念，再说明它常见的考法。[1]\n\n依据：" + first;
        }
        List<String> lines = new ArrayList<>();
        if (!prefix.isBlank()) {
            lines.add(prefix.trim());
        }
        lines.add("基于当前知识库，检索到的最相关内容是：[1]");
        lines.add(first + " [1]");
        lines.add("你可以继续追问：这部分有哪些易错点，或者让它整理成考试答题模板。");
        return String.join("\n\n", lines);
    }

    private boolean hasChatApiKey(com.example.exam.dto.AiSettingsDtos.AiSettingsResponse settings) {
        return settings.chatApiKey() != null && !settings.chatApiKey().isBlank();
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

    private String contextualExcerpt(String content, List<String> terms) {
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 220) {
            return normalized;
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        int bestIndex = -1;
        int bestLength = 0;
        for (String term : terms.stream()
                .filter(term -> term.length() >= 2)
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList()) {
            int index = lower.indexOf(term.toLowerCase(Locale.ROOT));
            if (index >= 0) {
                bestIndex = index;
                bestLength = term.length();
                break;
            }
        }
        if (bestIndex < 0) {
            return excerpt(content);
        }

        int start = Math.max(0, bestIndex - 70);
        int end = Math.min(normalized.length(), bestIndex + bestLength + 150);
        String excerpt = normalized.substring(start, end);
        return (start > 0 ? "..." : "") + excerpt + (end < normalized.length() ? "..." : "");
    }
}
