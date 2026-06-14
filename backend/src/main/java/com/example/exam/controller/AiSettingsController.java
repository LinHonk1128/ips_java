package com.example.exam.controller;

import com.example.exam.dto.AiSettingsDtos.AiSettingsRequest;
import com.example.exam.dto.AiSettingsDtos.AiSettingsPreset;
import com.example.exam.dto.AiSettingsDtos.AiSettingsPresetsRequest;
import com.example.exam.dto.AiSettingsDtos.AiSettingsResponse;
import com.example.exam.model.User;
import com.example.exam.service.AiSettingsService;
import com.example.exam.service.CurrentUserService;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai-settings")
// [SEARCH:API_AI_SETTINGS] 用户模型配置和配置预设接口入口。
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

    @GetMapping("/presets")
    public List<AiSettingsPreset> getPresets() {
        User user = currentUserService.user();
        return aiSettingsService.getPresets(user.getId());
    }

    @PutMapping("/presets")
    public List<AiSettingsPreset> savePresets(@RequestBody AiSettingsPresetsRequest request) {
        User user = currentUserService.user();
        return aiSettingsService.savePresets(user.getId(), request);
    }
}
