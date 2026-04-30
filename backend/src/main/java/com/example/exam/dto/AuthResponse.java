package com.example.exam.dto;

public record AuthResponse(String token, Long userId, String username, String displayName) {
}
