package com.example.exam.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
