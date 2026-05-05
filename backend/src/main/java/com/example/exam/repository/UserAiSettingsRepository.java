package com.example.exam.repository;

import com.example.exam.model.UserAiSettings;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAiSettingsRepository extends JpaRepository<UserAiSettings, Long> {
    Optional<UserAiSettings> findByUserId(Long userId);
}
