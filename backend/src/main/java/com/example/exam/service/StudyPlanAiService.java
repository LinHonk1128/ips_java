package com.example.exam.service;

import com.example.exam.dto.ChatDtos.ConversationMessage;
import com.example.exam.dto.StudyPlanDtos.StudyPlanAiChatRequest;
import com.example.exam.dto.StudyPlanDtos.StudyPlanAiChatResponse;
import com.example.exam.dto.StudyPlanDtos.StudyPlanApplyRequest;
import com.example.exam.dto.StudyPlanDtos.StudyPlanGenerateRequest;
import com.example.exam.dto.StudyPlanDtos.StudyPlanGenerateResponse;
import com.example.exam.dto.StudyPlanDtos.StudyPlanItemRequest;
import com.example.exam.dto.StudyPlanDtos.StudyPlanItemResponse;
import com.example.exam.dto.StudyPlanDtos.StudyPlanOperationRequest;
import com.example.exam.dto.StudyPlanDtos.StudyPlanOperationResponse;
import com.example.exam.model.StudyPlanSource;
import com.example.exam.model.StudyPlanItem;
import com.example.exam.model.StudyPlanItemType;
import com.example.exam.model.StudyPlanPriority;
import com.example.exam.model.StudyPlanStatus;
import com.example.exam.model.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyPlanAiService {
    private static final Logger log = LoggerFactory.getLogger(StudyPlanAiService.class);
    private static final int PLANNING_MAX_TOKENS = 2400;
    private static final Duration AI_TIMEOUT = Duration.ofSeconds(90);

    private final StudyPlanService studyPlanService;
    private final AiSettingsService aiSettingsService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public StudyPlanAiService(StudyPlanService studyPlanService, AiSettingsService aiSettingsService) {
        this.studyPlanService = studyPlanService;
        this.aiSettingsService = aiSettingsService;
    }

    @Transactional(readOnly = true)
    public StudyPlanAiChatResponse chat(Long userId, StudyPlanAiChatRequest request) {
        List<StudyPlanItem> items = studyPlanService.listEntities(userId, request.fromDate(), request.toDate());
        var settings = mergedSettings(userId, request);
        ModelCallResult result = callModelMessages(settings, buildChatMessages(request.messages(), items), 1600, 0.25);
        String answer = result.content();
        if (answer == null || answer.isBlank()) {
            answer = hasChatApiKey(settings)
                    ? "大模型调用失败：" + (result.error() == null ? "模型返回为空，请检查模型是否支持 Chat Completions 响应格式。" : result.error())
                    : "当前未配置答题 API Key。你可以先在 AI 设置中配置模型服务，然后我会把当前规划作为上下文帮你讨论。";
        }
        return new StudyPlanAiChatResponse(answer.trim());
    }

    @Transactional(readOnly = true)
    public StudyPlanGenerateResponse generate(Long userId, User user, StudyPlanGenerateRequest request) {
        List<StudyPlanItem> before = studyPlanService.listEntities(userId, request.fromDate(), request.toDate());
        var settings = mergedSettings(userId, request);
        ModelCallResult result = callModel(settings, buildGeneratePrompt(request, before), PLANNING_MAX_TOKENS, 0.1);
        String raw = result.content();
        if (raw == null || raw.isBlank()) {
            String reply = hasChatApiKey(settings)
                    ? "大模型暂时没有返回可应用的规划 JSON：" + (result.error() == null ? "模型返回为空。" : result.error())
                    : "当前未配置答题 API Key，无法让大模型生成可应用规划。";
            return new StudyPlanGenerateResponse(reply, List.of(), studyPlanService.list(userId, request.fromDate(), request.toDate()));
        }

        ParsedPlan parsed = parsePlan(raw);
        List<PlanAction> actions = parsed.actions().stream()
                .map(this::jsonToAction)
                .toList();
        List<StudyPlanOperationResponse> operations = actions.stream()
                .map(action -> toOperationResponse(action, "预览中，尚未保存"))
                .toList();
        return new StudyPlanGenerateResponse(parsed.reply(), operations, previewItems(before, actions));
    }

    @Transactional
    public StudyPlanGenerateResponse apply(Long userId, User user, StudyPlanApplyRequest request) {
        List<StudyPlanOperationResponse> operations = new ArrayList<>();
        for (StudyPlanOperationRequest operation : request.operations()) {
            operations.add(applyAction(userId, user, requestToAction(operation)));
        }
        List<StudyPlanItemResponse> items = studyPlanService.list(userId, request.fromDate(), request.toDate());
        return new StudyPlanGenerateResponse("AI 规划已保存到真实日程。", operations, items);
    }

    private StudyPlanOperationResponse applyAction(Long userId, User user, PlanAction action) {
        String operation = action.operation();
        try {
            if ("CREATE".equals(operation)) {
                StudyPlanItemResponse created = studyPlanService.createFromAi(user, actionToRequest(action, null));
                return toOperationResponse(action.withId(created.id()).withTitle(created.title()), "已新增");
            }
            if ("UPDATE".equals(operation)) {
                Long id = requireActionId(action);
                StudyPlanItem current = studyPlanService.requireOwned(id, userId);
                StudyPlanItemResponse updated = studyPlanService.updateFromAi(id, userId, actionToRequest(action, current));
                return toOperationResponse(action.withTitle(updated.title()), "已修改");
            }
            if ("DELETE".equals(operation)) {
                Long id = requireActionId(action);
                StudyPlanItem current = studyPlanService.requireOwned(id, userId);
                String title = current.getTitle();
                studyPlanService.delete(id, userId);
                return toOperationResponse(action.withTitle(title), "已删除");
            }
            return toOperationResponse(action, "已跳过：未知操作");
        } catch (Exception ex) {
            return toOperationResponse(action, "已跳过：" + ex.getMessage());
        }
    }

    private StudyPlanItemRequest actionToRequest(PlanAction action, StudyPlanItem current) {
        LocalDate startDate = action.startDate() == null ? current == null ? null : current.getStartDate() : action.startDate();
        LocalTime startTime = action.startTime() == null ? current == null ? null : current.getStartTime() : action.startTime();
        LocalTime endTime = action.endTime() == null ? current == null ? null : current.getEndTime() : action.endTime();
        return new StudyPlanItemRequest(
                firstNonBlank(action.title(), current == null ? null : current.getTitle()),
                firstNonBlank(action.subject(), current == null ? null : current.getSubject()),
                firstNonBlank(action.description(), current == null ? null : current.getDescription()),
                action.itemType() == null ? current == null ? StudyPlanItemType.SELF_STUDY : current.getItemType() : action.itemType(),
                startDate,
                startTime,
                endTime,
                firstNonBlank(action.location(), current == null ? null : current.getLocation()),
                action.priority() == null ? current == null ? StudyPlanPriority.MEDIUM : current.getPriority() : action.priority(),
                action.status() == null ? current == null ? StudyPlanStatus.TODO : current.getStatus() : action.status()
        );
    }

    private ParsedPlan parsePlan(String raw) {
        try {
            JsonNode root = mapper.readTree(extractJson(raw));
            JsonNode actions = root.path("actions");
            if (!actions.isArray()) {
                throw new IllegalArgumentException("actions 不是数组");
            }
            List<JsonNode> actionNodes = new ArrayList<>();
            actions.forEach(actionNodes::add);
            String reply = text(root, "reply", "已根据对话生成规划调整。");
            return new ParsedPlan(reply, actionNodes);
        } catch (Exception ex) {
            throw new IllegalArgumentException("AI 返回内容不是可解析的规划 JSON：" + ex.getMessage());
        }
    }

    private PlanAction jsonToAction(JsonNode action) {
        String operation = text(action, "operation", "UNKNOWN").toUpperCase(Locale.ROOT);
        return new PlanAction(
                operation,
                action.hasNonNull("id") ? action.path("id").asLong() : null,
                text(action, "title", null),
                text(action, "subject", null),
                text(action, "description", null),
                enumValue(StudyPlanItemType.class, text(action, "itemType", null), null),
                date(action, "startDate", null),
                time(action, "startTime", null),
                time(action, "endTime", null),
                text(action, "location", null),
                enumValue(StudyPlanPriority.class, text(action, "priority", null), null),
                enumValue(StudyPlanStatus.class, text(action, "status", null), null)
        );
    }

    private PlanAction requestToAction(StudyPlanOperationRequest request) {
        String operation = request.operation() == null ? "UNKNOWN" : request.operation().toUpperCase(Locale.ROOT);
        return new PlanAction(
                operation,
                request.id(),
                request.title(),
                request.subject(),
                request.description(),
                request.itemType(),
                request.startDate(),
                request.startTime(),
                request.endTime(),
                request.location(),
                request.priority(),
                request.status()
        );
    }

    private List<StudyPlanItemResponse> previewItems(List<StudyPlanItem> before, List<PlanAction> actions) {
        Map<Long, PreviewItem> preview = new LinkedHashMap<>();
        for (StudyPlanItem item : before) {
            preview.put(item.getId(), PreviewItem.from(item));
        }

        long temporaryId = -1L;
        for (PlanAction action : actions) {
            try {
                if ("CREATE".equals(action.operation())) {
                    StudyPlanItemRequest request = actionToRequest(action, null);
                    preview.put(temporaryId, PreviewItem.from(temporaryId, request, StudyPlanSource.AI));
                    temporaryId -= 1;
                } else if ("UPDATE".equals(action.operation()) && action.id() != null && preview.containsKey(action.id())) {
                    PreviewItem current = preview.get(action.id());
                    StudyPlanItemRequest request = new StudyPlanItemRequest(
                            firstNonBlank(action.title(), current.title()),
                            firstNonBlank(action.subject(), current.subject()),
                            firstNonBlank(action.description(), current.description()),
                            action.itemType() == null ? current.itemType() : action.itemType(),
                            action.startDate() == null ? current.startDate() : action.startDate(),
                            action.startTime() == null ? current.startTime() : action.startTime(),
                            action.endTime() == null ? current.endTime() : action.endTime(),
                            firstNonBlank(action.location(), current.location()),
                            action.priority() == null ? current.priority() : action.priority(),
                            action.status() == null ? current.status() : action.status()
                    );
                    preview.put(action.id(), PreviewItem.from(action.id(), request, StudyPlanSource.AI));
                } else if ("DELETE".equals(action.operation()) && action.id() != null) {
                    preview.remove(action.id());
                }
            } catch (Exception ignored) {
            }
        }

        return preview.values().stream()
                .sorted(Comparator
                        .comparing(PreviewItem::startDate)
                        .thenComparing(PreviewItem::startTime)
                        .thenComparing(PreviewItem::id))
                .map(PreviewItem::toResponse)
                .toList();
    }

    private StudyPlanOperationResponse toOperationResponse(PlanAction action, String detail) {
        return new StudyPlanOperationResponse(
                action.operation(),
                action.id(),
                action.title(),
                action.subject(),
                action.description(),
                action.itemType(),
                action.startDate(),
                action.startTime(),
                action.endTime(),
                action.location(),
                action.priority(),
                action.status(),
                detail
        );
    }

    private Long requireActionId(PlanAction action) {
        if (action.id() == null || action.id() <= 0) {
            throw new IllegalArgumentException("缺少有效 id");
        }
        return action.id();
    }

    private String extractJson(String raw) {
        String cleaned = raw.trim()
                .replaceFirst("(?is)^```json\\s*", "")
                .replaceFirst("(?is)^```\\s*", "")
                .replaceFirst("(?is)```$", "")
                .trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("没有找到 JSON 对象");
        }
        return cleaned.substring(start, end + 1);
    }

    private String buildChatPrompt(List<ConversationMessage> messages, List<StudyPlanItem> items) {
        return """
                你是学习时间规划助手，目标是帮助用户制定可执行、不过载的学习安排。
                你必须把“当前规划”作为上下文，回答时指出冲突、空档、负荷和优先级建议。
                现在只是讨论规划，不要输出 JSON，也不要声称已经修改日程。

                当前规划：
                %s

                对话：
                %s
                """.formatted(planContext(items), conversationTranscript(messages));
    }

    private List<Map<String, String>> buildChatMessages(List<ConversationMessage> messages, List<StudyPlanItem> items) {
        List<Map<String, String>> modelMessages = new ArrayList<>();
        modelMessages.add(Map.of(
                "role", "system",
                "content", """
                        你是学习时间规划助手，目标是帮助用户制定可执行、不过载的学习安排。
                        你必须把“当前规划”作为上下文，回答时指出冲突、空档、负荷和优先级建议。
                        现在只是讨论规划，不要输出 JSON，也不要声称已经修改日程。

                        当前规划：
                        %s
                        """.formatted(planContext(items))
        ));
        if (messages != null) {
            for (ConversationMessage message : messages) {
                if (message.content() == null || message.content().isBlank()) {
                    continue;
                }
                String role = "assistant".equalsIgnoreCase(message.role()) ? "assistant" : "user";
                if (modelMessages.size() == 1 && "assistant".equals(role)) {
                    continue;
                }
                modelMessages.add(Map.of("role", role, "content", message.content()));
            }
        }
        if (modelMessages.size() == 1) {
            modelMessages.add(Map.of("role", "user", "content", "请先根据当前规划给出学习节奏建议。"));
        }
        return modelMessages;
    }

    private String buildGeneratePrompt(StudyPlanGenerateRequest request, List<StudyPlanItem> items) {
        String instruction = request.instruction() == null || request.instruction().isBlank()
                ? "请根据完整对话生成并调整学习规划。"
                : request.instruction().trim();
        return """
                你是学习时间规划助手。请根据用户对话和当前规划，输出一个可以被系统直接执行的 JSON 对象。

                当前日期范围：%s 到 %s

                当前规划：
                %s

                用户对话：
                %s

                本轮规划要求：
                %s

                只允许输出 JSON，不要输出 Markdown 或解释文字。格式必须是：
                {
                  "reply": "用一句话概括你做了什么",
                  "actions": [
                    {
                      "operation": "CREATE | UPDATE | DELETE",
                      "id": 123,
                      "title": "任务标题",
                      "subject": "科目",
                      "description": "安排原因或学习内容",
                      "itemType": "COURSE | SELF_STUDY | REVIEW | EXAM | TASK | REST",
                      "startDate": "YYYY-MM-DD",
                      "startTime": "HH:mm",
                      "endTime": "HH:mm",
                      "location": "地点，可为空",
                      "priority": "LOW | MEDIUM | HIGH",
                      "status": "TODO | DONE | SKIPPED"
                    }
                  ]
                }

                规则：
                1. 如果用户只是提问、信息不足或不需要修改草稿，可以返回空 actions；
                2. CREATE 必须包含 title、startDate、startTime、endTime；
                3. UPDATE 必须包含已有规划 id，未改字段可以省略；
                4. DELETE 必须包含已有规划 id；
                5. 不要安排已过去的时间，不要制造时间重叠，保留必要休息；
                6. 优先围绕学习目标、课程表、复习和错题复盘安排，不要加入与学习无关事项。
                """.formatted(
                request.fromDate(),
                request.toDate(),
                planContext(items),
                conversationTranscript(request.messages()),
                instruction
        );
    }

    private String conversationTranscript(List<ConversationMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "用户还没有提供额外对话。";
        }
        return messages.stream()
                .map(message -> ("assistant".equalsIgnoreCase(message.role()) ? "助手" : "用户") + "：\n" + message.content())
                .collect(java.util.stream.Collectors.joining("\n\n---\n\n"));
    }

    private String planContext(List<StudyPlanItem> items) {
        if (items.isEmpty()) {
            return "当前范围内暂无规划。";
        }
        return items.stream()
                .map(item -> "ID:%d | %s %s-%s | %s | 科目:%s | 类型:%s | 优先级:%s | 状态:%s | 说明:%s".formatted(
                        item.getId(),
                        item.getStartDate(),
                        item.getStartTime(),
                        item.getEndTime(),
                        item.getTitle(),
                        blankToDash(item.getSubject()),
                        item.getItemType(),
                        item.getPriority(),
                        item.getStatus(),
                        blankToDash(item.getDescription())
                ))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private com.example.exam.dto.AiSettingsDtos.AiSettingsResponse mergedSettings(Long userId, StudyPlanAiChatRequest request) {
        return aiSettingsService.merge(
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
    }

    private com.example.exam.dto.AiSettingsDtos.AiSettingsResponse mergedSettings(Long userId, StudyPlanGenerateRequest request) {
        return aiSettingsService.merge(
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
    }

    private ModelCallResult callModel(com.example.exam.dto.AiSettingsDtos.AiSettingsResponse settings,
                                      String prompt,
                                      int maxTokens,
                                      double temperature) {
        return callModelMessages(settings, List.of(Map.of("role", "user", "content", prompt)), maxTokens, temperature);
    }

    private ModelCallResult callModelMessages(com.example.exam.dto.AiSettingsDtos.AiSettingsResponse settings,
                                              List<Map<String, String>> messages,
                                              int maxTokens,
                                              double temperature) {
        if (!hasChatApiKey(settings)) {
            return new ModelCallResult(null, null);
        }
        try {
            String endpoint = normalizeChatCompletionsEndpoint(settings.chatEndpoint());
            Map<String, Object> payload = Map.of(
                    "model", settings.chatModel() == null || settings.chatModel().isBlank() ? "gpt-4o-mini" : settings.chatModel(),
                    "messages", messages,
                    "temperature", temperature,
                    "max_tokens", maxTokens
            );
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(AI_TIMEOUT)
                    .header("Authorization", "Bearer " + settings.chatApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode root = mapper.readTree(response.body());
                String content = firstText(
                        root.at("/choices/0/message/content").asText(null),
                        root.at("/choices/0/message/reasoning_content").asText(null),
                        root.at("/choices/0/text").asText(null)
                );
                return new ModelCallResult(content, content == null || content.isBlank() ? "模型响应中没有 content 字段。" : null);
            }
            return new ModelCallResult(null, "HTTP " + response.statusCode() + "：" + responseBodyExcerpt(response.body()));
        } catch (Exception ignored) {
            log.warn("study plan model call failed", ignored);
            return new ModelCallResult(null, ignored.getClass().getSimpleName() + "：" + ignored.getMessage());
        }
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String responseBodyExcerpt(String body) {
        if (body == null || body.isBlank()) {
            return "响应体为空";
        }
        String normalized = body.replaceAll("\\s+", " ").trim();
        return normalized.length() > 260 ? normalized.substring(0, 260) + "..." : normalized;
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

    private boolean hasChatApiKey(com.example.exam.dto.AiSettingsDtos.AiSettingsResponse settings) {
        return settings.chatApiKey() != null && !settings.chatApiKey().isBlank();
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return fallback;
        String text = value.asText();
        return text == null || text.isBlank() ? fallback : text.trim();
    }

    private Long id(JsonNode node) {
        if (!node.hasNonNull("id")) {
            throw new IllegalArgumentException("缺少 id");
        }
        long value = node.path("id").asLong(0);
        if (value <= 0) {
            throw new IllegalArgumentException("id 无效");
        }
        return value;
    }

    private LocalDate date(JsonNode node, String field, LocalDate fallback) {
        String value = text(node, field, null);
        if (value == null) return fallback;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(field + " 日期格式必须为 YYYY-MM-DD");
        }
    }

    private LocalTime time(JsonNode node, String field, LocalTime fallback) {
        String value = text(node, field, null);
        if (value == null) return fallback;
        try {
            return LocalTime.parse(value.length() == 5 ? value : value.substring(0, 5));
        } catch (Exception ex) {
            throw new IllegalArgumentException(field + " 时间格式必须为 HH:mm");
        }
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record PlanAction(
            String operation,
            Long id,
            String title,
            String subject,
            String description,
            StudyPlanItemType itemType,
            LocalDate startDate,
            LocalTime startTime,
            LocalTime endTime,
            String location,
            StudyPlanPriority priority,
            StudyPlanStatus status
    ) {
        PlanAction withId(Long nextId) {
            return new PlanAction(operation, nextId, title, subject, description, itemType, startDate, startTime, endTime, location, priority, status);
        }

        PlanAction withTitle(String nextTitle) {
            return new PlanAction(operation, id, nextTitle, subject, description, itemType, startDate, startTime, endTime, location, priority, status);
        }
    }

    private record PreviewItem(
            Long id,
            String title,
            String subject,
            String description,
            StudyPlanItemType itemType,
            LocalDate startDate,
            LocalTime startTime,
            LocalTime endTime,
            String location,
            StudyPlanPriority priority,
            StudyPlanStatus status,
            StudyPlanSource source,
            java.time.Instant createdAt,
            java.time.Instant updatedAt
    ) {
        static PreviewItem from(StudyPlanItem item) {
            return new PreviewItem(
                    item.getId(),
                    item.getTitle(),
                    item.getSubject(),
                    item.getDescription(),
                    item.getItemType(),
                    item.getStartDate(),
                    item.getStartTime(),
                    item.getEndTime(),
                    item.getLocation(),
                    item.getPriority(),
                    item.getStatus(),
                    item.getSource(),
                    item.getCreatedAt(),
                    item.getUpdatedAt()
            );
        }

        static PreviewItem from(Long id, StudyPlanItemRequest request, StudyPlanSource source) {
            java.time.Instant now = java.time.Instant.now();
            return new PreviewItem(
                    id,
                    request.title(),
                    request.subject(),
                    request.description(),
                    request.itemType(),
                    request.startDate(),
                    request.startTime(),
                    request.endTime(),
                    request.location(),
                    request.priority(),
                    request.status(),
                    source,
                    now,
                    now
            );
        }

        StudyPlanItemResponse toResponse() {
            return new StudyPlanItemResponse(
                    id,
                    title,
                    subject,
                    description,
                    itemType,
                    startDate,
                    startTime,
                    endTime,
                    location,
                    priority,
                    status,
                    source,
                    createdAt,
                    updatedAt
            );
        }
    }

    private record ModelCallResult(String content, String error) {
    }

    private record ParsedPlan(String reply, List<JsonNode> actions) {
    }
}
