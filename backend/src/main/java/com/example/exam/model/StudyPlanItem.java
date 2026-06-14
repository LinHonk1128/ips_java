package com.example.exam.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(indexes = {
        @Index(name = "idx_study_plan_owner_date", columnList = "owner_id,start_date"),
        @Index(name = "idx_study_plan_owner_status", columnList = "owner_id,status")
})
/**
 * [SEARCH:ENTITY_STUDY_PLAN_ITEM] 日历中的一项学习安排。
 *
 * <p>包含日期、时间、优先级和完成状态；source 用于区分手工、AI 或画像建议产生的任务。</p>
 */
public class StudyPlanItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User owner;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 120)
    private String subject;

    @Column(length = 800)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private StudyPlanItemType itemType = StudyPlanItemType.SELF_STUDY;

    @Column(nullable = false, name = "start_date")
    private LocalDate startDate;

    @Column(nullable = false, name = "start_time")
    private LocalTime startTime;

    @Column(nullable = false, name = "end_time")
    private LocalTime endTime;

    @Column(length = 120)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StudyPlanPriority priority = StudyPlanPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StudyPlanStatus status = StudyPlanStatus.TODO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    // 记录计划的生成来源，便于前端展示以及后续评估建议采纳情况。
    private StudyPlanSource source = StudyPlanSource.MANUAL;

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public StudyPlanItemType getItemType() {
        return itemType;
    }

    public void setItemType(StudyPlanItemType itemType) {
        this.itemType = itemType;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public StudyPlanPriority getPriority() {
        return priority;
    }

    public void setPriority(StudyPlanPriority priority) {
        this.priority = priority;
    }

    public StudyPlanStatus getStatus() {
        return status;
    }

    public void setStatus(StudyPlanStatus status) {
        this.status = status;
    }

    public StudyPlanSource getSource() {
        return source;
    }

    public void setSource(StudyPlanSource source) {
        this.source = source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
