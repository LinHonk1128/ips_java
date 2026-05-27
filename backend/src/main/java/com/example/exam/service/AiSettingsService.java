package com.example.exam.service;

import com.example.exam.dto.AiSettingsDtos.AiSettingsRequest;
import com.example.exam.dto.AiSettingsDtos.AiSettingsPreset;
import com.example.exam.dto.AiSettingsDtos.AiSettingsPresetsRequest;
import com.example.exam.dto.AiSettingsDtos.AiSettingsResponse;
import com.example.exam.model.User;
import com.example.exam.model.UserAiSettings;
import com.example.exam.repository.UserAiSettingsRepository;
import com.example.exam.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiSettingsService {
    public static final String DEFAULT_AI_ROLE = "严谨的考研答疑老师";
    public static final String DEFAULT_SYSTEM_PROMPT = """
            请优先依据当前知识库内容回答，回答要符合考研学生的理解水平，不要直接复制资料原文，要用更容易理解的方式解释。

            如果知识库内容与问题高度相关：请基于资料内容回答，可以适当改写、举例或梳理逻辑，但不要加入资料无法支持的结论。
            如果知识库内容只提供了部分依据：请先说明“根据当前资料可以确定的是……”，再把可确认内容讲清楚。如需补充通用知识，请单独标明“补充理解”，并避免把补充内容说成资料原文依据。
            如果知识库内容不足或无关：请明确说明“无法从当前知识库确认”，可以提示用户补充资料或换一种问法，不要编造。
            """;
    private static final List<String> LEGACY_DEFAULT_SYSTEM_PROMPTS = List.of(
            "优先依据当前知识库回答；给出可追溯依据；如果资料不足，明确说明无法从知识库确认。",
            "优先依据当前知识库回答；每个结论尽量给出来源；如果资料不足，请直接说明无法从当前资料确认。",
            "优先依据当前知识库回答；每个结论尽量给出来源；资料不足时明确说明。"
    );
    public static final String DEFAULT_CHAT_MODEL = "deepseek-v4-flash";
    public static final String DEFAULT_CHAT_ENDPOINT = "https://api.deepseek.com/v1/chat/completions";
    public static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-v4";
    public static final String DEFAULT_EMBEDDING_ENDPOINT = "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings";
    public static final int DEFAULT_EMBEDDING_DIMENSIONS = 1536;

    private final UserAiSettingsRepository settingsRepository;
    private final UserRepository userRepository;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String backendChatModel;
    private final String backendChatEndpoint;
    private final String backendChatApiKey;
    private final String backendEmbeddingModel;
    private final String backendEmbeddingEndpoint;
    private final String backendEmbeddingApiKey;
    private final int backendEmbeddingDimensions;

    public AiSettingsService(UserAiSettingsRepository settingsRepository,
                             UserRepository userRepository,
                             @Value("${app.ai.chat.model:" + DEFAULT_CHAT_MODEL + "}") String backendChatModel,
                             @Value("${app.ai.chat.endpoint:" + DEFAULT_CHAT_ENDPOINT + "}") String backendChatEndpoint,
                             @Value("${app.ai.chat.api-key:}") String backendChatApiKey,
                             @Value("${app.ai.embedding.model:" + DEFAULT_EMBEDDING_MODEL + "}") String backendEmbeddingModel,
                             @Value("${app.ai.embedding.endpoint:" + DEFAULT_EMBEDDING_ENDPOINT + "}") String backendEmbeddingEndpoint,
                             @Value("${app.ai.embedding.api-key:}") String backendEmbeddingApiKey,
                             @Value("${app.ai.embedding.dimensions:" + DEFAULT_EMBEDDING_DIMENSIONS + "}") int backendEmbeddingDimensions) {
        this.settingsRepository = settingsRepository;
        this.userRepository = userRepository;
        this.backendChatModel = firstText(backendChatModel, DEFAULT_CHAT_MODEL);
        this.backendChatEndpoint = firstText(backendChatEndpoint, DEFAULT_CHAT_ENDPOINT);
        this.backendChatApiKey = clean(backendChatApiKey);
        this.backendEmbeddingModel = firstText(backendEmbeddingModel, DEFAULT_EMBEDDING_MODEL);
        this.backendEmbeddingEndpoint = firstText(backendEmbeddingEndpoint, DEFAULT_EMBEDDING_ENDPOINT);
        this.backendEmbeddingApiKey = clean(backendEmbeddingApiKey);
        this.backendEmbeddingDimensions = normalizeEmbeddingDimensions(backendEmbeddingDimensions);
    }

    @Transactional(readOnly = true)
    public AiSettingsResponse get(Long userId) {
        return settingsRepository.findByUserId(userId)
                .map(this::toResponse)
                .orElseGet(this::defaultResponse);
    }

    @Transactional
    public AiSettingsResponse save(Long userId, AiSettingsRequest request) {
        UserAiSettings settings = getOrCreateSettings(userId);
        settings.setAiRole(clean(request.aiRole()));
        settings.setSystemPrompt(normalizeSystemPrompt(request.systemPrompt()));
        settings.setChatModel("");
        settings.setChatEndpoint("");
        settings.setChatApiKey("");
        settings.setEmbeddingModel("");
        settings.setEmbeddingEndpoint("");
        settings.setEmbeddingApiKey("");
        settings.setEmbeddingDimensions(backendEmbeddingDimensions);
        return toResponse(settingsRepository.save(settings));
    }

    @Transactional(readOnly = true)
    public List<AiSettingsPreset> getPresets(Long userId) {
        return settingsRepository.findByUserId(userId)
                .map(settings -> readPresets(settings.getPresetsJson()))
                .orElseGet(List::of);
    }

    @Transactional
    public List<AiSettingsPreset> savePresets(Long userId, AiSettingsPresetsRequest request) {
        UserAiSettings settings = getOrCreateSettings(userId);
        List<AiSettingsPreset> presets = normalizePresets(request == null ? null : request.presets());
        settings.setPresetsJson(writePresets(presets));
        settingsRepository.save(settings);
        return presets;
    }

    public AiSettingsResponse merge(Long userId,
                                    String aiRole,
                                    String systemPrompt,
                                    String chatModel,
                                    String chatEndpoint,
                                    String chatApiKey,
                                    String embeddingModel,
                                    String embeddingEndpoint,
                                    String embeddingApiKey,
                                    Integer embeddingDimensions) {
        AiSettingsResponse saved = get(userId);
        return new AiSettingsResponse(
                firstText(aiRole, saved.aiRole(), DEFAULT_AI_ROLE),
                firstText(normalizeSystemPrompt(systemPrompt), normalizeSystemPrompt(saved.systemPrompt()), DEFAULT_SYSTEM_PROMPT),
                backendChatModel,
                backendChatEndpoint,
                backendChatApiKey,
                backendEmbeddingModel,
                backendEmbeddingEndpoint,
                backendEmbeddingApiKey,
                backendEmbeddingDimensions
        );
    }

    public AiSettingsResponse effective(Long userId) {
        AiSettingsResponse saved = get(userId);
        return new AiSettingsResponse(
                firstText(saved.aiRole(), DEFAULT_AI_ROLE),
                firstText(normalizeSystemPrompt(saved.systemPrompt()), DEFAULT_SYSTEM_PROMPT),
                backendChatModel,
                backendChatEndpoint,
                backendChatApiKey,
                backendEmbeddingModel,
                backendEmbeddingEndpoint,
                backendEmbeddingApiKey,
                backendEmbeddingDimensions
        );
    }

    private AiSettingsResponse toResponse(UserAiSettings settings) {
        return new AiSettingsResponse(
                firstText(settings.getAiRole(), DEFAULT_AI_ROLE),
                firstText(normalizeSystemPrompt(settings.getSystemPrompt()), DEFAULT_SYSTEM_PROMPT),
                "",
                "",
                "",
                "",
                "",
                "",
                backendEmbeddingDimensions
        );
    }

    private AiSettingsResponse defaultResponse() {
        return new AiSettingsResponse(
                DEFAULT_AI_ROLE,
                DEFAULT_SYSTEM_PROMPT,
                "",
                "",
                "",
                "",
                "",
                "",
                backendEmbeddingDimensions
        );
    }

    private UserAiSettings getOrCreateSettings(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        UserAiSettings settings = settingsRepository.findByUserId(userId).orElseGet(UserAiSettings::new);
        settings.setUser(user);
        return settings;
    }

    private List<AiSettingsPreset> readPresets(String presetsJson) {
        if (presetsJson == null || presetsJson.isBlank()) return List.of();
        try {
            return normalizePresets(mapper.readValue(presetsJson, new TypeReference<List<AiSettingsPreset>>() {
            }));
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private String writePresets(List<AiSettingsPreset> presets) {
        try {
            return mapper.writeValueAsString(presets);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid AI settings presets");
        }
    }

    private List<AiSettingsPreset> normalizePresets(List<AiSettingsPreset> presets) {
        if (presets == null) return List.of();
        return presets.stream()
                .map(this::normalizePreset)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(AiSettingsPreset::updatedAt).reversed())
                .limit(30)
                .toList();
    }

    private AiSettingsPreset normalizePreset(AiSettingsPreset preset) {
        if (preset == null || preset.name() == null || preset.name().isBlank() || preset.settings() == null) {
            return null;
        }
        String id = preset.id() == null || preset.id().isBlank() ? UUID.randomUUID().toString() : preset.id().trim();
        String name = preset.name().trim();
        if (name.length() > 60) name = name.substring(0, 60);
        return new AiSettingsPreset(
                id,
                name,
                normalizeRequest(preset.settings()),
                preset.updatedAt() == null || preset.updatedAt() <= 0 ? System.currentTimeMillis() : preset.updatedAt()
        );
    }

    private AiSettingsRequest normalizeRequest(AiSettingsRequest request) {
        return new AiSettingsRequest(
                clean(request.aiRole()),
                normalizeSystemPrompt(request.systemPrompt()),
                "",
                "",
                "",
                "",
                "",
                "",
                backendEmbeddingDimensions
        );
    }

    private int normalizeEmbeddingDimensions(Integer value) {
        if (value == null) return DEFAULT_EMBEDDING_DIMENSIONS;
        return Math.max(1, Math.min(4096, value));
    }

    private String firstText(String value, String fallback) {
        return firstText(value, fallback, "");
    }

    private String firstText(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) return first.trim();
        if (second != null && !second.isBlank()) return second.trim();
        return fallback;
    }

    private String normalizeSystemPrompt(String value) {
        String cleaned = clean(value);
        return LEGACY_DEFAULT_SYSTEM_PROMPTS.contains(cleaned) ? "" : cleaned;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
