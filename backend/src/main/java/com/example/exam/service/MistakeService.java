package com.example.exam.service;

import com.example.exam.dto.MistakeDtos.MistakeAttachmentResponse;
import com.example.exam.dto.MistakeDtos.MistakeResponse;
import com.example.exam.dto.MistakeDtos.MistakeSubjectTagResponse;
import com.example.exam.dto.MistakeDtos.MistakeStatusResponse;
import com.example.exam.model.MistakeAttachment;
import com.example.exam.model.MistakeAttachmentType;
import com.example.exam.model.MistakeQuestion;
import com.example.exam.model.MistakeStatus;
import com.example.exam.model.MistakeSubjectTag;
import com.example.exam.model.User;
import com.example.exam.repository.MistakeAttachmentRepository;
import com.example.exam.repository.MistakeQuestionRepository;
import com.example.exam.repository.MistakeStatusRepository;
import com.example.exam.repository.MistakeSubjectTagRepository;
import com.example.exam.repository.UserRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MistakeService {
    private static final String MASTERED_STATUS_NAME = "完全掌握";
    private static final String DEFAULT_UNMASTERED_STATUS_NAME = "未掌握";

    private final MistakeQuestionRepository mistakeRepository;
    private final MistakeStatusRepository statusRepository;
    private final MistakeAttachmentRepository attachmentRepository;
    private final MistakeSubjectTagRepository subjectTagRepository;
    private final UserRepository userRepository;
    private final TextExtractionService textExtractionService;
    private final Path uploadDir;

    public MistakeService(MistakeQuestionRepository mistakeRepository,
                          MistakeStatusRepository statusRepository,
                          MistakeAttachmentRepository attachmentRepository,
                          MistakeSubjectTagRepository subjectTagRepository,
                          UserRepository userRepository,
                          TextExtractionService textExtractionService,
                          @Value("${app.upload-dir}") String uploadDir) {
        this.mistakeRepository = mistakeRepository;
        this.statusRepository = statusRepository;
        this.attachmentRepository = attachmentRepository;
        this.subjectTagRepository = subjectTagRepository;
        this.userRepository = userRepository;
        this.textExtractionService = textExtractionService;
        this.uploadDir = Path.of(uploadDir);
    }

    @Transactional(readOnly = true)
    public List<MistakeStatusResponse> listStatuses(Long userId) {
        List<MistakeStatusResponse> custom = statusRepository.findByOwnerIdOrderByCreatedAtAsc(userId).stream()
                .map(status -> new MistakeStatusResponse(status.getId(), status.getName(), false))
                .toList();
        java.util.ArrayList<MistakeStatusResponse> all = new java.util.ArrayList<>();
        all.add(new MistakeStatusResponse(null, MASTERED_STATUS_NAME, true));
        all.addAll(custom);
        return all;
    }

    @Transactional
    public MistakeStatusResponse createStatus(Long userId, String name) {
        String normalized = normalizeName(name);
        if (MASTERED_STATUS_NAME.equals(normalized) || DEFAULT_UNMASTERED_STATUS_NAME.equals(normalized)) {
            throw new IllegalArgumentException("该状态为系统内置状态");
        }
        if (statusRepository.existsByOwnerIdAndNameIgnoreCase(userId, normalized)) {
            throw new IllegalArgumentException("该状态已存在");
        }
        User owner = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        MistakeStatus status = new MistakeStatus();
        status.setOwner(owner);
        status.setName(normalized);
        statusRepository.save(status);
        return new MistakeStatusResponse(status.getId(), status.getName(), false);
    }

    @Transactional
    public MistakeStatusResponse updateStatus(Long userId, Long statusId, String name) {
        MistakeStatus status = requireStatus(statusId, userId);
        String normalized = normalizeName(name);
        if (!status.getName().equalsIgnoreCase(normalized)
                && statusRepository.existsByOwnerIdAndNameIgnoreCase(userId, normalized)) {
            throw new IllegalArgumentException("该状态已存在");
        }
        status.setName(normalized);
        return new MistakeStatusResponse(status.getId(), status.getName(), false);
    }

    @Transactional
    public void deleteStatus(Long userId, Long statusId) {
        MistakeStatus status = requireStatus(statusId, userId);
        if (mistakeRepository.existsByStatusId(statusId)) {
            throw new IllegalArgumentException("该状态下还有错题，不能删除");
        }
        statusRepository.delete(status);
    }

    @Transactional(readOnly = true)
    public List<MistakeSubjectTagResponse> listSubjectTags(Long userId) {
        return subjectTagRepository.findByOwnerIdOrderByCreatedAtAsc(userId).stream()
                .map(this::toSubjectTagResponse)
                .toList();
    }

    @Transactional
    public MistakeSubjectTagResponse createSubjectTag(Long userId, String name) {
        String normalized = normalizeName(name);
        if (subjectTagRepository.existsByOwnerIdAndNameIgnoreCase(userId, normalized)) {
            throw new IllegalArgumentException("该学科标签已存在");
        }
        User owner = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        MistakeSubjectTag tag = new MistakeSubjectTag();
        tag.setOwner(owner);
        tag.setName(normalized);
        subjectTagRepository.save(tag);
        return toSubjectTagResponse(tag);
    }

    @Transactional
    public void deleteSubjectTag(Long userId, Long tagId) {
        MistakeSubjectTag tag = subjectTagRepository.findByIdAndOwnerId(tagId, userId)
                .orElseThrow(() -> new IllegalArgumentException("学科标签不存在或无权访问"));
        if (subjectTagRepository.isUsed(tagId)) {
            throw new IllegalArgumentException("该学科标签仍被错题使用，不能删除");
        }
        subjectTagRepository.delete(tag);
    }

    @Transactional(readOnly = true)
    public List<MistakeResponse> list(Long userId, Boolean mastered) {
        List<MistakeQuestion> mistakes = mastered == null
                ? mistakeRepository.findByOwnerIdOrderByUpdatedAtDesc(userId)
                : mistakeRepository.findByOwnerIdAndMasteredOrderByUpdatedAtDesc(userId, mastered);
        return mistakes.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<MistakeResponse> practice(Long userId, int count, List<Long> subjectTagIds) {
        int normalizedCount = Math.max(1, Math.min(count, 100));
        List<Long> normalizedSubjectTagIds = normalizeIds(subjectTagIds);
        List<MistakeQuestion> questions = normalizedSubjectTagIds.isEmpty()
                ? mistakeRepository.findRandomUnmastered(userId, normalizedCount)
                : mistakeRepository.findRandomUnmasteredBySubjectTags(userId, normalizedSubjectTagIds, normalizedCount);
        return questions.stream().map(this::toResponse).toList();
    }

    @Transactional
    public MistakeResponse create(Long userId,
                                  String questionText,
                                  MultipartFile questionAttachmentFile,
                                  List<MultipartFile> questionImageFiles,
                                  List<String> questionImageNames,
                                  String solutionText,
                                  MultipartFile solutionFile,
                                  List<MultipartFile> solutionImageFiles,
                                  List<String> solutionImageNames,
                                  Boolean mastered,
                                  Long statusId,
                                  List<Long> subjectTagIds) throws IOException {
        User owner = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        MistakeQuestion mistake = new MistakeQuestion();
        mistake.setOwner(owner);
        applyQuestion(mistake, userId, questionText, questionAttachmentFile);
        mistake.setSolutionText(blankToNull(solutionText));
        applySolutionFile(mistake, userId, solutionFile);
        applyStatus(mistake, userId, mastered, statusId);
        applySubjectTags(mistake, userId, subjectTagIds);
        mistakeRepository.save(mistake);
        applyImageAttachments(mistake, userId, MistakeAttachmentType.QUESTION, questionImageFiles, questionImageNames);
        applyImageAttachments(mistake, userId, MistakeAttachmentType.SOLUTION, solutionImageFiles, solutionImageNames);
        return toResponse(mistake);
    }

    @Transactional
    public MistakeResponse update(Long mistakeId,
                                  Long userId,
                                  String questionText,
                                  MultipartFile questionAttachmentFile,
                                  List<MultipartFile> questionImageFiles,
                                  List<String> questionImageNames,
                                  String solutionText,
                                  MultipartFile solutionFile,
                                  List<MultipartFile> solutionImageFiles,
                                  List<String> solutionImageNames,
                                  Boolean mastered,
                                  Long statusId,
                                  List<Long> subjectTagIds) throws IOException {
        MistakeQuestion mistake = requireMistake(mistakeId, userId);
        applyQuestion(mistake, userId, questionText, questionAttachmentFile);
        mistake.setSolutionText(blankToNull(solutionText));
        applySolutionFile(mistake, userId, solutionFile);
        applyStatus(mistake, userId, mastered, statusId);
        applySubjectTags(mistake, userId, subjectTagIds);
        applyImageAttachments(mistake, userId, MistakeAttachmentType.QUESTION, questionImageFiles, questionImageNames);
        applyImageAttachments(mistake, userId, MistakeAttachmentType.SOLUTION, solutionImageFiles, solutionImageNames);
        return toResponse(mistake);
    }

    @Transactional
    public MistakeResponse updateStatusSelection(Long mistakeId, Long userId, Boolean mastered, Long statusId) {
        MistakeQuestion mistake = requireMistake(mistakeId, userId);
        applyStatus(mistake, userId, mastered, statusId);
        return toResponse(mistake);
    }

    @Transactional
    public void delete(Long mistakeId, Long userId) throws IOException {
        MistakeQuestion mistake = requireMistake(mistakeId, userId);
        deleteAllAttachments(mistake);
        deleteQuestionFile(mistake);
        deleteSolutionFile(mistake);
        mistakeRepository.delete(mistake);
    }

    @Transactional(readOnly = true)
    public String recognizeText(Long userId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要识别的 PDF 或图片文件");
        }
        StoredFile stored = storeFile(userId, "recognition", file);
        try {
            String extracted = textExtractionService.extract(stored.path(), stored.originalName(), stored.contentType());
            if (textExtractionService.isExtractionPlaceholder(extracted)) {
                throw new IllegalArgumentException(extracted);
            }
            return extracted;
        } finally {
            Files.deleteIfExists(stored.path());
        }
    }

    @Transactional(readOnly = true)
    public SolutionFile questionFile(Long mistakeId, Long userId) throws IOException {
        MistakeQuestion mistake = requireMistake(mistakeId, userId);
        if (mistake.getQuestionStoredPath() == null || mistake.getQuestionStoredPath().isBlank()) {
            throw new IllegalArgumentException("该错题没有题目附件");
        }
        Resource resource = new UrlResource(Path.of(mistake.getQuestionStoredPath()).toUri());
        if (!resource.exists() || !resource.isReadable()) {
            throw new IllegalArgumentException("题目附件不存在或无法读取");
        }
        return new SolutionFile(resource, mistake.getQuestionContentType(), mistake.getQuestionOriginalName());
    }

    @Transactional(readOnly = true)
    public SolutionFile solutionFile(Long mistakeId, Long userId) throws IOException {
        MistakeQuestion mistake = requireMistake(mistakeId, userId);
        if (mistake.getSolutionStoredPath() == null || mistake.getSolutionStoredPath().isBlank()) {
            throw new IllegalArgumentException("该错题没有解析文件");
        }
        Resource resource = new UrlResource(Path.of(mistake.getSolutionStoredPath()).toUri());
        if (!resource.exists() || !resource.isReadable()) {
            throw new IllegalArgumentException("解析文件不存在或无法读取");
        }
        return new SolutionFile(resource, mistake.getSolutionContentType(), mistake.getSolutionOriginalName());
    }

    @Transactional(readOnly = true)
    public SolutionFile attachmentFile(Long attachmentId, Long userId) throws IOException {
        MistakeAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("附件不存在"));
        if (!attachment.getMistake().getOwner().getId().equals(userId)) {
            throw new IllegalArgumentException("附件不存在或无权访问");
        }
        Resource resource = new UrlResource(Path.of(attachment.getStoredPath()).toUri());
        if (!resource.exists() || !resource.isReadable()) {
            throw new IllegalArgumentException("附件不存在或无法读取");
        }
        return new SolutionFile(resource, attachment.getContentType(), attachment.getDisplayName());
    }

    private void applyQuestion(MistakeQuestion mistake, Long userId, String questionText, MultipartFile questionAttachmentFile) throws IOException {
        if (questionAttachmentFile != null && !questionAttachmentFile.isEmpty()) {
            deleteQuestionFile(mistake);
            StoredFile stored = storeFile(userId, "question-attachments", questionAttachmentFile);
            mistake.setQuestionOriginalName(stored.originalName());
            mistake.setQuestionStoredPath(stored.path().toString());
            mistake.setQuestionContentType(stored.contentType());
        }
        String normalizedQuestion = questionText == null ? "" : questionText.trim();
        if (normalizedQuestion.isBlank()
                && (mistake.getQuestionStoredPath() == null || mistake.getQuestionStoredPath().isBlank())) {
            throw new IllegalArgumentException("请填写题目，或上传题目附件");
        }
        mistake.setQuestionText(normalizedQuestion);
    }

    private void applySolutionFile(MistakeQuestion mistake, Long userId, MultipartFile solutionFile) throws IOException {
        if (solutionFile == null || solutionFile.isEmpty()) {
            return;
        }
        deleteSolutionFile(mistake);
        StoredFile stored = storeFile(userId, "solutions", solutionFile);
        mistake.setSolutionOriginalName(stored.originalName());
        mistake.setSolutionStoredPath(stored.path().toString());
        mistake.setSolutionContentType(stored.contentType());
    }

    private void applyImageAttachments(MistakeQuestion mistake,
                                       Long userId,
                                       MistakeAttachmentType type,
                                       List<MultipartFile> files,
                                       List<String> names) throws IOException {
        List<MultipartFile> selected = files == null ? List.of() : files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();
        if (selected.isEmpty()) {
            return;
        }
        deleteAttachments(mistake, type);
        if (type == MistakeAttachmentType.QUESTION) {
            deleteQuestionFile(mistake);
        } else {
            deleteSolutionFile(mistake);
        }
        for (int index = 0; index < selected.size(); index++) {
            MultipartFile file = selected.get(index);
            String contentType = file.getContentType() == null ? "" : file.getContentType();
            if (!contentType.startsWith("image/")) {
                throw new IllegalArgumentException("只能上传图片附件");
            }
            StoredFile stored = storeFile(userId, type == MistakeAttachmentType.QUESTION ? "question-images" : "solution-images", file);
            MistakeAttachment attachment = new MistakeAttachment();
            attachment.setMistake(mistake);
            attachment.setType(type);
            attachment.setOriginalName(stored.originalName());
            attachment.setDisplayName(displayNameFor(stored.originalName(), names, index));
            attachment.setStoredPath(stored.path().toString());
            attachment.setContentType(stored.contentType());
            attachmentRepository.save(attachment);
        }
    }

    private String displayNameFor(String originalName, List<String> names, int index) {
        String candidate = names == null || index >= names.size() ? "" : names.get(index);
        if (candidate != null && !candidate.isBlank()) {
            return candidate.trim();
        }
        return originalName == null || originalName.isBlank() ? "图片" + (index + 1) : originalName;
    }

    private void applyStatus(MistakeQuestion mistake, Long userId, Boolean mastered, Long statusId) {
        boolean isMastered = Boolean.TRUE.equals(mastered);
        mistake.setMastered(isMastered);
        if (isMastered) {
            mistake.setStatus(null);
            return;
        }
        mistake.setStatus(statusId == null ? null : requireStatus(statusId, userId));
    }

    private void applySubjectTags(MistakeQuestion mistake, Long userId, List<Long> subjectTagIds) {
        List<Long> ids = normalizeIds(subjectTagIds);
        if (ids.isEmpty()) {
            mistake.getSubjectTags().clear();
            return;
        }
        List<MistakeSubjectTag> tags = subjectTagRepository.findByIdInAndOwnerId(ids, userId);
        if (tags.size() != ids.size()) {
            throw new IllegalArgumentException("学科标签不存在或无权访问");
        }
        mistake.setSubjectTags(new LinkedHashSet<>(tags));
    }

    private List<Long> normalizeIds(List<Long> ids) {
        return ids == null ? List.of() : ids.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    private StoredFile storeFile(Long userId, String category, MultipartFile file) throws IOException {
        Path directory = uploadDir.resolve(String.valueOf(userId)).resolve("mistakes").resolve(category);
        Files.createDirectories(directory);
        String originalName = file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();
        Path target = directory.resolve(UUID.randomUUID() + "-" + sanitizeFileName(originalName));
        file.transferTo(target);
        return new StoredFile(
                target,
                originalName,
                file.getContentType() == null ? "application/octet-stream" : file.getContentType()
        );
    }

    private MistakeQuestion requireMistake(Long mistakeId, Long userId) {
        return mistakeRepository.findByIdAndOwnerId(mistakeId, userId)
                .orElseThrow(() -> new IllegalArgumentException("错题不存在或无权访问"));
    }

    private MistakeStatus requireStatus(Long statusId, Long userId) {
        return statusRepository.findByIdAndOwnerId(statusId, userId)
                .orElseThrow(() -> new IllegalArgumentException("错题状态不存在或无权访问"));
    }

    private void deleteSolutionFile(MistakeQuestion mistake) throws IOException {
        if (mistake.getSolutionStoredPath() != null && !mistake.getSolutionStoredPath().isBlank()) {
            Files.deleteIfExists(Path.of(mistake.getSolutionStoredPath()));
        }
        mistake.setSolutionOriginalName(null);
        mistake.setSolutionStoredPath(null);
        mistake.setSolutionContentType(null);
    }

    private void deleteQuestionFile(MistakeQuestion mistake) throws IOException {
        if (mistake.getQuestionStoredPath() != null && !mistake.getQuestionStoredPath().isBlank()) {
            Files.deleteIfExists(Path.of(mistake.getQuestionStoredPath()));
        }
        mistake.setQuestionOriginalName(null);
        mistake.setQuestionStoredPath(null);
        mistake.setQuestionContentType(null);
    }

    private void deleteAllAttachments(MistakeQuestion mistake) throws IOException {
        for (MistakeAttachment attachment : attachmentRepository.findByMistakeIdOrderByCreatedAtAsc(mistake.getId())) {
            Files.deleteIfExists(Path.of(attachment.getStoredPath()));
            attachmentRepository.delete(attachment);
        }
    }

    private void deleteAttachments(MistakeQuestion mistake, MistakeAttachmentType type) throws IOException {
        for (MistakeAttachment attachment : attachmentRepository.findByMistakeIdAndTypeOrderByCreatedAtAsc(mistake.getId(), type)) {
            Files.deleteIfExists(Path.of(attachment.getStoredPath()));
            attachmentRepository.delete(attachment);
        }
    }

    private String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("状态名称不能为空");
        }
        return normalized;
    }

    private String sanitizeFileName(String originalName) {
        String sanitized = originalName.replaceAll("[\\\\/:*?\"<>|]", "_");
        return sanitized.isBlank() ? "unnamed" : sanitized;
    }

    private String joinText(String inputText, String extractedText) {
        String first = inputText == null ? "" : inputText.trim();
        String second = extractedText == null ? "" : extractedText.trim();
        if (first.isBlank()) {
            return second;
        }
        if (second.isBlank()) {
            return first;
        }
        return first + "\n\n" + second;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private MistakeSubjectTagResponse toSubjectTagResponse(MistakeSubjectTag tag) {
        return new MistakeSubjectTagResponse(tag.getId(), tag.getName());
    }

    private MistakeResponse toResponse(MistakeQuestion mistake) {
        MistakeStatus status = mistake.getStatus();
        String statusName = mistake.isMastered()
                ? MASTERED_STATUS_NAME
                : status == null ? DEFAULT_UNMASTERED_STATUS_NAME : status.getName();
        return new MistakeResponse(
                mistake.getId(),
                mistake.getQuestionText(),
                mistake.getQuestionOriginalName(),
                mistake.getQuestionContentType(),
                mistake.getQuestionStoredPath() != null && !mistake.getQuestionStoredPath().isBlank(),
                mistake.getSolutionText(),
                mistake.getSolutionOriginalName(),
                mistake.getSolutionContentType(),
                mistake.getSolutionStoredPath() != null && !mistake.getSolutionStoredPath().isBlank(),
                questionAttachments(mistake),
                solutionAttachments(mistake),
                mistake.getSubjectTags().stream().map(this::toSubjectTagResponse).toList(),
                mistake.isMastered(),
                status == null ? null : status.getId(),
                statusName,
                mistake.getCreatedAt(),
                mistake.getUpdatedAt()
        );
    }

    private List<MistakeAttachmentResponse> questionAttachments(MistakeQuestion mistake) {
        return attachments(mistake, MistakeAttachmentType.QUESTION, mistake.getQuestionOriginalName(),
                mistake.getQuestionContentType(), mistake.getQuestionStoredPath() != null && !mistake.getQuestionStoredPath().isBlank());
    }

    private List<MistakeAttachmentResponse> solutionAttachments(MistakeQuestion mistake) {
        return attachments(mistake, MistakeAttachmentType.SOLUTION, mistake.getSolutionOriginalName(),
                mistake.getSolutionContentType(), mistake.getSolutionStoredPath() != null && !mistake.getSolutionStoredPath().isBlank());
    }

    private List<MistakeAttachmentResponse> attachments(MistakeQuestion mistake,
                                                       MistakeAttachmentType type,
                                                       String legacyName,
                                                       String legacyContentType,
                                                       boolean hasLegacy) {
        List<MistakeAttachmentResponse> responses = new ArrayList<>(attachmentRepository
                .findByMistakeIdAndTypeOrderByCreatedAtAsc(mistake.getId(), type)
                .stream()
                .map(attachment -> new MistakeAttachmentResponse(
                        attachment.getId(),
                        attachment.getDisplayName(),
                        attachment.getOriginalName(),
                        attachment.getContentType(),
                        isImage(attachment.getContentType())
                ))
                .toList());
        if (hasLegacy) {
            responses.add(new MistakeAttachmentResponse(null, legacyName, legacyName, legacyContentType, isImage(legacyContentType)));
        }
        return responses;
    }

    private boolean isImage(String contentType) {
        return contentType != null && contentType.startsWith("image/");
    }

    public record SolutionFile(Resource resource, String contentType, String originalName) {
    }

    private record StoredFile(Path path, String originalName, String contentType) {
    }
}
