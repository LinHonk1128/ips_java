package com.example.exam.controller;

import com.example.exam.dto.StudyProfileDtos.OnboardingRequest;
import com.example.exam.dto.StudyProfileDtos.StudyProfileResponse;
import com.example.exam.dto.StudyProfileDtos.UpdateStudyProfileRequest;
import com.example.exam.model.User;
import com.example.exam.service.CurrentUserService;
import com.example.exam.service.StudyProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/study-profile")
public class StudyProfileController {
    private final StudyProfileService studyProfileService;
    private final CurrentUserService currentUserService;

    public StudyProfileController(StudyProfileService studyProfileService, CurrentUserService currentUserService) {
        this.studyProfileService = studyProfileService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public StudyProfileResponse get() {
        User user = currentUserService.user();
        return studyProfileService.getOrCreate(user.getId());
    }

    @PostMapping("/onboarding")
    public StudyProfileResponse onboard(@Valid @RequestBody OnboardingRequest request) {
        User user = currentUserService.user();
        return studyProfileService.onboard(user.getId(), request);
    }

    @PutMapping
    public StudyProfileResponse update(@Valid @RequestBody UpdateStudyProfileRequest request) {
        User user = currentUserService.user();
        return studyProfileService.update(user.getId(), request);
    }
}
