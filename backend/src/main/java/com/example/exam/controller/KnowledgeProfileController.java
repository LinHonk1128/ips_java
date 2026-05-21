package com.example.exam.controller;

import com.example.exam.dto.KnowledgeProfileDtos.FileProfileResponse;
import com.example.exam.dto.KnowledgeProfileDtos.ActivityResponse;
import com.example.exam.dto.KnowledgeProfileDtos.ChunkSearchResponse;
import com.example.exam.dto.KnowledgeProfileDtos.DiagnosisResponse;
import com.example.exam.dto.KnowledgeProfileDtos.DistributionResponse;
import com.example.exam.dto.KnowledgeProfileDtos.OverviewResponse;
import com.example.exam.dto.KnowledgeProfileDtos.RiskResponse;
import com.example.exam.dto.KnowledgeProfileDtos.SubjectProfileResponse;
import com.example.exam.dto.KnowledgeProfileDtos.TrendPointResponse;
import com.example.exam.dto.KnowledgeProfileDtos.WeakChunkResponse;
import com.example.exam.model.User;
import com.example.exam.service.CurrentUserService;
import com.example.exam.service.KnowledgeProfileService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge-profile")
public class KnowledgeProfileController {
    private final KnowledgeProfileService knowledgeProfileService;
    private final CurrentUserService currentUserService;

    public KnowledgeProfileController(KnowledgeProfileService knowledgeProfileService, CurrentUserService currentUserService) {
        this.knowledgeProfileService = knowledgeProfileService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/overview")
    public OverviewResponse overview() {
        User user = currentUserService.user();
        return knowledgeProfileService.overview(user.getId());
    }

    @GetMapping("/subjects")
    public List<SubjectProfileResponse> subjects() {
        User user = currentUserService.user();
        return knowledgeProfileService.subjects(user.getId());
    }

    @GetMapping("/files")
    public List<FileProfileResponse> files(@RequestParam(required = false) Long folderId) {
        User user = currentUserService.user();
        return knowledgeProfileService.files(user.getId(), folderId);
    }

    @GetMapping("/weak-chunks")
    public List<WeakChunkResponse> weakChunks() {
        User user = currentUserService.user();
        return knowledgeProfileService.weakChunks(user.getId());
    }

    @GetMapping("/trends")
    public List<TrendPointResponse> trends(@RequestParam(defaultValue = "14") int days) {
        User user = currentUserService.user();
        return knowledgeProfileService.trends(user.getId(), days);
    }

    @GetMapping("/distribution")
    public DistributionResponse distribution() {
        User user = currentUserService.user();
        return knowledgeProfileService.distribution(user.getId());
    }

    @GetMapping("/activity")
    public ActivityResponse activity(@RequestParam(defaultValue = "30") int days) {
        User user = currentUserService.user();
        return knowledgeProfileService.activity(user.getId(), days);
    }

    @GetMapping("/risk")
    public RiskResponse risk(@RequestParam(defaultValue = "30") int days,
                             @RequestParam(required = false) Long folderId) {
        User user = currentUserService.user();
        return knowledgeProfileService.risk(user.getId(), days, folderId);
    }

    @GetMapping("/diagnosis")
    public DiagnosisResponse diagnosis(@RequestParam(defaultValue = "30") int days,
                                       @RequestParam(defaultValue = "true") boolean ai) {
        User user = currentUserService.user();
        return knowledgeProfileService.diagnosis(user.getId(), days, ai);
    }

    @GetMapping("/chunks")
    public List<ChunkSearchResponse> searchChunks(@RequestParam(required = false) Long folderId,
                                                  @RequestParam(required = false) Long fileId,
                                                  @RequestParam(required = false) String query,
                                                  @RequestParam(defaultValue = "20") int limit) {
        User user = currentUserService.user();
        return knowledgeProfileService.searchChunks(user.getId(), folderId, fileId, query, limit);
    }
}
