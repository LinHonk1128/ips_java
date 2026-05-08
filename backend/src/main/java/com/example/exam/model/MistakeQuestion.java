package com.example.exam.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
public class MistakeQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    private MistakeStatus status;

    @ManyToMany
    @JoinTable(
            name = "mistake_question_subject_tag",
            joinColumns = @JoinColumn(name = "mistake_id"),
            inverseJoinColumns = @JoinColumn(name = "subject_tag_id")
    )
    private Set<MistakeSubjectTag> subjectTags = new LinkedHashSet<>();

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean mastered;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(length = 180)
    private String questionOriginalName;

    @Column
    private String questionStoredPath;

    @Column(length = 120)
    private String questionContentType;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String solutionText;

    @Column(length = 180)
    private String solutionOriginalName;

    @Column
    private String solutionStoredPath;

    @Column(length = 120)
    private String solutionContentType;

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

    public MistakeStatus getStatus() {
        return status;
    }

    public void setStatus(MistakeStatus status) {
        this.status = status;
    }

    public Set<MistakeSubjectTag> getSubjectTags() {
        return subjectTags;
    }

    public void setSubjectTags(Set<MistakeSubjectTag> subjectTags) {
        this.subjectTags = subjectTags;
    }

    public boolean isMastered() {
        return mastered;
    }

    public void setMastered(boolean mastered) {
        this.mastered = mastered;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public String getQuestionOriginalName() {
        return questionOriginalName;
    }

    public void setQuestionOriginalName(String questionOriginalName) {
        this.questionOriginalName = questionOriginalName;
    }

    public String getQuestionStoredPath() {
        return questionStoredPath;
    }

    public void setQuestionStoredPath(String questionStoredPath) {
        this.questionStoredPath = questionStoredPath;
    }

    public String getQuestionContentType() {
        return questionContentType;
    }

    public void setQuestionContentType(String questionContentType) {
        this.questionContentType = questionContentType;
    }

    public String getSolutionText() {
        return solutionText;
    }

    public void setSolutionText(String solutionText) {
        this.solutionText = solutionText;
    }

    public String getSolutionOriginalName() {
        return solutionOriginalName;
    }

    public void setSolutionOriginalName(String solutionOriginalName) {
        this.solutionOriginalName = solutionOriginalName;
    }

    public String getSolutionStoredPath() {
        return solutionStoredPath;
    }

    public void setSolutionStoredPath(String solutionStoredPath) {
        this.solutionStoredPath = solutionStoredPath;
    }

    public String getSolutionContentType() {
        return solutionContentType;
    }

    public void setSolutionContentType(String solutionContentType) {
        this.solutionContentType = solutionContentType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
