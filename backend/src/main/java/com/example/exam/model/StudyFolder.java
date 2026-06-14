package com.example.exam.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
/**
 * [SEARCH:ENTITY_STUDY_FOLDER] 用户知识库中的目录节点。
 *
 * <p>普通目录组织资料；顶层学科目录还承担画像按科目聚合和错题标签同步的职责。</p>
 */
public class StudyFolder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private StudyFolder parent;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 400)
    private String description;

    @Column(nullable = false, columnDefinition = "integer default 1")
    private int depth = 1;

    @Column(nullable = false, columnDefinition = "boolean default false")
    // 标记该顶层目录是否来自用户的备考科目配置。
    private boolean subjectFolder = false;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int subjectOrder = 0;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public StudyFolder getParent() {
        return parent;
    }

    public void setParent(StudyFolder parent) {
        this.parent = parent;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public boolean isSubjectFolder() {
        return subjectFolder;
    }

    public void setSubjectFolder(boolean subjectFolder) {
        this.subjectFolder = subjectFolder;
    }

    public int getSubjectOrder() {
        return subjectOrder;
    }

    public void setSubjectOrder(int subjectOrder) {
        this.subjectOrder = subjectOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
