package com.example.exam.controller;

import com.example.exam.dto.ChatDtos.ChatRequest;
import com.example.exam.dto.ChatDtos.ChatResponse;
import com.example.exam.model.User;
import com.example.exam.service.ChatService;
import com.example.exam.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;
    private final CurrentUserService currentUserService;

    public ChatController(ChatService chatService, CurrentUserService currentUserService) {
        this.chatService = chatService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    public ChatResponse ask(@Valid @RequestBody ChatRequest request) {
        User user = currentUserService.user();
        return chatService.ask(user.getId(), request);
    }
}
