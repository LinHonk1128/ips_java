package com.example.exam.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class AiSettingsDtos {
    public record AiSettingsRequest(
            String aiRole,
            String systemPrompt,
            String chatModel,
            String chatEndpoint,
            String chatApiKey,
            String embeddingModel,
            String embeddingEndpoint,
            String embeddingApiKey,
            @Min(1) @Max(4096) Integer embeddingDimensions
    ) {
    }

    public record AiSettingsResponse(
            String aiRole,
            String systemPrompt,
            String chatModel,
            String chatEndpoint,
            String chatApiKey,
            String embeddingModel,
            String embeddingEndpoint,
            String embeddingApiKey,
            Integer embeddingDimensions
    ) {
    }
}
