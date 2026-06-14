package com.example.exam.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
/**
 * [SEARCH:ENTITY_MISTAKE_PRACTICE_EVENT] 单次错题复习结果事件。
 *
 * <p>保留时间维度的答题结果，供画像统计近期练习活跃度和正确率。</p>
 */
public class MistakePracticeEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private Long mistakeId;

    @Column(nullable = false)
    private boolean correct;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public Long getMistakeId() {
        return mistakeId;
    }

    public void setMistakeId(Long mistakeId) {
        this.mistakeId = mistakeId;
    }

    public boolean isCorrect() {
        return correct;
    }

    public void setCorrect(boolean correct) {
        this.correct = correct;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
