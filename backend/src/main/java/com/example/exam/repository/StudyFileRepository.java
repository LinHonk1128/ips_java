package com.example.exam.repository;

import com.example.exam.model.StudyFile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyFileRepository extends JpaRepository<StudyFile, Long> {
    List<StudyFile> findByFolderIdOrderByUploadedAtDesc(Long folderId);

    Optional<StudyFile> findByIdAndFolderOwnerId(Long id, Long ownerId);
}
