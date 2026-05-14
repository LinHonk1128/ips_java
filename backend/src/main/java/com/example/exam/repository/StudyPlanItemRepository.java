package com.example.exam.repository;

import com.example.exam.model.StudyPlanItem;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyPlanItemRepository extends JpaRepository<StudyPlanItem, Long> {
    List<StudyPlanItem> findByOwnerIdAndStartDateBetweenOrderByStartDateAscStartTimeAsc(Long ownerId, LocalDate from, LocalDate to);

    Optional<StudyPlanItem> findByIdAndOwnerId(Long id, Long ownerId);
}
