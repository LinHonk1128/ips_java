package com.example.exam.repository;

import com.example.exam.model.KnowledgeChunk;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from KnowledgeChunk k where k.file.id = :fileId")
    void deleteByFileId(@Param("fileId") Long fileId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from KnowledgeChunk k where k.folder.id in :folderIds")
    void deleteByFolderIdIn(@Param("folderIds") Collection<Long> folderIds);

    @Query("select k from KnowledgeChunk k where k.folder.id = :folderId order by k.id asc")
    List<KnowledgeChunk> findByFolderId(@Param("folderId") Long folderId);

    @Query("select k from KnowledgeChunk k where k.folder.id in :folderIds order by k.id asc")
    List<KnowledgeChunk> findByFolderIdIn(@Param("folderIds") Collection<Long> folderIds);

    @Query("select k from KnowledgeChunk k where k.file.id = :fileId order by k.chunkIndex asc")
    List<KnowledgeChunk> findByFileIdOrderByChunkIndexAsc(@Param("fileId") Long fileId);

    long countByFileId(Long fileId);

    long countByFileIdAndPageNumberGreaterThan(Long fileId, int pageNumber);

    @Query("""
            select k from KnowledgeChunk k
            join k.file f
            join f.folder folder
            where k.folder.id in :folderIds
              and folder.owner.id = :ownerId
              and f.knowledgeEnabled = true
            order by k.id asc
            """)
    List<KnowledgeChunk> findExistingByFolderIdInAndOwnerId(@Param("folderIds") Collection<Long> folderIds,
                                                            @Param("ownerId") Long ownerId);
}
