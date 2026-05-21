package com.example.exam.repository;

import com.example.exam.model.KnowledgeChunkEvent;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeChunkEventRepository extends JpaRepository<KnowledgeChunkEvent, Long> {
    List<KnowledgeChunkEvent> findByOwnerIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(Long ownerId, Instant createdAt);
}
