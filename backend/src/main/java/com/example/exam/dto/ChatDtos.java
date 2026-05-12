package com.example.exam.dto;

import com.example.exam.model.QuestionMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class ChatDtos {
    public record ChatRequest(
            @NotNull Long folderId,
            @NotNull QuestionMode mode,
            @NotBlank String question,
            String model,
            String apiKey,
            String endpoint,
            String aiRole,
            String systemPrompt,
            String chatModel,
            String chatEndpoint,
            String chatApiKey,
            String embeddingModel,
            String embeddingEndpoint,
            String embeddingApiKey,
            Integer embeddingDimensions,
            Boolean withCitations
    ) {
    }

    public record Source(Integer citationIndex, Long fileId, Long folderId, String fileName, Integer pageNumber, String excerpt) {
    }

    public record ChatResponse(String answer, List<Source> sources) {
    }

    public record ConversationMessage(@NotBlank String role, @NotBlank String content) {
    }

    public record NoteRequest(
            @NotNull Long folderId,
            @NotNull QuestionMode mode,
            @NotNull List<ConversationMessage> messages,
            String model,
            String apiKey,
            String endpoint,
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
