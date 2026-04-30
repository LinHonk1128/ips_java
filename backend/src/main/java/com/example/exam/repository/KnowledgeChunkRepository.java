package com.example.exam.repository;

import com.example.exam.model.KnowledgeChunk;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {
    void deleteByFileId(Long fileId);

    @Query("select k from KnowledgeChunk k where k.folder.id = :folderId order by k.id asc")
    List<KnowledgeChunk> findByFolderId(@Param("folderId") Long folderId);
}
