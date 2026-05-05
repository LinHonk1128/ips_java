package com.example.exam.controller;

import com.example.exam.dto.AiSettingsDtos.AiSettingsRequest;
import com.example.exam.dto.AiSettingsDtos.AiSettingsResponse;
import com.example.exam.model.User;
import com.example.exam.service.AiSettingsService;
import com.example.exam.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai-settings")
public class AiSettingsController {
    private final AiSettingsService aiSettingsService;
    private final CurrentUserService currentUserService;

    public AiSettingsController(AiSettingsService aiSettingsService, CurrentUserService currentUserService) {
        this.aiSettingsService = aiSettingsService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public AiSettingsResponse get() {
        User user = currentUserService.user();
        return aiSettingsService.get(user.getId());
    }

    @PutMapping
    public AiSettingsResponse save(@Valid @RequestBody AiSettingsRequest request) {
        User user = currentUserService.user();
        return aiSettingsService.save(user.getId(), request);
    }
}
