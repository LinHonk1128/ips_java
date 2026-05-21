package com.example.exam.repository;

import com.example.exam.model.MistakeSubjectTag;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MistakeSubjectTagRepository extends JpaRepository<MistakeSubjectTag, Long> {
    List<MistakeSubjectTag> findByOwnerIdOrderByCreatedAtAsc(Long ownerId);

    List<MistakeSubjectTag> findByIdInAndOwnerId(Collection<Long> ids, Long ownerId);

    Optional<MistakeSubjectTag> findByIdAndOwnerId(Long id, Long ownerId);

    Optional<MistakeSubjectTag> findByOwnerIdAndNameIgnoreCase(Long ownerId, String name);

    boolean existsByOwnerIdAndNameIgnoreCase(Long ownerId, String name);

    @Query(value = "select count(*) > 0 from mistake_question_subject_tag where subject_tag_id = :tagId", nativeQuery = true)
    boolean isUsed(@Param("tagId") Long tagId);
}
