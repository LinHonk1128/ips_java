package com.example.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

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
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
