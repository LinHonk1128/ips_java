package com.example.exam.service;

import com.example.exam.dto.AiSettingsDtos.AiSettingsRequest;
import com.example.exam.dto.AiSettingsDtos.AiSettingsResponse;
import com.example.exam.model.User;
import com.example.exam.model.UserAiSettings;
import com.example.exam.repository.UserAiSettingsRepository;
import com.example.exam.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiSettingsService {
    public static final String DEFAULT_AI_ROLE = "严谨的考研答疑老师";
    public static final String DEFAULT_SYSTEM_PROMPT = "优先依据当前知识库回答；给出可追溯依据；如果资料不足，明确说明无法从知识库确认。";
    public static final String DEFAULT_CHAT_MODEL = "gpt-4o-mini";
    public static final String DEFAULT_CHAT_ENDPOINT = "https://api.openai.com/v1/chat/completions";
    public static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-3-small";
    public static final String DEFAULT_EMBEDDING_ENDPOINT = "https://api.openai.com/v1/embeddings";
    public static final int DEFAULT_EMBEDDING_DIMENSIONS = 1536;

    private final UserAiSettingsRepository settingsRepository;
    private final UserRepository userRepository;

    public AiSettingsService(UserAiSettingsRepository settingsRepository, UserRepository userRepository) {
        this.settingsRepository = settingsRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public AiSettingsResponse get(Long userId) {
        return settingsRepository.findByUserId(userId)
                .map(this::toResponse)
                .orElseGet(this::defaultResponse);
    }

    @Transactional
    public AiSettingsResponse save(Long userId, AiSettingsRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        UserAiSettings settings = settingsRepository.findByUserId(userId).orElseGet(UserAiSettings::new);
        settings.setUser(user);
        settings.setAiRole(clean(request.aiRole()));
        settings.setSystemPrompt(clean(request.systemPrompt()));
        settings.setChatModel(clean(request.chatModel()));
        settings.setChatEndpoint(clean(request.chatEndpoint()));
        settings.setChatApiKey(clean(request.chatApiKey()));
        settings.setEmbeddingModel(clean(request.embeddingModel()));
        settings.setEmbeddingEndpoint(clean(request.embeddingEndpoint()));
        settings.setEmbeddingApiKey(clean(request.embeddingApiKey()));
        settings.setEmbeddingDimensions(request.embeddingDimensions() == null
                ? DEFAULT_EMBEDDING_DIMENSIONS
                : request.embeddingDimensions());
        return toResponse(settingsRepository.save(settings));
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
                firstText(systemPrompt, saved.systemPrompt(), DEFAULT_SYSTEM_PROMPT),
                firstText(chatModel, saved.chatModel(), DEFAULT_CHAT_MODEL),
                firstText(chatEndpoint, saved.chatEndpoint(), DEFAULT_CHAT_ENDPOINT),
                firstText(chatApiKey, saved.chatApiKey(), ""),
                firstText(embeddingModel, saved.embeddingModel(), DEFAULT_EMBEDDING_MODEL),
                firstText(embeddingEndpoint, saved.embeddingEndpoint(), DEFAULT_EMBEDDING_ENDPOINT),
                firstText(embeddingApiKey, saved.embeddingApiKey(), ""),
                embeddingDimensions != null ? embeddingDimensions
                        : saved.embeddingDimensions() != null ? saved.embeddingDimensions()
                        : DEFAULT_EMBEDDING_DIMENSIONS
        );
    }

    private AiSettingsResponse toResponse(UserAiSettings settings) {
        return new AiSettingsResponse(
                firstText(settings.getAiRole(), DEFAULT_AI_ROLE),
                firstText(settings.getSystemPrompt(), DEFAULT_SYSTEM_PROMPT),
                firstText(settings.getChatModel(), DEFAULT_CHAT_MODEL),
                firstText(settings.getChatEndpoint(), DEFAULT_CHAT_ENDPOINT),
                firstText(settings.getChatApiKey(), ""),
                firstText(settings.getEmbeddingModel(), DEFAULT_EMBEDDING_MODEL),
                firstText(settings.getEmbeddingEndpoint(), DEFAULT_EMBEDDING_ENDPOINT),
                firstText(settings.getEmbeddingApiKey(), ""),
                settings.getEmbeddingDimensions() > 0 ? settings.getEmbeddingDimensions() : DEFAULT_EMBEDDING_DIMENSIONS
        );
    }

    private AiSettingsResponse defaultResponse() {
        return new AiSettingsResponse(
                DEFAULT_AI_ROLE,
                DEFAULT_SYSTEM_PROMPT,
                DEFAULT_CHAT_MODEL,
                DEFAULT_CHAT_ENDPOINT,
                "",
                DEFAULT_EMBEDDING_MODEL,
                DEFAULT_EMBEDDING_ENDPOINT,
                "",
                DEFAULT_EMBEDDING_DIMENSIONS
        );
    }

    private String firstText(String value, String fallback) {
        return firstText(value, fallback, "");
    }

    private String firstText(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) return first.trim();
        if (second != null && !second.isBlank()) return second.trim();
        return fallback;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
