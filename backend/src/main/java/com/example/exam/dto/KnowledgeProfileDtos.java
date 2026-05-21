package com.example.exam.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class KnowledgeProfileDtos {
    public record OverviewResponse(
            long totalChunkCount,
            long practicedChunkCount,
            double coverageRate,
            double overallMasteryRate,
            long totalCiteCount,
            long totalCorrectHitCount,
            long totalWrongHitCount,
            long weakChunkCount,
            long unpracticedChunkCount,
            long masteredChunkCount,
            long mediumChunkCount,
            long highRiskChunkCount,
            String averageConfidenceLevel,
            long recentPracticeCount,
            long recentCorrectCount,
            long recentWrongCount,
            long reviewDueCount,
            Instant lastAccessedAt,
            LocalDate examDate,
            Long daysUntilExam
    ) {
    }

    public record SubjectProfileResponse(
            Long subjectFolderId,
            String subjectName,
            long chunkCount,
            long practicedChunkCount,
            double coverageRate,
            double masteryRate,
            long citeCount,
            long weakChunkCount,
            long unpracticedChunkCount,
            long masteredChunkCount,
            long mediumChunkCount,
            long highRiskChunkCount,
            String averageConfidenceLevel,
            long recentPracticeCount,
            long recentCorrectCount,
            long recentWrongCount,
            double reviewPressure,
            Instant lastAccessedAt
    ) {
    }

    public record FileProfileResponse(
            Long fileId,
            String fileName,
            long chunkCount,
            long practicedChunkCount,
            double coverageRate,
            double masteryRate,
            long citeCount,
            long weakChunkCount,
            long unpracticedChunkCount,
            long masteredChunkCount,
            long mediumChunkCount,
            long highRiskChunkCount,
            String averageConfidenceLevel,
            long recentPracticeCount,
            long recentCorrectCount,
            long recentWrongCount,
            Instant lastAccessedAt
    ) {
    }

    public record WeakChunkResponse(
            Long chunkId,
            Long fileId,
            String fileName,
            Integer pageNumber,
            String excerpt,
            double masteryRate,
            int correctHitCount,
            int wrongHitCount,
            int citeCount,
            String confidenceLevel,
            Instant lastPracticedAt,
            double reviewPriority
    ) {
    }

    public record ChunkSearchResponse(
            Long chunkId,
            Long fileId,
            String fileName,
            Integer pageNumber,
            String excerpt,
            double masteryRate,
            int citeCount,
            int correctHitCount,
            int wrongHitCount,
            String confidenceLevel,
            Instant lastPracticedAt
    ) {
    }

    public record TrendPointResponse(
            LocalDate date,
            long practiceCount,
            long correctCount,
            long wrongCount,
            long citationCount,
            long mistakePracticeCount,
            long newlyMasteredCount
    ) {
    }

    public record DistributionResponse(
            long unassessed,
            long weak,
            long medium,
            long good,
            long mastered
    ) {
    }

    public record ActivityResponse(
            int days,
            List<TrendPointResponse> daily,
            List<SubjectActivityResponse> subjects
    ) {
    }

    public record SubjectActivityResponse(
            Long subjectFolderId,
            String subjectName,
            long practiceCount,
            long correctCount,
            long wrongCount,
            long citationCount,
            double activityScore
    ) {
    }

    public record RiskResponse(
            int days,
            List<RiskChunkResponse> highRiskChunks,
            List<ReviewPressurePointResponse> pressureTrend,
            List<RiskBubbleResponse> bubbles
    ) {
    }

    public record RiskChunkResponse(
            Long chunkId,
            Long fileId,
            String fileName,
            Integer pageNumber,
            String title,
            String excerpt,
            double masteryRate,
            int correctHitCount,
            int wrongHitCount,
            int citeCount,
            String confidenceLevel,
            Instant lastPracticedAt,
            double riskScore,
            double forgettingRisk
    ) {
    }

    public record ReviewPressurePointResponse(
            LocalDate date,
            double averageRiskScore,
            long dueChunkCount,
            long highRiskChunkCount
    ) {
    }

    public record RiskBubbleResponse(
            Long chunkId,
            String title,
            double masteryRate,
            int citeCount,
            int wrongHitCount,
            double riskScore
    ) {
    }

    public record DiagnosisResponse(
            boolean aiAvailable,
            boolean dataSufficient,
            String confidenceThreshold,
            String summary,
            String aiSummary,
            List<DiagnosisItemResponse> items,
            List<ReviewSuggestionResponse> suggestions
    ) {
    }

    public record DiagnosisItemResponse(
            String label,
            String value,
            String detail,
            String severity
    ) {
    }

    public record ReviewSuggestionResponse(
            Long chunkId,
            Long fileId,
            String title,
            String subjectName,
            String fileName,
            Integer pageNumber,
            String excerpt,
            String reason,
            double riskScore,
            int estimatedMinutes,
            StudyPlanPayload planPayload
    ) {
    }

    public record StudyPlanPayload(
            String title,
            String subject,
            String description,
            String itemType,
            LocalDate startDate,
            String startTime,
            String endTime,
            String priority,
            String status
    ) {
    }

    public record KnowledgeProfileResponse(
            OverviewResponse overview,
            List<SubjectProfileResponse> subjects,
            List<FileProfileResponse> files,
            List<WeakChunkResponse> weakChunks
    ) {
    }
}
