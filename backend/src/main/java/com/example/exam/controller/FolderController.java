package com.example.exam.controller;

import com.example.exam.dto.FolderDtos.CreateFolderRequest;
import com.example.exam.dto.FolderDtos.FolderResponse;
import com.example.exam.dto.FolderDtos.UpdateFolderRequest;
import com.example.exam.model.User;
import com.example.exam.service.CurrentUserService;
import com.example.exam.service.FolderService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/folders")
public class FolderController {
    private final FolderService folderService;
    private final CurrentUserService currentUserService;

    public FolderController(FolderService folderService, CurrentUserService currentUserService) {
        this.folderService = folderService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<FolderResponse> list() {
        User user = currentUserService.user();
        return folderService.list(user);
    }

    @PostMapping
    public FolderResponse create(@Valid @RequestBody CreateFolderRequest request) {
        User user = currentUserService.user();
        return folderService.create(user, request);
    }

    @PatchMapping("/{folderId}")
    public FolderResponse update(@PathVariable Long folderId, @Valid @RequestBody UpdateFolderRequest request) {
        User user = currentUserService.user();
        return folderService.update(folderId, user.getId(), request);
    }

    @DeleteMapping("/{folderId}")
    public ResponseEntity<Void> delete(@PathVariable Long folderId) throws IOException {
        User user = currentUserService.user();
        folderService.delete(folderId, user.getId());
        return ResponseEntity.noContent().build();
    }
}
