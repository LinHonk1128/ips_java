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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileService {
    private static final int PAGE_SIZE = 3500;
    private static final int CHUNKING_VERSION = 2;
    private static final int MIN_CHUNK_SIZE = 300;
    private static final int TARGET_CHUNK_SIZE = 800;
    private static final int MAX_CHUNK_SIZE = 1100;
    private static final int OVERLAP_SENTENCE_COUNT = 1;
    private static final Pattern HEADING_PATTERN = Pattern.compile(
            "^(#{1,6}\\s+.+|第[一二三四五六七八九十百千万0-9]+[章节篇部分].*|[一二三四五六七八九十]+[、.．].+|\\(?[一二三四五六七八九十]+\\).+|\\d+(?:\\.\\d+){0,3}[、.．\\s]+.+|考点\\s*[:：]?.+|知识点\\s*[:：]?.+)$");
    private static final Pattern SENTENCE_PATTERN = Pattern.compile("[^。！？；.!?;]+[。！？；.!?;]?");
    private static final Pattern SOFT_BREAK_PATTERN = Pattern.compile("[^，,、：:]+[，,、：:]?");

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
        if (request.originalName() != null && !request.originalName().isBlank()) {
            file.setOriginalName(sanitizeDisplayFileName(request.originalName()));
        }
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
        int index = 0;
        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (SemanticChunk semanticChunk : splitIntoSemanticChunks(text)) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setFile(file);
            chunk.setFolder(file.getFolder());
            chunk.setChunkIndex(index++);
            chunk.setPageNumber(pageNumberForOffset(semanticChunk.startOffset()));
            chunk.setChunkingVersion(CHUNKING_VERSION);
            chunk.setContent(semanticChunk.content());
            chunks.add(chunkRepository.save(chunk));
        }
        elasticsearchService.reindexFile(userId, file, chunks, aiSettingsService.get(userId));
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfillKnowledgeForExistingFiles() {
        for (StudyFile file : fileRepository.findByKnowledgeEnabledTrue()) {
            if (chunkRepository.countByFileId(file.getId()) == 0
                    || shouldRebuildPageNumbers(file)
                    || shouldRebuildChunkingVersion(file)) {
                rebuildKnowledge(file, file.getFolder().getOwner().getId());
            }
        }
    }

    private boolean shouldRebuildChunkingVersion(StudyFile file) {
        return chunkRepository.countByFileIdAndChunkingVersionLessThan(file.getId(), CHUNKING_VERSION) > 0;
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

    private List<SemanticChunk> splitIntoSemanticChunks(String text) {
        List<TextUnit> units = splitIntoTextUnits(text);
        if (units.isEmpty()) {
            return List.of();
        }

        List<SemanticChunk> chunks = new ArrayList<>();
        List<TextUnit> current = new ArrayList<>();
        boolean currentFromOverlap = false;
        for (int i = 0; i < units.size(); i++) {
            TextUnit unit = units.get(i);
            boolean hasMoreUnits = i < units.size() - 1;
            if (shouldStartNewChunk(current, unit)) {
                if (!currentFromOverlap) {
                    addChunk(chunks, current);
                }
                current.clear();
                currentFromOverlap = false;
            } else if (!current.isEmpty() && contentLength(current, unit) > MAX_CHUNK_SIZE) {
                addChunk(chunks, current);
                current = overlapTail(current);
                currentFromOverlap = !current.isEmpty();
                if (contentLength(current, unit) > MAX_CHUNK_SIZE) {
                    current.clear();
                    currentFromOverlap = false;
                }
            }

            current.add(unit);
            currentFromOverlap = false;
            if (contentLength(current) >= TARGET_CHUNK_SIZE && isNaturalStop(unit)) {
                addChunk(chunks, current);
                current = hasMoreUnits ? overlapTail(current) : new ArrayList<>();
                currentFromOverlap = hasMoreUnits && !current.isEmpty();
            }
        }

        if (!current.isEmpty()) {
            addChunk(chunks, current);
        }
        return mergeShortTail(chunks);
    }

    private List<TextUnit> splitIntoTextUnits(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        List<TextUnit> units = new ArrayList<>();
        int offset = 0;
        boolean paragraphStart = true;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                paragraphStart = true;
                offset += line.length() + 1;
                continue;
            }

            int lineContentOffset = offset + Math.max(0, line.indexOf(trimmed));
            boolean heading = isHeading(trimmed);
            List<String> pieces = heading ? List.of(trimmed) : splitSentences(trimmed);
            int searchFrom = 0;
            for (String piece : pieces) {
                int localIndex = trimmed.indexOf(piece, searchFrom);
                int pieceOffset = lineContentOffset + Math.max(0, localIndex);
                units.add(new TextUnit(piece, heading, paragraphStart, pieceOffset));
                searchFrom = localIndex < 0 ? searchFrom : localIndex + piece.length();
                paragraphStart = false;
            }
            offset += line.length() + 1;
        }
        return units;
    }

    private List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        Matcher matcher = SENTENCE_PATTERN.matcher(text);
        while (matcher.find()) {
            String sentence = matcher.group().trim();
            if (!sentence.isBlank()) {
                sentences.addAll(splitOversizedSentence(sentence));
            }
        }
        if (sentences.isEmpty() && !text.isBlank()) {
            sentences.addAll(splitOversizedSentence(text.trim()));
        }
        return sentences;
    }

    private List<String> splitOversizedSentence(String sentence) {
        if (sentence.length() <= MAX_CHUNK_SIZE) {
            return List.of(sentence);
        }
        List<String> clauses = new ArrayList<>();
        Matcher matcher = SOFT_BREAK_PATTERN.matcher(sentence);
        StringBuilder current = new StringBuilder();
        while (matcher.find()) {
            String clause = matcher.group().trim();
            if (clause.isBlank()) {
                continue;
            }
            if (!current.isEmpty() && current.length() + clause.length() > MAX_CHUNK_SIZE) {
                clauses.add(current.toString().trim());
                current.setLength(0);
            }
            if (clause.length() > MAX_CHUNK_SIZE) {
                clauses.addAll(splitByLength(clause));
            } else {
                current.append(clause);
            }
        }
        if (!current.isEmpty()) {
            clauses.add(current.toString().trim());
        }
        return clauses.isEmpty() ? splitByLength(sentence) : clauses;
    }

    private List<String> splitByLength(String text) {
        List<String> parts = new ArrayList<>();
        for (int start = 0; start < text.length(); start += MAX_CHUNK_SIZE) {
            parts.add(text.substring(start, Math.min(start + MAX_CHUNK_SIZE, text.length())).trim());
        }
        return parts;
    }

    private boolean shouldStartNewChunk(List<TextUnit> current, TextUnit unit) {
        if (current.isEmpty()) {
            return false;
        }
        int currentLength = contentLength(current);
        if (unit.heading()) {
            return contentLength(current) >= MIN_CHUNK_SIZE || current.stream().anyMatch(textUnit -> !textUnit.heading());
        }
        if (unit.paragraphStart() && currentLength >= TARGET_CHUNK_SIZE) {
            return true;
        }
        return startsNewTopic(unit.text()) && currentLength >= TARGET_CHUNK_SIZE;
    }

    private boolean startsNewTopic(String text) {
        return text.startsWith("首先")
                || text.startsWith("其次")
                || text.startsWith("再次")
                || text.startsWith("最后")
                || text.startsWith("另一方面")
                || text.startsWith("与此相对")
                || text.startsWith("相比之下");
    }

    private boolean isNaturalStop(TextUnit unit) {
        String text = unit.text();
        return unit.heading()
                || text.endsWith("。")
                || text.endsWith("！")
                || text.endsWith("？")
                || text.endsWith("；")
                || text.endsWith(".")
                || text.endsWith("!")
                || text.endsWith("?")
                || text.endsWith(";");
    }

    private boolean isHeading(String text) {
        return text.length() <= 120 && HEADING_PATTERN.matcher(text).matches();
    }

    private void addChunk(List<SemanticChunk> chunks, List<TextUnit> units) {
        if (units.isEmpty()) {
            return;
        }
        String content = toChunkContent(units);
        if (!content.isBlank()) {
            chunks.add(new SemanticChunk(content, units.get(0).startOffset()));
        }
    }

    private List<TextUnit> overlapTail(List<TextUnit> units) {
        List<TextUnit> tail = new ArrayList<>();
        for (int i = units.size() - 1; i >= 0 && tail.size() < OVERLAP_SENTENCE_COUNT; i--) {
            TextUnit unit = units.get(i);
            if (!unit.heading()) {
                tail.add(0, unit);
            }
        }
        return tail;
    }

    private List<SemanticChunk> mergeShortTail(List<SemanticChunk> chunks) {
        if (chunks.size() < 2) {
            return chunks;
        }
        SemanticChunk tail = chunks.get(chunks.size() - 1);
        SemanticChunk previous = chunks.get(chunks.size() - 2);
        if (tail.content().length() >= MIN_CHUNK_SIZE
                || previous.content().length() + tail.content().length() > MAX_CHUNK_SIZE + MIN_CHUNK_SIZE) {
            return chunks;
        }
        List<SemanticChunk> merged = new ArrayList<>(chunks.subList(0, chunks.size() - 2));
        merged.add(new SemanticChunk(previous.content() + "\n" + tail.content(), previous.startOffset()));
        return merged;
    }

    private int contentLength(List<TextUnit> units) {
        return toChunkContent(units).length();
    }

    private int contentLength(List<TextUnit> units, TextUnit next) {
        List<TextUnit> combined = new ArrayList<>(units);
        combined.add(next);
        return contentLength(combined);
    }

    private String toChunkContent(List<TextUnit> units) {
        StringBuilder builder = new StringBuilder();
        for (TextUnit unit : units) {
            if (builder.isEmpty()) {
                builder.append(unit.text());
            } else if (unit.heading() || unit.paragraphStart()) {
                builder.append('\n').append(unit.text());
            } else {
                builder.append(unit.text());
            }
        }
        return builder.toString().trim();
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

    private String sanitizeDisplayFileName(String originalName) {
        String cleaned = originalName.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        return cleaned.isBlank() ? "未命名文件" : cleaned;
    }

    private FileResponse toResponse(StudyFile file) {
        return new FileResponse(file.getId(), file.getFolder().getId(), file.getOriginalName(), file.getTag(),
                file.getContentType(), file.getExtractedText(), file.isKnowledgeEnabled(),
                pageCount(file.getExtractedText()), file.getUploadedAt());
    }

    private record TextUnit(String text, boolean heading, boolean paragraphStart, int startOffset) {
    }

    private record SemanticChunk(String content, int startOffset) {
    }
}
