package com.example.exam.service;

import com.example.exam.dto.FileDtos.FileResponse;
import com.example.exam.dto.FileDtos.UpdateFileTextRequest;
import com.example.exam.model.FileTag;
import com.example.exam.model.KnowledgeChunk;
import com.example.exam.model.StudyFile;
import com.example.exam.model.StudyFolder;
import com.example.exam.repository.KnowledgeChunkRepository;
import com.example.exam.repository.StudyFileRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileService {
    private final StudyFileRepository fileRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final FolderService folderService;
    private final TextExtractionService textExtractionService;
    private final Path uploadDir;

    public FileService(StudyFileRepository fileRepository,
                       KnowledgeChunkRepository chunkRepository,
                       FolderService folderService,
                       TextExtractionService textExtractionService,
                       @Value("${app.upload-dir}") String uploadDir) {
        this.fileRepository = fileRepository;
        this.chunkRepository = chunkRepository;
        this.folderService = folderService;
        this.textExtractionService = textExtractionService;
        this.uploadDir = Path.of(uploadDir);
    }

    @Transactional(readOnly = true)
    public List<FileResponse> list(Long folderId, Long userId) {
        folderService.requireOwned(folderId, userId);
        return fileRepository.findByFolderIdOrderByUploadedAtDesc(folderId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public FileResponse upload(Long folderId, Long userId, FileTag tag, MultipartFile multipartFile) throws IOException {
        StudyFolder folder = folderService.requireOwned(folderId, userId);
        Files.createDirectories(uploadDir.resolve(String.valueOf(userId)).resolve(String.valueOf(folderId)));
        String originalName = multipartFile.getOriginalFilename() == null ? "unnamed" : multipartFile.getOriginalFilename();
        Path target = uploadDir.resolve(String.valueOf(userId)).resolve(String.valueOf(folderId))
                .resolve(UUID.randomUUID() + "-" + originalName.replaceAll("[\\\\/:*?\"<>|]", "_"));
        multipartFile.transferTo(target);

        StudyFile file = new StudyFile();
        file.setFolder(folder);
        file.setOriginalName(originalName);
        file.setStoredPath(target.toString());
        file.setContentType(multipartFile.getContentType() == null ? "application/octet-stream" : multipartFile.getContentType());
        file.setTag(tag == null ? FileTag.OTHER : tag);
        file.setExtractedText(textExtractionService.extract(target, originalName, file.getContentType()));
        fileRepository.save(file);
        rebuildKnowledge(file);
        return toResponse(file);
    }

    @Transactional
    public FileResponse updateText(Long fileId, Long userId, UpdateFileTextRequest request) {
        StudyFile file = fileRepository.findByIdAndFolderOwnerId(fileId, userId)
                .orElseThrow(() -> new IllegalArgumentException("File not found or access denied"));
        file.setExtractedText(request.extractedText());
        file.setTag(request.tag());
        rebuildKnowledge(file);
        return toResponse(file);
    }

    private void rebuildKnowledge(StudyFile file) {
        chunkRepository.deleteByFileId(file.getId());
        String text = file.getExtractedText() == null ? "" : file.getExtractedText().trim();
        if (text.isBlank()) {
            return;
        }
        int chunkSize = 900;
        int index = 0;
        for (int start = 0; start < text.length(); start += chunkSize) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setFile(file);
            chunk.setFolder(file.getFolder());
            chunk.setChunkIndex(index++);
            chunk.setContent(text.substring(start, Math.min(start + chunkSize, text.length())));
            chunkRepository.save(chunk);
        }
    }

    private FileResponse toResponse(StudyFile file) {
        return new FileResponse(file.getId(), file.getFolder().getId(), file.getOriginalName(), file.getTag(),
                file.getContentType(), file.getExtractedText(), file.getUploadedAt());
    }
}
