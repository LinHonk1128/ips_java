package com.example.exam.repository;

import com.example.exam.model.MistakeAttachment;
import com.example.exam.model.MistakeAttachmentType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MistakeAttachmentRepository extends JpaRepository<MistakeAttachment, Long> {
    List<MistakeAttachment> findByMistakeIdOrderByCreatedAtAsc(Long mistakeId);

    List<MistakeAttachment> findByMistakeIdAndTypeOrderByCreatedAtAsc(Long mistakeId, MistakeAttachmentType type);
}
