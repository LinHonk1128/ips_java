package com.example.exam.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public class StudyProfileDtos {
    public record SubjectFolderResponse(
            Long id,
            String name,
            int subjectOrder
    ) {
    }

    public record StudyProfileResponse(
            boolean onboarded,
            LocalDate examDate,
            int subjectCount,
            List<SubjectFolderResponse> subjects
    ) {
    }

    public record OnboardingRequest(
            @NotNull LocalDate examDate,
            @NotNull @Size(min = 1, max = 12) List<@Size(min = 1, max = 120) String> subjects
    ) {
    }

    public record UpdateStudyProfileRequest(
            @NotNull LocalDate examDate,
            @Size(min = 1, max = 12) List<@Size(min = 1, max = 120) String> subjects
    ) {
    }
}
