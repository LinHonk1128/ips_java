package com.example.exam.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
/**
 * [SEARCH:ENTITY_STUDY_PROFILE] 用户的备考基础档案。
 *
 * <p>考试日期驱动倒计时和规划诊断；科目数量与学科目录由档案服务同步维护。</p>
 */
public class UserStudyProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    private User owner;

    @Column
    private LocalDate examDate;

    @Column(nullable = false, columnDefinition = "boolean default false")
    // 标识用户是否完成首次备考信息引导。
    private boolean onboarded = false;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int subjectCount = 0;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    public void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public LocalDate getExamDate() {
        return examDate;
    }

    public void setExamDate(LocalDate examDate) {
        this.examDate = examDate;
    }

    public boolean isOnboarded() {
        return onboarded;
    }

    public void setOnboarded(boolean onboarded) {
        this.onboarded = onboarded;
    }

    public int getSubjectCount() {
        return subjectCount;
    }

    public void setSubjectCount(int subjectCount) {
        this.subjectCount = subjectCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
