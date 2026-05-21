package com.example.exam.repository;

import com.example.exam.model.MistakeQuestionChunk;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MistakeQuestionChunkRepository extends JpaRepository<MistakeQuestionChunk, Long> {
    List<MistakeQuestionChunk> findByMistakeId(Long mistakeId);

    List<MistakeQuestionChunk> findByMistakeIdIn(Collection<Long> mistakeIds);

    boolean existsByMistakeIdAndChunkId(Long mistakeId, Long chunkId);
}
