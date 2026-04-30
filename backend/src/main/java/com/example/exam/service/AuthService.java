package com.example.exam.service;

import com.example.exam.dto.AuthRequest;
import com.example.exam.dto.AuthResponse;
import com.example.exam.model.User;
import com.example.exam.repository.UserRepository;
import com.example.exam.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(AuthRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("Username already exists");
        }
        User user = new User();
        user.setUsername(request.username());
        user.setDisplayName(request.displayName() == null || request.displayName().isBlank()
                ? request.username()
                : request.displayName());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(user);
        return response(user);
    }

    public AuthResponse login(AuthRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }
        return response(user);
    }

    private AuthResponse response(User user) {
        return new AuthResponse(jwtService.createToken(user.getId(), user.getUsername()),
                user.getId(), user.getUsername(), user.getDisplayName());
    }
}
