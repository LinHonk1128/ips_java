package com.example.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public class MistakeDtos {
    public record MistakeStatusResponse(Long id, String name, boolean mastered) {
    }

    public record MistakeSubjectTagResponse(Long id, String name) {
    }

    public record CreateMistakeStatusRequest(
            @NotBlank @Size(max = 60) String name
    ) {
    }

    public record CreateMistakeSubjectTagRequest(
            @NotBlank @Size(max = 60) String name
    ) {
    }

    public record UpdateMistakeStatusRequest(
            @NotBlank @Size(max = 60) String name
    ) {
    }

    public record UpdateMistakeStatusSelectionRequest(Boolean mastered, Long statusId) {
    }

    public record RecognizeTextResponse(String text) {
    }

    public record MistakeAttachmentResponse(
            Long id,
            String displayName,
            String originalName,
            String contentType,
            boolean image
    ) {
    }

    public record LinkedChunkResponse(
            Long chunkId,
            String fileName,
            Integer pageNumber,
            String excerpt,
            Double masteryRate,
            Integer citeCount,
            Integer correctHitCount,
            Integer wrongHitCount,
            Instant lastPracticedAt
    ) {
    }

    public record MistakeResponse(
            Long id,
            String questionText,
            String questionOriginalName,
            String questionContentType,
            boolean hasQuestionFile,
            String solutionText,
            String solutionOriginalName,
            String solutionContentType,
            boolean hasSolutionFile,
            java.util.List<MistakeAttachmentResponse> questionAttachments,
            java.util.List<MistakeAttachmentResponse> solutionAttachments,
            java.util.List<MistakeSubjectTagResponse> subjectTags,
            boolean mastered,
            Long statusId,
            String statusName,
            java.util.List<LinkedChunkResponse> linkedChunks,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CreateMistakeFromTeacherRequest(
            @NotNull Long chunkId,
            @NotBlank String questionText,
            String solutionText,
            Boolean feedbackAlreadyForgot,
            List<Long> subjectTagIds
    ) {
    }

    public record PracticeResultRequest(@NotNull Boolean correct) {
    }

    public record PracticeResultResponse(
            Long mistakeId,
            boolean correct,
            int updatedChunkCount,
            List<LinkedChunkResponse> linkedChunks
    ) {
    }
}
