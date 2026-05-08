package com.example.exam.repository;

import com.example.exam.model.MistakeQuestion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MistakeQuestionRepository extends JpaRepository<MistakeQuestion, Long> {
    List<MistakeQuestion> findByOwnerIdOrderByUpdatedAtDesc(Long ownerId);

    List<MistakeQuestion> findByOwnerIdAndMasteredOrderByUpdatedAtDesc(Long ownerId, boolean mastered);

    Optional<MistakeQuestion> findByIdAndOwnerId(Long id, Long ownerId);

    boolean existsByStatusId(Long statusId);

    @Query(value = """
            select * from mistake_question
            where owner_id = :ownerId and mastered = false
            order by rand()
            limit :limit
            """, nativeQuery = true)
    List<MistakeQuestion> findRandomUnmastered(@Param("ownerId") Long ownerId, @Param("limit") int limit);

    @Query(value = """
            select distinct mq.* from mistake_question mq
            join mistake_question_subject_tag st on st.mistake_id = mq.id
            where mq.owner_id = :ownerId
              and mq.mastered = false
              and st.subject_tag_id in (:subjectTagIds)
            order by rand()
            limit :limit
            """, nativeQuery = true)
    List<MistakeQuestion> findRandomUnmasteredBySubjectTags(@Param("ownerId") Long ownerId,
                                                            @Param("subjectTagIds") List<Long> subjectTagIds,
                                                            @Param("limit") int limit);
}
