package com.example.exam.service;

import com.example.exam.dto.ChatDtos.ChatRequest;
import com.example.exam.dto.ChatDtos.ChatResponse;
import com.example.exam.dto.ChatDtos.TeacherQuestionRequest;
import com.example.exam.dto.ChatDtos.TeacherQuestionResponse;
import com.example.exam.dto.ChatDtos.ConversationMessage;
import com.example.exam.dto.ChatDtos.NoteRequest;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int MAX_RETRIEVED_CHUNKS = 5;
    private static final int MAX_RERANK_CANDIDATES = 20;
    private static final int MAX_INITIAL_CHUNKS_PER_FILE = 2;
    private static final int DEFAULT_CHAT_MAX_TOKENS = 1600;
    private static final int DIRECT_CHAT_MAX_TOKENS = 2000;
    private static final int DEEP_CHAT_MAX_TOKENS = 2400;
    private static final int QUERY_REWRITE_MAX_TOKENS = 120;
    private static final Duration CHAT_TIMEOUT = Duration.ofSeconds(90);

    private final KnowledgeChunkRepository chunkRepository;
    private final StudyFolderRepository folderRepository;
    private final AiSettingsService aiSettingsService;
    private final ElasticsearchService elasticsearchService;
    private final KnowledgeChunkInteractionService chunkInteractionService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public ChatService(KnowledgeChunkRepository chunkRepository,
                       StudyFolderRepository folderRepository,
                       AiSettingsService aiSettingsService,
                       ElasticsearchService elasticsearchService,
                       KnowledgeChunkInteractionService chunkInteractionService) {
        this.chunkRepository = chunkRepository;
        this.folderRepository = folderRepository;
        this.aiSettingsService = aiSettingsService;
        this.elasticsearchService = elasticsearchService;
        this.chunkInteractionService = chunkInteractionService;
    }

    @Transactional
    public ChatResponse ask(Long userId, ChatRequest request) {
        long started = System.nanoTime();
        boolean useKnowledgeBase = useKnowledgeBase(request);
        StudyFolder folder = useKnowledgeBase ? requireFolder(request.folderId(), userId) : null;
        long folderLoadedAt = System.nanoTime();
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
        long settingsLoadedAt = System.nanoTime();
        boolean withCitations = useKnowledgeBase && (request.withCitations() == null || request.withCitations());
        List<KnowledgeChunk> chunks = useKnowledgeBase
                ? retrieve(folder, userId, request.question(), settings, deepAnswer(request))
                : List.of();
        long retrievedAt = System.nanoTime();
        initializeSourceData(chunks);
        String prompt = useKnowledgeBase
                ? buildPrompt(request.mode(), request.question(), chunks, settings.aiRole(), settings.systemPrompt(), withCitations)
                : buildDirectPrompt(request.mode(), request.question(), settings.aiRole(), settings.systemPrompt());
        long promptBuiltAt = System.nanoTime();
        int responseTokenLimit = responseTokenLimit(request, useKnowledgeBase);
        String answer = callModel(settings, prompt, responseTokenLimit, 0.2);
        long modelCalledAt = System.nanoTime();
        if (answer == null || answer.isBlank()) {
            answer = useKnowledgeBase
                    ? localAnswer(request.mode(), chunks, hasChatApiKey(settings), withCitations)
                    : localDirectAnswer(hasChatApiKey(settings));
        }
        List<Source> sources = useKnowledgeBase && withCitations
                ? chunkInteractionService.recordCitations(userId, buildSources(chunks, request.question(), answer))
                : List.of();
        long finishedAt = System.nanoTime();
        log.info("chat.ask timings userId={} folderId={} knowledgeBase={} deep={} citations={} chunks={} folder={}ms settings={}ms retrieve={}ms prompt={}ms model={}ms sources={}ms total={}ms",
                userId,
                request.folderId(),
                useKnowledgeBase,
                deepAnswer(request),
                withCitations,
                chunks.size(),
                millisBetween(started, folderLoadedAt),
                millisBetween(folderLoadedAt, settingsLoadedAt),
                millisBetween(settingsLoadedAt, retrievedAt),
                millisBetween(retrievedAt, promptBuiltAt),
                millisBetween(promptBuiltAt, modelCalledAt),
                millisBetween(modelCalledAt, finishedAt),
                millisBetween(started, finishedAt));
        return new ChatResponse(answer, sources);
    }

    @Transactional(readOnly = true)
    public SseEmitter askStream(Long userId, ChatRequest request) {
        long started = System.nanoTime();
        SseEmitter emitter = new SseEmitter(CHAT_TIMEOUT.plusSeconds(5).toMillis());
        boolean useKnowledgeBase = useKnowledgeBase(request);
        StudyFolder folder = useKnowledgeBase ? requireFolder(request.folderId(), userId) : null;
        long folderLoadedAt = System.nanoTime();
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
        long settingsLoadedAt = System.nanoTime();
        boolean withCitations = useKnowledgeBase && (request.withCitations() == null || request.withCitations());
        List<KnowledgeChunk> chunks = useKnowledgeBase
                ? retrieve(folder, userId, request.question(), settings, deepAnswer(request))
                : List.of();
        long retrievedAt = System.nanoTime();
        initializeSourceData(chunks);
        String prompt = useKnowledgeBase
                ? buildPrompt(request.mode(), request.question(), chunks, settings.aiRole(), settings.systemPrompt(), withCitations)
                : buildDirectPrompt(request.mode(), request.question(), settings.aiRole(), settings.systemPrompt());
        long promptBuiltAt = System.nanoTime();

        CompletableFuture.runAsync(() -> {
            try {
                sendEvent(emitter, "ready", "ok");
                int responseTokenLimit = responseTokenLimit(request, useKnowledgeBase);
                String answer = callModelStream(settings, prompt, responseTokenLimit, delta -> {
                    sendEvent(emitter, "delta", delta);
                });
                long modelCalledAt = System.nanoTime();
                if (answer == null || answer.isBlank()) {
                    answer = useKnowledgeBase
                            ? localAnswer(request.mode(), chunks, hasChatApiKey(settings), withCitations)
                            : localDirectAnswer(hasChatApiKey(settings));
                    sendEvent(emitter, "delta", answer);
                }
                List<Source> sources = useKnowledgeBase && withCitations
                        ? chunkInteractionService.recordCitations(userId, buildSources(chunks, request.question(), answer))
                        : List.of();
                long finishedAt = System.nanoTime();
                sendEvent(emitter, "done", new ChatResponse(answer, sources));
                emitter.complete();
                log.info("chat.stream timings userId={} folderId={} knowledgeBase={} deep={} citations={} chunks={} folder={}ms settings={}ms retrieve={}ms prompt={}ms model={}ms sources={}ms total={}ms",
                        userId,
                        request.folderId(),
                        useKnowledgeBase,
                        deepAnswer(request),
                        withCitations,
                        chunks.size(),
                        millisBetween(started, folderLoadedAt),
                        millisBetween(folderLoadedAt, settingsLoadedAt),
                        millisBetween(settingsLoadedAt, retrievedAt),
                        millisBetween(retrievedAt, promptBuiltAt),
                        millisBetween(promptBuiltAt, modelCalledAt),
                        millisBetween(modelCalledAt, finishedAt),
                        millisBetween(started, finishedAt));
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    @Transactional(readOnly = true)
    public String summarizeConversationAsNote(Long userId, NoteRequest request) {
        folderRepository.findByIdAndOwnerId(request.folderId(), userId)
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在，或你没有访问权限"));
        if (request.messages() == null || request.messages().isEmpty()) {
            throw new IllegalArgumentException("当前对话为空，无法整理为笔记");
        }
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
        String transcript = conversationTranscript(request.messages());
        String prompt = """
                请把下面这段考研复习问答重新整理成一份结构化学习笔记。

                要求：
                1. 不要简单复制双方对话，要提炼、合并、改写成笔记；
                2. 保留关键概念、推导过程、易错点、结论和后续复习建议；
                3. 使用 Markdown，标题层级清晰，适合直接存入资料文件夹；
                4. 如果对话中有引用编号，可以保留在对应知识点后。

                对话内容：
                %s
                """.formatted(transcript);
        String note = callModel(settings, prompt);
        if (note == null || note.isBlank()) {
            note = localConversationNote(request.messages(), hasChatApiKey(settings));
        }
        return note.trim();
    }

    @Transactional
    public TeacherQuestionResponse teacherQuestion(Long userId, TeacherQuestionRequest request) {
        StudyFolder scopeFolder = requireFolder(request.subjectFolderId() == null ? request.folderId() : request.subjectFolderId(), userId);
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
        KnowledgeChunk selected = selectTeacherChunk(scopeFolder, userId, request.requirement(), request.excludeChunkIds());
        if (selected == null) {
            throw new IllegalArgumentException("当前知识库还没有可用于教师模式出题的知识片段");
        }
        chunkInteractionService.recordCitation(selected);
        String prompt = buildTeacherQuestionPrompt(request.requirement(), selected);
        String generated = callModel(settings, prompt, 900, 0.15);
        TeacherQuestionPayload payload = parseTeacherQuestion(generated, selected);
        Source source = chunkInteractionService.toSource(1, selected, contextualExcerpt(selected.getContent(), searchTerms(request.requirement())));
        return new TeacherQuestionResponse(payload.question(), payload.referenceAnswer(), source, selected.getId());
    }

    private KnowledgeChunk selectTeacherChunk(StudyFolder scopeFolder, Long userId, String requirement, List<Long> excludeChunkIds) {
        List<Long> folderIds = folderScope(scopeFolder, userId);
        List<KnowledgeChunk> candidates = chunkRepository.findExistingByFolderIdInAndOwnerId(folderIds, userId).stream()
                .filter(chunk -> isUsableKnowledgeContent(chunk.getContent()))
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        Set<Long> excluded = excludeChunkIds == null ? Set.of() : new HashSet<>(excludeChunkIds);
        List<KnowledgeChunk> available = candidates.stream()
                .filter(chunk -> !excluded.contains(chunk.getId()))
                .toList();
        if (available.isEmpty()) {
            available = candidates;
        }

        List<String> terms = searchTerms(requirement);
        Map<Long, Integer> relevanceById = new HashMap<>();
        int maxRelevance = 0;
        for (KnowledgeChunk chunk : available) {
            int relevance = terms.isEmpty() ? 1 : score(chunk.getContent(), terms);
            relevanceById.put(chunk.getId(), relevance);
            maxRelevance = Math.max(maxRelevance, relevance);
        }
        int maxCite = available.stream().mapToInt(KnowledgeChunk::getCiteCount).max().orElse(0);
        int relevanceBase = maxRelevance <= 0 ? 1 : maxRelevance;
        int citeBase = Math.max(1, maxCite);

        return available.stream()
                .max(Comparator.comparingDouble(chunk -> teacherChunkScore(chunk, relevanceById.getOrDefault(chunk.getId(), 0),
                        relevanceBase, citeBase)))
                .orElse(available.get(0));
    }

    private double teacherChunkScore(KnowledgeChunk chunk, int relevance, int relevanceBase, int citeBase) {
        double relevanceScore = relevance <= 0 ? 0.05 : Math.min(1.0, relevance / (double) relevanceBase);
        double attentionScore = Math.log1p(chunk.getCiteCount()) / Math.log1p(citeBase);
        double daysSincePractice = chunk.getLastPracticedAt() == null
                ? 14.0
                : Math.max(0.0, Duration.between(chunk.getLastPracticedAt(), Instant.now()).toDays());
        double forgetRisk = Math.min(1.0, daysSincePractice / 14.0);
        double reviewPriority = (1.0 - chunk.getMasteryRate()) * 0.6 + forgetRisk * 0.25 + attentionScore * 0.15;
        return relevanceScore * 0.50 + reviewPriority * 0.45 + Math.random() * 0.05;
    }

    private String buildTeacherQuestionPrompt(String requirement, KnowledgeChunk chunk) {
        String requestText = requirement == null || requirement.isBlank() ? "围绕该知识片段的核心考点" : requirement.trim();
        return """
                你是考研复习老师。请只依据给定知识片段中“明确写出”的信息，围绕用户的提问要求出 1 道问题。

                要求：
                1. 只输出 1 道题；
                2. 问题的答案必须能直接从知识片段中找到，不允许依赖常识、教材外知识或推理补全；
                3. 如果知识片段只是简单提到两个概念，但没有说明二者区别、联系、原因、优缺点或适用场景，不要提问“区别是什么”“为什么”“比较两者”等扩展题；
                4. 如果资料信息较少，就出识记型或定位型问题，例如“片段中提到了哪些……”“片段如何定义……”“片段给出了哪些分类……”；
                5. referenceAnswer 只能复述或概括知识片段中的原有信息；资料没有写出的内容必须回答“当前片段未说明”；
                6. 只输出 JSON：{"question":"...","referenceAnswer":"..."}。

                提问要求：
                %s

                知识片段：
                %s
                """.formatted(requestText, promptExcerpt(chunk.getContent()));
    }

    private TeacherQuestionPayload parseTeacherQuestion(String generated, KnowledgeChunk chunk) {
        if (generated != null && !generated.isBlank()) {
            try {
                String json = generated.trim();
                int start = json.indexOf('{');
                int end = json.lastIndexOf('}');
                if (start >= 0 && end > start) {
                    json = json.substring(start, end + 1);
                }
                JsonNode root = mapper.readTree(json);
                String question = root.path("question").asText("");
                String referenceAnswer = root.path("referenceAnswer").asText("");
                if (!question.isBlank()) {
                    if (needsExplicitEvidence(question) && !hasExplicitEvidence(question, chunk.getContent())) {
                        return safeTeacherQuestion(chunk);
                    }
                    return new TeacherQuestionPayload(question.trim(), referenceAnswer.isBlank() ? excerpt(chunk.getContent()) : referenceAnswer.trim());
                }
            } catch (Exception ignored) {
                if (!generated.isBlank()) {
                    if (needsExplicitEvidence(generated) && !hasExplicitEvidence(generated, chunk.getContent())) {
                        return safeTeacherQuestion(chunk);
                    }
                    return new TeacherQuestionPayload(generated.trim(), excerpt(chunk.getContent()));
                }
            }
        }
        return safeTeacherQuestion(chunk);
    }

    private TeacherQuestionPayload safeTeacherQuestion(KnowledgeChunk chunk) {
        return new TeacherQuestionPayload("根据当前知识片段，请概括其中明确提到的核心概念或分类。", excerpt(chunk.getContent()));
    }

    private boolean needsExplicitEvidence(String question) {
        String text = question == null ? "" : question;
        return text.contains("区别")
                || text.contains("不同")
                || text.contains("比较")
                || text.contains("联系")
                || text.contains("关系")
                || text.contains("为什么")
                || text.contains("原因")
                || text.contains("优缺点")
                || text.contains("适用场景");
    }

    private boolean hasExplicitEvidence(String question, String content) {
        String text = content == null ? "" : content;
        if ((question.contains("区别") || question.contains("不同") || question.contains("比较"))
                && !(text.contains("区别") || text.contains("不同") || text.contains("比较") || text.contains("分别") || text.contains("而"))) {
            return false;
        }
        if ((question.contains("为什么") || question.contains("原因"))
                && !(text.contains("因为") || text.contains("由于") || text.contains("所以") || text.contains("因此") || text.contains("原因"))) {
            return false;
        }
        if ((question.contains("联系") || question.contains("关系"))
                && !(text.contains("联系") || text.contains("关系") || text.contains("相关") || text.contains("依赖"))) {
            return false;
        }
        if ((question.contains("优缺点") || question.contains("适用场景"))
                && !(text.contains("优点") || text.contains("缺点") || text.contains("适用") || text.contains("场景"))) {
            return false;
        }
        return true;
    }

    private record TeacherQuestionPayload(String question, String referenceAnswer) {
    }

    private StudyFolder requireFolder(Long folderId, Long userId) {
        if (folderId == null) {
            throw new IllegalArgumentException("请选择知识库文件夹，或关闭“使用知识库”后直接聊天");
        }
        return folderRepository.findByIdAndOwnerId(folderId, userId)
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在，或你没有访问权限"));
    }

    private boolean useKnowledgeBase(ChatRequest request) {
        return request.useKnowledgeBase() == null || request.useKnowledgeBase();
    }

    private boolean deepAnswer(ChatRequest request) {
        return Boolean.TRUE.equals(request.deepAnswer());
    }

    private int responseTokenLimit(ChatRequest request, boolean useKnowledgeBase) {
        if (!useKnowledgeBase) {
            return DIRECT_CHAT_MAX_TOKENS;
        }
        return deepAnswer(request) ? DEEP_CHAT_MAX_TOKENS : DEFAULT_CHAT_MAX_TOKENS;
    }

    private String conversationTranscript(List<ConversationMessage> messages) {
        return messages.stream()
                .map(message -> {
                    String role = "assistant".equalsIgnoreCase(message.role()) ? "助手" : "用户";
                    return role + "：\n" + message.content();
                })
                .collect(java.util.stream.Collectors.joining("\n\n---\n\n"));
    }

    private String localConversationNote(List<ConversationMessage> messages, boolean modelConfigured) {
        List<String> userQuestions = messages.stream()
                .filter(message -> "user".equalsIgnoreCase(message.role()))
                .map(ConversationMessage::content)
                .filter(content -> content != null && !content.isBlank())
                .toList();
        List<String> assistantAnswers = messages.stream()
                .filter(message -> "assistant".equalsIgnoreCase(message.role()))
                .map(ConversationMessage::content)
                .filter(content -> content != null && !content.isBlank())
                .toList();
        List<String> lines = new ArrayList<>();
        lines.add("# 对话整理笔记");
        if (modelConfigured) {
            lines.add("> 大模型暂时没有返回有效整理结果，以下为本地提炼版。");
        } else {
            lines.add("> 未配置答题 API Key，以下为本地提炼版。");
        }
        lines.add("## 问题线索");
        if (userQuestions.isEmpty()) {
            lines.add("- 本次对话没有可整理的问题。");
        } else {
            userQuestions.forEach(question -> lines.add("- " + excerpt(question)));
        }
        lines.add("## 核心内容");
        if (assistantAnswers.isEmpty()) {
            lines.add("- 暂无助手回答可整理。");
        } else {
            assistantAnswers.forEach(answer -> lines.add("- " + excerpt(answer)));
        }
        lines.add("## 复习建议");
        lines.add("- 回到原资料核对引用片段，并把仍不确定的概念作为下一轮提问入口。");
        return String.join("\n\n", lines);
    }

    private List<Source> buildSources(List<KnowledgeChunk> chunks, String question, String answer) {
        List<String> terms = searchTerms((question == null ? "" : question) + " " + (answer == null ? "" : answer));
        return IntStream.range(0, chunks.size())
                .mapToObj(index -> {
                    KnowledgeChunk chunk = chunks.get(index);
                    return new Source(
                        index + 1,
                        chunk.getId(),
                        chunk.getFile().getId(),
                        chunk.getFolder().getId(),
                        chunk.getFile().getOriginalName(),
                        chunk.getPageNumber(),
                        contextualExcerpt(chunk.getContent(), terms),
                        chunk.getCiteCount(),
                        chunk.getCorrectHitCount(),
                        chunk.getWrongHitCount(),
                        chunk.getMasteryRate(),
                        chunk.getLastAccessedAt(),
                        chunk.getLastPracticedAt());
                })
                .toList();
    }

    private void initializeSourceData(List<KnowledgeChunk> chunks) {
        for (KnowledgeChunk chunk : chunks) {
            chunk.getContent();
            chunk.getFile().getId();
            chunk.getFile().getOriginalName();
            chunk.getFolder().getId();
        }
    }

    private List<KnowledgeChunk> retrieve(StudyFolder folder,
                                          Long userId,
                                          String question,
                                          com.example.exam.dto.AiSettingsDtos.AiSettingsResponse settings,
                                          boolean deepAnswer) {
        List<Long> folderIds = folderScope(folder, userId);
        List<Long> elasticChunkIds = elasticsearchService.hybridSearch(userId, folderIds, question, settings);
        String deepQuery = "";
        if (deepAnswer) {
            deepQuery = buildDeepSearchQuery(question, settings);
        }
        List<KnowledgeChunk> chunks = loadRankedChunks(elasticChunkIds, MAX_RERANK_CANDIDATES);
        if (!deepQuery.isBlank() && !sameQuery(question, deepQuery)) {
            List<KnowledgeChunk> extraChunks = loadRankedChunks(
                    elasticsearchService.hybridSearch(userId, folderIds, deepQuery, settings),
                    MAX_RERANK_CANDIDATES
            );
            chunks = mergeCandidates(MAX_RERANK_CANDIDATES, List.of(chunks, extraChunks));
        }
        if (!chunks.isEmpty()) {
            return rerankAndDiversify(chunks, deepQuery.isBlank() ? question : question + " " + deepQuery);
        }

        List<KnowledgeChunk> all = chunkRepository.findExistingByFolderIdInAndOwnerId(folderIds, userId);
        String rankingQuestion = deepQuery.isBlank() ? question : question + " " + deepQuery;
        List<String> terms = searchTerms(rankingQuestion);
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
                .limit(MAX_RERANK_CANDIDATES)
                .toList();
        List<KnowledgeChunk> fallbackCandidates = candidates.isEmpty()
                ? ranked.stream().limit(MAX_RERANK_CANDIDATES).toList()
                : candidates;
        return rerankAndDiversify(fallbackCandidates, rankingQuestion);
    }

    private List<KnowledgeChunk> loadRankedChunks(List<Long> chunkIds, int limit) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return List.of();
        }
        List<Long> limitedIds = chunkIds.stream().limit(limit).toList();
        Map<Long, KnowledgeChunk> byId = chunkRepository.findAllById(limitedIds).stream()
                .filter(chunk -> chunk.getFile().isKnowledgeEnabled())
                .filter(chunk -> isUsableKnowledgeContent(chunk.getContent()))
                .collect(java.util.stream.Collectors.toMap(KnowledgeChunk::getId, chunk -> chunk));
        return limitedIds.stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private List<KnowledgeChunk> mergeCandidates(int limit, List<List<KnowledgeChunk>> candidateLists) {
        Map<Long, KnowledgeChunk> merged = new LinkedHashMap<>();
        int index = 0;
        boolean hasMore;
        do {
            hasMore = false;
            for (List<KnowledgeChunk> candidates : candidateLists) {
                if (candidates == null || index >= candidates.size()) {
                    continue;
                }
                hasMore = true;
                KnowledgeChunk chunk = candidates.get(index);
                if (merged.putIfAbsent(chunk.getId(), chunk) == null) {
                    if (merged.size() >= limit) {
                        return new ArrayList<>(merged.values());
                    }
                }
            }
            index++;
        } while (hasMore);
        return new ArrayList<>(merged.values());
    }

    private List<KnowledgeChunk> rerankAndDiversify(List<KnowledgeChunk> candidates, String question) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<String> terms = searchTerms(question);
        Map<Long, Integer> originalRank = new HashMap<>();
        Map<Long, KnowledgeChunk> unique = new LinkedHashMap<>();
        for (int i = 0; i < candidates.size(); i++) {
            KnowledgeChunk chunk = candidates.get(i);
            unique.putIfAbsent(chunk.getId(), chunk);
            originalRank.putIfAbsent(chunk.getId(), i);
        }
        List<KnowledgeChunk> reranked = unique.values().stream()
                .sorted(Comparator
                        .comparingDouble((KnowledgeChunk chunk) -> rerankScore(chunk, terms, originalRank.getOrDefault(chunk.getId(), MAX_RERANK_CANDIDATES)))
                        .reversed()
                        .thenComparingInt(chunk -> originalRank.getOrDefault(chunk.getId(), MAX_RERANK_CANDIDATES)))
                .toList();
        return diversifyChunks(reranked, MAX_RETRIEVED_CHUNKS);
    }

    private double rerankScore(KnowledgeChunk chunk, List<String> terms, int originalRank) {
        String content = chunk.getContent() == null ? "" : chunk.getContent();
        String lowerContent = content.toLowerCase(Locale.ROOT);
        String fileName = chunk.getFile().getOriginalName() == null ? "" : chunk.getFile().getOriginalName().toLowerCase(Locale.ROOT);
        double result = score(content, terms) * 2.0;
        for (String term : terms) {
            if (term.length() < 2) {
                continue;
            }
            int contentIndex = lowerContent.indexOf(term.toLowerCase(Locale.ROOT));
            if (contentIndex >= 0) {
                result += Math.max(1.0, 12.0 - Math.min(10.0, contentIndex / 180.0));
            }
            if (term.length() >= 3 && fileName.contains(term.toLowerCase(Locale.ROOT))) {
                result += 4.0;
            }
        }
        result += Math.max(0, MAX_RERANK_CANDIDATES - originalRank) * 0.15;
        result += Math.min(4.0, content.length() / 220.0);
        return result;
    }

    private List<KnowledgeChunk> diversifyChunks(List<KnowledgeChunk> ranked, int limit) {
        List<KnowledgeChunk> selected = new ArrayList<>();
        Set<Long> selectedChunkIds = new HashSet<>();
        Map<Long, Integer> byFile = new LinkedHashMap<>();

        for (KnowledgeChunk chunk : ranked) {
            Long fileId = chunk.getFile().getId();
            if (byFile.getOrDefault(fileId, 0) == 0 && selectedChunkIds.add(chunk.getId())) {
                selected.add(chunk);
                byFile.put(fileId, 1);
            }
            if (selected.size() >= limit) {
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
            if (selected.size() >= limit) {
                return selected;
            }
        }

        for (KnowledgeChunk chunk : ranked) {
            if (selectedChunkIds.add(chunk.getId())) {
                selected.add(chunk);
            }
            if (selected.size() >= limit) {
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

    private String buildDeepSearchQuery(String question, com.example.exam.dto.AiSettingsDtos.AiSettingsResponse settings) {
        if (!hasChatApiKey(settings) || question == null || question.isBlank()) {
            return "";
        }
        String prompt = """
                请把下面的用户问题改写成一行适合知识库检索的查询语句。
                要求：保留关键概念、同义表达、可能出现的教材术语；不要回答问题；不要编号；不要超过 80 个汉字。

                用户问题：
                %s
                """.formatted(question);
        String rewritten = callModel(settings, prompt, QUERY_REWRITE_MAX_TOKENS, 0.1);
        if (rewritten == null || rewritten.isBlank()) {
            return "";
        }
        String cleaned = rewritten
                .replaceAll("(?m)^\\s*[-*\\d.、)）]+\\s*", "")
                .replaceAll("[`\"“”]", "")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned.length() > 240 ? cleaned.substring(0, 240) : cleaned;
    }

    private boolean sameQuery(String left, String right) {
        return normalizeQuery(left).equals(normalizeQuery(right));
    }

    private String normalizeQuery(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
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

    private String buildDirectPrompt(QuestionMode mode, String question, String aiRole, String systemPrompt) {
        String role = aiRole == null || aiRole.isBlank() ? "严谨的考研答疑老师" : aiRole.trim();
        String customInstruction = systemPrompt == null || systemPrompt.isBlank()
                ? "回答要清晰、分点、有结论；不确定时说明不确定，不要编造。"
                : systemPrompt.trim();
        if (mode == QuestionMode.TEACHER) {
            return """
                    角色：
                    %s

                    补充要求：
                    %s

                    用户已选择不引用知识库。请直接作为考研复习老师和用户互动：可以解释概念、追问薄弱点，或围绕用户输入提出 1 个循序渐进的问题。
                    不要输出资料引用编号。

                    用户输入：
                    %s
                    """.formatted(role, customInstruction, question);
        }
        return """
                角色：
                %s

                补充要求：
                %s

                用户已选择不引用知识库。请直接基于你的通用能力回答用户问题，用简体中文，结构清晰；如果不确定，请明确说明不确定。
                不要输出资料引用编号。

                用户问题：
                %s
                """.formatted(role, customInstruction, question);
    }

    private String buildPrompt(QuestionMode mode, String question, List<KnowledgeChunk> chunks, String aiRole, String systemPrompt, boolean withCitations) {
        String context = numberedContext(chunks);
        String role = aiRole == null || aiRole.isBlank() ? "严谨的考研答疑老师" : aiRole.trim();
        String customInstruction = systemPrompt == null || systemPrompt.isBlank()
                ? "优先依据当前知识库回答；给出可追溯依据；如果资料不足，明确说明无法从知识库确认。"
                : systemPrompt.trim();
        if (!withCitations) {
            if (mode == QuestionMode.TEACHER) {
                return """
                        角色：
                        %s

                        补充要求：
                        %s

                        你正在进行考研复习抽问。请只依据下方知识库内容，用简体中文向用户提出 1 个循序渐进的问题。
                        回答中不要输出引用编号或来源标记。如果知识库不足以出题，请直接说明“当前知识库资料不足，无法确认出题依据”，不要编造。

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
                    回答要清晰、分点、有结论，但不要输出引用编号、来源标记或文末参考列表。
                    如果知识库里没有足够依据，请明确说“无法从当前知识库确认”，不要用常识或猜测补全。

                    知识库：
                    %s

                    用户问题：
                    %s
                    """.formatted(role, customInstruction, context, question);
        }
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
                    return "资料片段 [%d]\n文件：%s\n页码：第 %d 页\n内容：\n%s".formatted(
                            index + 1,
                            chunk.getFile().getOriginalName(),
                            chunk.getPageNumber(),
                            promptExcerpt(chunk.getContent())
                    );
                })
                .collect(java.util.stream.Collectors.joining("\n---\n"));
    }

    private String promptExcerpt(String content) {
        String normalized = content == null ? "" : content.replaceAll("\\s+", " ").trim();
        return normalized.length() > 600 ? normalized.substring(0, 600) + "..." : normalized;
    }

    private String callModel(com.example.exam.dto.AiSettingsDtos.AiSettingsResponse settings, String prompt) {
        return callModel(settings, prompt, DEFAULT_CHAT_MAX_TOKENS, 0.2);
    }

    private String callModel(com.example.exam.dto.AiSettingsDtos.AiSettingsResponse settings,
                             String prompt,
                             int maxTokens,
                             double temperature) {
        if (!hasChatApiKey(settings)) {
            return null;
        }
        try {
            String endpoint = normalizeChatCompletionsEndpoint(settings.chatEndpoint());
            Map<String, Object> payload = Map.of(
                    "model", settings.chatModel() == null || settings.chatModel().isBlank() ? "gpt-4o-mini" : settings.chatModel(),
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "temperature", temperature,
                    "max_tokens", maxTokens
            );
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(CHAT_TIMEOUT)
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

    private String callModelStream(com.example.exam.dto.AiSettingsDtos.AiSettingsResponse settings,
                                   String prompt,
                                   int maxTokens,
                                   Consumer<String> onDelta) {
        if (!hasChatApiKey(settings)) {
            return null;
        }
        try {
            String endpoint = normalizeChatCompletionsEndpoint(settings.chatEndpoint());
            Map<String, Object> payload = Map.of(
                    "model", settings.chatModel() == null || settings.chatModel().isBlank() ? "gpt-4o-mini" : settings.chatModel(),
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "temperature", 0.2,
                    "max_tokens", maxTokens,
                    "stream", true
            );
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(CHAT_TIMEOUT)
                    .header("Authorization", "Bearer " + settings.chatApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<Stream<String>> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            StringBuilder answer = new StringBuilder();
            try (Stream<String> lines = response.body()) {
                lines.forEach(line -> appendStreamDelta(line, answer, onDelta));
            }
            return answer.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void appendStreamDelta(String line, StringBuilder answer, Consumer<String> onDelta) {
        if (line == null || !line.startsWith("data:")) {
            return;
        }
        String data = line.substring("data:".length()).trim();
        if (data.isBlank() || "[DONE]".equals(data)) {
            return;
        }
        try {
            JsonNode root = mapper.readTree(data);
            String delta = root.at("/choices/0/delta/content").asText("");
            if (!delta.isEmpty()) {
                answer.append(delta);
                onDelta.accept(delta);
            }
        } catch (Exception ignored) {
        }
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to send chat stream event", ex);
        }
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

    private String localDirectAnswer(boolean modelConfigured) {
        if (modelConfigured) {
            return "大模型接口暂时没有返回有效内容，请检查 API Key、模型名和 Endpoint。当前已关闭知识库检索，因此无法使用本地知识库兜底回答。";
        }
        return "当前未配置答题 API Key，无法直接和大模型聊天。你可以先配置模型服务，或重新开启“使用知识库”来使用本地检索兜底回答。";
    }

    private String localAnswer(QuestionMode mode, List<KnowledgeChunk> chunks, boolean modelConfigured, boolean withCitations) {
        if (chunks.isEmpty()) {
            return "当前知识库还没有可用内容。请先上传资料，检查自动抽取文本是否有效，再保存为知识库。";
        }
        String first = excerpt(chunks.get(0).getContent());
        String prefix = modelConfigured
                ? "大模型接口暂时没有返回有效内容，请检查 API Key、模型名和 Endpoint。下面先给出本地检索摘要：\n\n"
                : "";
        String citation = withCitations ? " [1]" : "";
        if (mode == QuestionMode.TEACHER) {
            return prefix + "教师模式建议先围绕这段资料追问：请你先解释其中的核心概念，再说明它常见的考法。" + citation + "\n\n依据：" + first;
        }
        List<String> lines = new ArrayList<>();
        if (!prefix.isBlank()) {
            lines.add(prefix.trim());
        }
        lines.add("基于当前知识库，检索到的最相关内容是：" + citation);
        lines.add(first + citation);
        lines.add("你可以继续追问：这部分有哪些易错点，或者让它整理成考试答题模板。");
        return String.join("\n\n", lines);
    }

    private boolean hasChatApiKey(com.example.exam.dto.AiSettingsDtos.AiSettingsResponse settings) {
        return settings.chatApiKey() != null && !settings.chatApiKey().isBlank();
    }

    private long millisBetween(long startNanos, long endNanos) {
        return Duration.ofNanos(endNanos - startNanos).toMillis();
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
