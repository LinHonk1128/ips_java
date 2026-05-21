package com.example.exam.repository;

import com.example.exam.model.MistakePracticeEvent;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MistakePracticeEventRepository extends JpaRepository<MistakePracticeEvent, Long> {
    List<MistakePracticeEvent> findByOwnerIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(Long ownerId, Instant createdAt);
}
