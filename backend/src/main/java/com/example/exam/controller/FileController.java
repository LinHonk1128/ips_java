package com.example.exam.controller;

import com.example.exam.dto.FileDtos.FileResponse;
import com.example.exam.dto.FileDtos.UpdateFileTextRequest;
import com.example.exam.model.FileTag;
import com.example.exam.model.User;
import com.example.exam.service.CurrentUserService;
import com.example.exam.service.FileService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class FileController {
    private final FileService fileService;
    private final CurrentUserService currentUserService;

    public FileController(FileService fileService, CurrentUserService currentUserService) {
        this.fileService = fileService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/folders/{folderId}/files")
    public List<FileResponse> list(@PathVariable Long folderId) {
        User user = currentUserService.user();
        return fileService.list(folderId, user.getId());
    }

    @GetMapping("/files/{fileId}")
    public FileResponse get(@PathVariable Long fileId) {
        User user = currentUserService.user();
        return fileService.get(fileId, user.getId());
    }

    @PostMapping("/folders/{folderId}/files")
    public FileResponse upload(@PathVariable Long folderId,
                               @RequestParam(defaultValue = "OTHER") FileTag tag,
                               @RequestParam MultipartFile file) throws IOException {
        User user = currentUserService.user();
        return fileService.upload(folderId, user.getId(), tag, file);
    }

    @PutMapping("/files/{fileId}")
    public FileResponse updateText(@PathVariable Long fileId, @Valid @RequestBody UpdateFileTextRequest request) {
        User user = currentUserService.user();
        return fileService.updateText(fileId, user.getId(), request);
    }

    @DeleteMapping("/files/{fileId}")
    public ResponseEntity<Void> delete(@PathVariable Long fileId) throws IOException {
        User user = currentUserService.user();
        fileService.delete(fileId, user.getId());
        return ResponseEntity.noContent().build();
    }
}
