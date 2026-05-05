package com.example.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public class FolderDtos {
    public record CreateFolderRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 400) String description,
            Long parentId
    ) {
    }

    public record UpdateFolderRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 400) String description
    ) {
    }

    public record FolderResponse(Long id, String name, String description, Long parentId, int depth, Instant createdAt) {
    }
}
