package com.example.exam.repository;

import com.example.exam.model.UserStudyProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserStudyProfileRepository extends JpaRepository<UserStudyProfile, Long> {
    Optional<UserStudyProfile> findByOwnerId(Long ownerId);

    boolean existsByOwnerId(Long ownerId);
}
