package com.example.exam.repository;

import com.example.exam.model.StudyFolder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyFolderRepository extends JpaRepository<StudyFolder, Long> {
    List<StudyFolder> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    Optional<StudyFolder> findByIdAndOwnerId(Long id, Long ownerId);
}
