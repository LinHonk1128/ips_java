package com.example.exam.dto;

import com.example.exam.model.FileTag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public class FileDtos {
    public record FileResponse(
            Long id,
            Long folderId,
            String originalName,
            FileTag tag,
            String contentType,
            String extractedText,
            boolean knowledgeEnabled,
            int pageCount,
            Instant uploadedAt
    ) {
    }

    public record UpdateFileTextRequest(String originalName, @NotBlank String extractedText, @NotNull FileTag tag) {
    }

    public record UpdateKnowledgeStatusRequest(@NotNull Boolean knowledgeEnabled) {
    }

    public record MoveFileRequest(@NotNull Long folderId) {
    }
}
