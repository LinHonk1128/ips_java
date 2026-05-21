package com.example.exam.repository;

import com.example.exam.model.StudyFolder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudyFolderRepository extends JpaRepository<StudyFolder, Long> {
    List<StudyFolder> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    Optional<StudyFolder> findByIdAndOwnerId(Long id, Long ownerId);

    List<StudyFolder> findByOwnerIdAndParentIsNullOrderByCreatedAtAsc(Long ownerId);

    List<StudyFolder> findByOwnerIdAndSubjectFolderTrueOrderBySubjectOrderAscCreatedAtAsc(Long ownerId);

    @Query("select count(f) from StudyFolder f where f.owner.id = :ownerId and f.parent is null")
    long countRootFolders(@Param("ownerId") Long ownerId);
}
