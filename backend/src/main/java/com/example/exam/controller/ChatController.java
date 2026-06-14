package com.example.exam.controller;

import com.example.exam.dto.ChatDtos.ChatRequest;
import com.example.exam.dto.ChatDtos.ChatResponse;
import com.example.exam.dto.ChatDtos.ChunkFeedbackRequest;
import com.example.exam.dto.ChatDtos.ChunkFeedbackResponse;
import com.example.exam.dto.ChatDtos.NoteRequest;
import com.example.exam.dto.ChatDtos.TeacherQuestionRequest;
import com.example.exam.dto.ChatDtos.TeacherQuestionResponse;
import com.example.exam.dto.FileDtos.FileResponse;
import com.example.exam.model.User;
import com.example.exam.service.ChatService;
import com.example.exam.service.CurrentUserService;
import com.example.exam.service.FileService;
import com.example.exam.service.KnowledgeChunkInteractionService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
// [SEARCH:API_CHAT] 同步/流式问答、教师出题、片段反馈和对话笔记接口入口。
public class ChatController {
    private static final DateTimeFormatter NOTE_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ChatService chatService;
    private final FileService fileService;
    private final KnowledgeChunkInteractionService chunkInteractionService;
    private final CurrentUserService currentUserService;

    public ChatController(ChatService chatService,
                          FileService fileService,
                          KnowledgeChunkInteractionService chunkInteractionService,
                          CurrentUserService currentUserService) {
        this.chatService = chatService;
        this.fileService = fileService;
        this.chunkInteractionService = chunkInteractionService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    public ChatResponse ask(@Valid @RequestBody ChatRequest request) {
        User user = currentUserService.user();
        return chatService.ask(user.getId(), request);
    }

    @PostMapping("/stream")
    public SseEmitter askStream(@Valid @RequestBody ChatRequest request) {
        User user = currentUserService.user();
        return chatService.askStream(user.getId(), request);
    }

    @PostMapping("/teacher/question")
    public TeacherQuestionResponse teacherQuestion(@Valid @RequestBody TeacherQuestionRequest request) {
        User user = currentUserService.user();
        return chatService.teacherQuestion(user.getId(), request);
    }

    @PostMapping("/chunks/{chunkId}/feedback")
    public ChunkFeedbackResponse feedback(@PathVariable Long chunkId, @Valid @RequestBody ChunkFeedbackRequest request) {
        User user = currentUserService.user();
        return chunkInteractionService.recordFeedback(user.getId(), chunkId, request.type());
    }

    @PostMapping("/note")
    public FileResponse createNote(@Valid @RequestBody NoteRequest request) throws IOException {
        User user = currentUserService.user();
        String content = chatService.summarizeConversationAsNote(user.getId(), request);
        String name = "对话整理笔记-" + LocalDateTime.now().format(NOTE_NAME_FORMAT) + ".md";
        return fileService.createTextNote(request.folderId(), user.getId(), name, content);
    }
}
