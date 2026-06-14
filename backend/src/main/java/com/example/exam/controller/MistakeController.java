package com.example.exam.controller;

import com.example.exam.dto.MistakeDtos.CreateMistakeStatusRequest;
import com.example.exam.dto.MistakeDtos.CreateMistakeSubjectTagRequest;
import com.example.exam.dto.MistakeDtos.CreateMistakeFromTeacherRequest;
import com.example.exam.dto.MistakeDtos.MistakeResponse;
import com.example.exam.dto.MistakeDtos.MistakeSubjectTagResponse;
import com.example.exam.dto.MistakeDtos.MistakeStatusResponse;
import com.example.exam.dto.MistakeDtos.PracticeResultRequest;
import com.example.exam.dto.MistakeDtos.PracticeResultResponse;
import com.example.exam.dto.MistakeDtos.RecognizeTextResponse;
import com.example.exam.dto.MistakeDtos.UpdateMistakeStatusRequest;
import com.example.exam.dto.MistakeDtos.UpdateMistakeStatusSelectionRequest;
import com.example.exam.model.User;
import com.example.exam.service.CurrentUserService;
import com.example.exam.service.MistakeService;
import com.example.exam.service.MistakeService.SolutionFile;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
// [SEARCH:API_MISTAKES] 错题状态、录入、附件、复习和结果回写接口入口。
public class MistakeController {
    private final MistakeService mistakeService;
    private final CurrentUserService currentUserService;

    public MistakeController(MistakeService mistakeService, CurrentUserService currentUserService) {
        this.mistakeService = mistakeService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/mistake-statuses")
    public List<MistakeStatusResponse> listStatuses() {
        User user = currentUserService.user();
        return mistakeService.listStatuses(user.getId());
    }

    @PostMapping("/mistake-statuses")
    public MistakeStatusResponse createStatus(@Valid @RequestBody CreateMistakeStatusRequest request) {
        User user = currentUserService.user();
        return mistakeService.createStatus(user.getId(), request.name());
    }

    @PutMapping("/mistake-statuses/{statusId}")
    public MistakeStatusResponse updateStatus(@PathVariable Long statusId,
                                              @Valid @RequestBody UpdateMistakeStatusRequest request) {
        User user = currentUserService.user();
        return mistakeService.updateStatus(user.getId(), statusId, request.name());
    }

    @DeleteMapping("/mistake-statuses/{statusId}")
    public ResponseEntity<Void> deleteStatus(@PathVariable Long statusId) {
        User user = currentUserService.user();
        mistakeService.deleteStatus(user.getId(), statusId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/mistake-subject-tags")
    public List<MistakeSubjectTagResponse> listSubjectTags() {
        User user = currentUserService.user();
        return mistakeService.listSubjectTags(user.getId());
    }

    @PostMapping("/mistake-subject-tags")
    public MistakeSubjectTagResponse createSubjectTag(@Valid @RequestBody CreateMistakeSubjectTagRequest request) {
        User user = currentUserService.user();
        return mistakeService.createSubjectTag(user.getId(), request.name());
    }

    @DeleteMapping("/mistake-subject-tags/{tagId}")
    public ResponseEntity<Void> deleteSubjectTag(@PathVariable Long tagId) {
        User user = currentUserService.user();
        mistakeService.deleteSubjectTag(user.getId(), tagId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/mistakes")
    public List<MistakeResponse> list(@RequestParam(required = false) Boolean mastered) {
        User user = currentUserService.user();
        return mistakeService.list(user.getId(), mastered);
    }

    @GetMapping("/mistakes/practice")
    public List<MistakeResponse> practice(@RequestParam(defaultValue = "10") int count,
                                          @RequestParam(required = false) List<Long> subjectTagIds) {
        User user = currentUserService.user();
        return mistakeService.practice(user.getId(), count, subjectTagIds);
    }

    @PostMapping("/mistakes/recognize")
    public RecognizeTextResponse recognize(@RequestParam MultipartFile file) throws IOException {
        User user = currentUserService.user();
        return new RecognizeTextResponse(mistakeService.recognizeText(user.getId(), file));
    }

    @PostMapping("/mistakes")
    public MistakeResponse create(@RequestParam(required = false) String questionText,
                                  @RequestParam(required = false) MultipartFile questionAttachmentFile,
                                  @RequestParam(required = false) List<MultipartFile> questionImageFiles,
                                  @RequestParam(required = false) List<String> questionImageNames,
                                  @RequestParam(required = false) String solutionText,
                                  @RequestParam(required = false) MultipartFile solutionFile,
                                  @RequestParam(required = false) List<MultipartFile> solutionImageFiles,
                                  @RequestParam(required = false) List<String> solutionImageNames,
                                  @RequestParam(defaultValue = "false") Boolean mastered,
                                  @RequestParam(required = false) Long statusId,
                                  @RequestParam(required = false) List<Long> subjectTagIds,
                                  @RequestParam(required = false) List<Long> chunkIds) throws IOException {
        User user = currentUserService.user();
        return mistakeService.create(user.getId(), questionText, questionAttachmentFile, questionImageFiles, questionImageNames,
                solutionText, solutionFile, solutionImageFiles, solutionImageNames, mastered, statusId, subjectTagIds, chunkIds);
    }

    @PostMapping("/mistakes/from-teacher-question")
    public MistakeResponse createFromTeacher(@Valid @RequestBody CreateMistakeFromTeacherRequest request) {
        User user = currentUserService.user();
        return mistakeService.createFromTeacher(user.getId(), request.chunkId(), request.questionText(),
                request.solutionText(), request.feedbackAlreadyForgot(), request.subjectTagIds());
    }

    @PutMapping("/mistakes/{mistakeId}")
    public MistakeResponse update(@PathVariable Long mistakeId,
                                  @RequestParam(required = false) String questionText,
                                  @RequestParam(required = false) MultipartFile questionAttachmentFile,
                                  @RequestParam(required = false) List<MultipartFile> questionImageFiles,
                                  @RequestParam(required = false) List<String> questionImageNames,
                                  @RequestParam(required = false) List<Long> retainedQuestionAttachmentIds,
                                  @RequestParam(required = false) String solutionText,
                                  @RequestParam(required = false) MultipartFile solutionFile,
                                  @RequestParam(required = false) List<MultipartFile> solutionImageFiles,
                                  @RequestParam(required = false) List<String> solutionImageNames,
                                  @RequestParam(required = false) List<Long> retainedSolutionAttachmentIds,
                                  @RequestParam(defaultValue = "false") Boolean mastered,
                                  @RequestParam(required = false) Long statusId,
                                  @RequestParam(required = false) List<Long> subjectTagIds,
                                  @RequestParam(required = false) List<Long> chunkIds) throws IOException {
        User user = currentUserService.user();
        return mistakeService.update(mistakeId, user.getId(), questionText, questionAttachmentFile, questionImageFiles, questionImageNames,
                retainedQuestionAttachmentIds, solutionText, solutionFile, solutionImageFiles, solutionImageNames,
                retainedSolutionAttachmentIds, mastered, statusId, subjectTagIds, chunkIds);
    }

    @PatchMapping("/mistakes/{mistakeId}/status")
    public MistakeResponse updateMistakeStatus(@PathVariable Long mistakeId,
                                               @RequestBody UpdateMistakeStatusSelectionRequest request) {
        User user = currentUserService.user();
        return mistakeService.updateStatusSelection(mistakeId, user.getId(), request.mastered(), request.statusId());
    }

    @PostMapping("/mistakes/{mistakeId}/practice-result")
    public PracticeResultResponse recordPracticeResult(@PathVariable Long mistakeId,
                                                       @Valid @RequestBody PracticeResultRequest request) {
        User user = currentUserService.user();
        return mistakeService.recordPracticeResult(mistakeId, user.getId(), request.correct());
    }

    @DeleteMapping("/mistakes/{mistakeId}")
    public ResponseEntity<Void> delete(@PathVariable Long mistakeId) throws IOException {
        User user = currentUserService.user();
        mistakeService.delete(mistakeId, user.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/mistakes/{mistakeId}/solution-file")
    public ResponseEntity<Resource> solutionFile(@PathVariable Long mistakeId) throws IOException {
        User user = currentUserService.user();
        SolutionFile file = mistakeService.solutionFile(mistakeId, user.getId());
        return inlineFile(file);
    }

    @GetMapping("/mistakes/{mistakeId}/question-file")
    public ResponseEntity<Resource> questionFile(@PathVariable Long mistakeId) throws IOException {
        User user = currentUserService.user();
        SolutionFile file = mistakeService.questionFile(mistakeId, user.getId());
        return inlineFile(file);
    }

    @GetMapping("/mistake-attachments/{attachmentId}")
    public ResponseEntity<Resource> attachmentFile(@PathVariable Long attachmentId) throws IOException {
        User user = currentUserService.user();
        SolutionFile file = mistakeService.attachmentFile(attachmentId, user.getId());
        return inlineFile(file);
    }

    private ResponseEntity<Resource> inlineFile(SolutionFile file) {
        MediaType mediaType = MediaType.parseMediaType(file.contentType() == null ? "application/octet-stream" : file.contentType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(file.originalName() == null ? "attachment" : file.originalName())
                        .build()
                        .toString())
                .body(file.resource());
    }
}
