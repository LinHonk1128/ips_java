package com.example.exam.service;

import com.example.exam.dto.FileDtos.FileResponse;
import com.example.exam.dto.FileDtos.MoveFileRequest;
import com.example.exam.dto.FileDtos.UpdateKnowledgeStatusRequest;
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
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileService {
    private static final int PAGE_SIZE = 3500;

    private final StudyFileRepository fileRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final FolderService folderService;
    private final TextExtractionService textExtractionService;
    private final AiSettingsService aiSettingsService;
    private final ElasticsearchService elasticsearchService;
    private final Path uploadDir;

    public FileService(StudyFileRepository fileRepository,
                       KnowledgeChunkRepository chunkRepository,
                       FolderService folderService,
                       TextExtractionService textExtractionService,
                       AiSettingsService aiSettingsService,
                       ElasticsearchService elasticsearchService,
                       @Value("${app.upload-dir}") String uploadDir) {
        this.fileRepository = fileRepository;
        this.chunkRepository = chunkRepository;
        this.folderService = folderService;
        this.textExtractionService = textExtractionService;
        this.aiSettingsService = aiSettingsService;
        this.elasticsearchService = elasticsearchService;
        this.uploadDir = Path.of(uploadDir);
    }

    @Transactional(readOnly = true)
    public List<FileResponse> list(Long folderId, Long userId) {
        folderService.requireOwned(folderId, userId);
        return fileRepository.findByFolderIdOrderByUploadedAtDesc(folderId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public FileResponse get(Long fileId, Long userId) {
        StudyFile file = fileRepository.findByIdAndFolderOwnerId(fileId, userId)
                .orElseThrow(() -> new IllegalArgumentException("File not found or access denied"));
        return toResponse(file);
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
        String extractedText = textExtractionService.extract(target, originalName, file.getContentType());
        if (textExtractionService.isExtractionPlaceholder(extractedText)) {
            Files.deleteIfExists(target);
            throw new IllegalArgumentException(extractedText);
        }
        file.setExtractedText(extractedText);
        file.setKnowledgeEnabled(true);
        fileRepository.save(file);
        rebuildKnowledge(file, userId);
        return toResponse(file);
    }

    @Transactional
    public FileResponse createTextNote(Long folderId, Long userId, String originalName, String content) throws IOException {
        StudyFolder folder = folderService.requireOwned(folderId, userId);
        Files.createDirectories(uploadDir.resolve(String.valueOf(userId)).resolve(String.valueOf(folderId)));
        String safeName = originalName == null || originalName.isBlank() ? "对话整理笔记.md" : originalName;
        Path target = uploadDir.resolve(String.valueOf(userId)).resolve(String.valueOf(folderId))
                .resolve(UUID.randomUUID() + "-" + safeName.replaceAll("[\\\\/:*?\"<>|]", "_"));
        Files.writeString(target, content == null ? "" : content);

        StudyFile file = new StudyFile();
        file.setFolder(folder);
        file.setOriginalName(safeName);
        file.setStoredPath(target.toString());
        file.setContentType("text/markdown");
        file.setTag(FileTag.NOTE);
        file.setExtractedText(content == null ? "" : content);
        file.setKnowledgeEnabled(true);
        fileRepository.save(file);
        rebuildKnowledge(file, userId);
        return toResponse(file);
    }

    @Transactional
    public FileResponse updateText(Long fileId, Long userId, UpdateFileTextRequest request) {
        StudyFile file = fileRepository.findByIdAndFolderOwnerId(fileId, userId)
                .orElseThrow(() -> new IllegalArgumentException("File not found or access denied"));
        file.setExtractedText(request.extractedText());
        file.setTag(request.tag());
        file.setKnowledgeEnabled(true);
        rebuildKnowledge(file, userId);
        return toResponse(file);
    }

    @Transactional
    public FileResponse updateKnowledgeStatus(Long fileId, Long userId, UpdateKnowledgeStatusRequest request) {
        StudyFile file = fileRepository.findByIdAndFolderOwnerId(fileId, userId)
                .orElseThrow(() -> new IllegalArgumentException("File not found or access denied"));
        file.setKnowledgeEnabled(request.knowledgeEnabled());
        if (file.isKnowledgeEnabled()) {
            rebuildKnowledge(file, userId);
        } else {
            chunkRepository.deleteByFileId(file.getId());
            elasticsearchService.deleteByFileId(userId, file.getId());
        }
        return toResponse(file);
    }

    @Transactional
    public FileResponse move(Long fileId, Long userId, MoveFileRequest request) {
        StudyFile file = fileRepository.findByIdAndFolderOwnerId(fileId, userId)
                .orElseThrow(() -> new IllegalArgumentException("File not found or access denied"));
        StudyFolder targetFolder = folderService.requireOwned(request.folderId(), userId);
        if (file.getFolder().getId().equals(targetFolder.getId())) {
            return toResponse(file);
        }
        file.setFolder(targetFolder);
        List<KnowledgeChunk> chunks = chunkRepository.findByFileIdOrderByChunkIndexAsc(file.getId());
        for (KnowledgeChunk chunk : chunks) {
            chunk.setFolder(targetFolder);
        }
        elasticsearchService.deleteByFileId(userId, file.getId());
        if (file.isKnowledgeEnabled() && !chunks.isEmpty()) {
            elasticsearchService.reindexFile(userId, file, chunks, aiSettingsService.get(userId));
        }
        return toResponse(file);
    }

    @Transactional
    public void delete(Long fileId, Long userId) throws IOException {
        StudyFile file = fileRepository.findByIdAndFolderOwnerId(fileId, userId)
                .orElseThrow(() -> new IllegalArgumentException("File not found or access denied"));
        elasticsearchService.deleteByFileId(userId, file.getId());
        chunkRepository.deleteByFileId(file.getId());
        fileRepository.delete(file);
        Files.deleteIfExists(Path.of(file.getStoredPath()));
    }

    private void rebuildKnowledge(StudyFile file, Long userId) {
        chunkRepository.deleteByFileId(file.getId());
        if (!file.isKnowledgeEnabled()) {
            elasticsearchService.deleteByFileId(userId, file.getId());
            return;
        }
        String text = toSearchableText(file.getExtractedText()).trim();
        if (text.isBlank() || textExtractionService.isExtractionPlaceholder(text)) {
            elasticsearchService.deleteByFileId(userId, file.getId());
            return;
        }
        int chunkSize = 800;
        int overlap = 120;
        int index = 0;
        List<KnowledgeChunk> chunks = new java.util.ArrayList<>();
        for (int start = 0; start < text.length(); start += chunkSize - overlap) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setFile(file);
            chunk.setFolder(file.getFolder());
            chunk.setChunkIndex(index++);
            chunk.setPageNumber(pageNumberForOffset(start));
            chunk.setContent(text.substring(start, Math.min(start + chunkSize, text.length())));
            chunks.add(chunkRepository.save(chunk));
            if (start + chunkSize >= text.length()) {
                break;
            }
        }
        elasticsearchService.reindexFile(userId, file, chunks, aiSettingsService.get(userId));
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfillKnowledgeForExistingFiles() {
        for (StudyFile file : fileRepository.findByKnowledgeEnabledTrue()) {
            if (chunkRepository.countByFileId(file.getId()) == 0
                    || shouldRebuildPageNumbers(file)) {
                rebuildKnowledge(file, file.getFolder().getOwner().getId());
            }
        }
    }

    private boolean shouldRebuildPageNumbers(StudyFile file) {
        return pageCount(file.getExtractedText()) > 1
                && chunkRepository.countByFileIdAndPageNumberGreaterThan(file.getId(), 1) == 0;
    }

    private int pageNumberForOffset(int offset) {
        return Math.max(1, offset / PAGE_SIZE + 1);
    }

    private int pageCount(String content) {
        String text = toSearchableText(content).trim();
        if (text.isBlank()) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(text.length() / (double) PAGE_SIZE));
    }

    private String toSearchableText(String content) {
        if (content == null) {
            return "";
        }
        return content
                .replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?i)</(p|div|li|tr|h[1-6])>", "\n")
                .replaceAll("(?i)</t[dh]>", " ")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n{3,}", "\n\n");
    }

    private FileResponse toResponse(StudyFile file) {
        return new FileResponse(file.getId(), file.getFolder().getId(), file.getOriginalName(), file.getTag(),
                file.getContentType(), file.getExtractedText(), file.isKnowledgeEnabled(),
                pageCount(file.getExtractedText()), file.getUploadedAt());
    }
}
