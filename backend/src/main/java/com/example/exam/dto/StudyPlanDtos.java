package com.example.exam.dto;

import com.example.exam.dto.ChatDtos.ConversationMessage;
import com.example.exam.model.StudyPlanItemType;
import com.example.exam.model.StudyPlanPriority;
import com.example.exam.model.StudyPlanSource;
import com.example.exam.model.StudyPlanStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class StudyPlanDtos {
    public record StudyPlanItemRequest(
            @NotBlank @Size(max = 120) String title,
            @Size(max = 120) String subject,
            @Size(max = 800) String description,
            StudyPlanItemType itemType,
            @NotNull LocalDate startDate,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @Size(max = 120) String location,
            StudyPlanPriority priority,
            StudyPlanStatus status
    ) {
    }

    public record StudyPlanItemResponse(
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
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record StudyPlanAiChatRequest(
            LocalDate fromDate,
            LocalDate toDate,
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

    public record StudyPlanAiChatResponse(String answer) {
    }

    public record StudyPlanGenerateRequest(
            LocalDate fromDate,
            LocalDate toDate,
            @NotNull List<ConversationMessage> messages,
            String instruction,
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

    public record StudyPlanOperationRequest(
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
    }

    public record StudyPlanOperationResponse(
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
            StudyPlanStatus status,
            String detail
    ) {
    }

    public record StudyPlanGenerateResponse(
            String reply,
            List<StudyPlanOperationResponse> operations,
            List<StudyPlanItemResponse> items
    ) {
    }

    public record StudyPlanApplyRequest(
            LocalDate fromDate,
            LocalDate toDate,
            @NotNull List<StudyPlanOperationRequest> operations
    ) {
    }
}
