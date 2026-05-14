package com.example.exam.controller;

import com.example.exam.dto.StudyPlanDtos.StudyPlanAiChatRequest;
import com.example.exam.dto.StudyPlanDtos.StudyPlanAiChatResponse;
import com.example.exam.dto.StudyPlanDtos.StudyPlanApplyRequest;
import com.example.exam.dto.StudyPlanDtos.StudyPlanGenerateRequest;
import com.example.exam.dto.StudyPlanDtos.StudyPlanGenerateResponse;
import com.example.exam.dto.StudyPlanDtos.StudyPlanItemRequest;
import com.example.exam.dto.StudyPlanDtos.StudyPlanItemResponse;
import com.example.exam.model.User;
import com.example.exam.service.CurrentUserService;
import com.example.exam.service.StudyPlanAiService;
import com.example.exam.service.StudyPlanService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/study-plan")
public class StudyPlanController {
    private final StudyPlanService studyPlanService;
    private final StudyPlanAiService studyPlanAiService;
    private final CurrentUserService currentUserService;

    public StudyPlanController(StudyPlanService studyPlanService,
                               StudyPlanAiService studyPlanAiService,
                               CurrentUserService currentUserService) {
        this.studyPlanService = studyPlanService;
        this.studyPlanAiService = studyPlanAiService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<StudyPlanItemResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        User user = currentUserService.user();
        return studyPlanService.list(user.getId(), from, to);
    }

    @PostMapping
    public StudyPlanItemResponse create(@Valid @RequestBody StudyPlanItemRequest request) {
        User user = currentUserService.user();
        return studyPlanService.create(user, request);
    }

    @PutMapping("/{itemId}")
    public StudyPlanItemResponse update(@PathVariable Long itemId, @Valid @RequestBody StudyPlanItemRequest request) {
        User user = currentUserService.user();
        return studyPlanService.update(itemId, user.getId(), request);
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> delete(@PathVariable Long itemId) {
        User user = currentUserService.user();
        studyPlanService.delete(itemId, user.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/ai/chat")
    public StudyPlanAiChatResponse chat(@Valid @RequestBody StudyPlanAiChatRequest request) {
        User user = currentUserService.user();
        return studyPlanAiService.chat(user.getId(), request);
    }

    @PostMapping("/ai/generate")
    public StudyPlanGenerateResponse generate(@Valid @RequestBody StudyPlanGenerateRequest request) {
        User user = currentUserService.user();
        return studyPlanAiService.generate(user.getId(), user, request);
    }

    @PostMapping("/ai/apply")
    public StudyPlanGenerateResponse apply(@Valid @RequestBody StudyPlanApplyRequest request) {
        User user = currentUserService.user();
        return studyPlanAiService.apply(user.getId(), user, request);
    }
}
