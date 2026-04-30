package com.example.exam.service;

import com.example.exam.model.User;
import com.example.exam.repository.UserRepository;
import com.example.exam.security.JwtPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User user() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof JwtPrincipal jwtPrincipal) {
            return userRepository.findById(jwtPrincipal.userId()).orElseThrow();
        }
        throw new IllegalStateException("Not authenticated");
    }
}
