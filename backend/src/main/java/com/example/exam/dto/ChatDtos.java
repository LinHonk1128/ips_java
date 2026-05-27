package com.example.exam.dto;

import com.example.exam.model.QuestionMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public class ChatDtos {
    public record ChatRequest(
            Long folderId,
            @NotNull QuestionMode mode,
            @NotBlank String question,
            List<ConversationMessage> messages,
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
            Boolean withCitations,
            Boolean deepAnswer,
            Boolean useKnowledgeBase
    ) {
    }

    public enum ChunkFeedbackType {
        CLEAR,
        FORGOT
    }

    public record Source(
            Integer citationIndex,
            Long chunkId,
            Long fileId,
            Long folderId,
            String fileName,
            Integer pageNumber,
            String excerpt,
            Integer citeCount,
            Integer correctHitCount,
            Integer wrongHitCount,
            Double masteryRate,
            Instant lastAccessedAt,
            Instant lastPracticedAt
    ) {
    }

    public record ChatResponse(String answer, List<Source> sources) {
    }

    public record ChunkFeedbackRequest(@NotNull ChunkFeedbackType type) {
    }

    public record ChunkFeedbackResponse(
            Long chunkId,
            int citeCount,
            int correctHitCount,
            int wrongHitCount,
            double masteryRate,
            Instant lastAccessedAt,
            Instant lastPracticedAt
    ) {
    }

    public record TeacherQuestionRequest(
            @NotNull Long folderId,
            Long subjectFolderId,
            String requirement,
            List<Long> excludeChunkIds,
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

    public record TeacherQuestionResponse(String question, String referenceAnswer, Source source, Long chunkId) {
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
