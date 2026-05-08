package com.example.exam.repository;

import com.example.exam.model.MistakeStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MistakeStatusRepository extends JpaRepository<MistakeStatus, Long> {
    List<MistakeStatus> findByOwnerIdOrderByCreatedAtAsc(Long ownerId);

    Optional<MistakeStatus> findByIdAndOwnerId(Long id, Long ownerId);

    boolean existsByOwnerIdAndNameIgnoreCase(Long ownerId, String name);
}
