package com.example.exam.repository;

import com.example.exam.model.StudyFile;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StudyFileRepository extends JpaRepository<StudyFile, Long> {
    List<StudyFile> findByFolderIdOrderByUploadedAtDesc(Long folderId);

    List<StudyFile> findByFolderIdIn(Collection<Long> folderIds);

    @Query("""
            select f from StudyFile f
            join fetch f.folder folder
            join fetch folder.owner
            where f.knowledgeEnabled = true
            """)
    List<StudyFile> findByKnowledgeEnabledTrue();

    Optional<StudyFile> findByIdAndFolderOwnerId(Long id, Long ownerId);
}
