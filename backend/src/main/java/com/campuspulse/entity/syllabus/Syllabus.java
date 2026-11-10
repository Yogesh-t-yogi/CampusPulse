
package com.campuspulse.entity.syllabus;

import com.campuspulse.entity.user.AppUser;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "syllabus")
public class Syllabus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Prevent circular JSON serialization through AppUser
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser owner;

    @Column(nullable = false)
    private String name;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    @Column(
        name = "created_at",
        nullable = false,
        columnDefinition = "TIMESTAMP WITH TIME ZONE"
    )
    private LocalDateTime createdAt;

    // PROCESSING, READY, READY_WITH_WARNINGS, FAILED (null = uploaded, not processed yet)
    @Column(name = "processing_status", length = 30)
    private String processingStatus;

    @Column(name = "processing_message", columnDefinition = "TEXT")
    private String processingMessage;

    @OneToMany(
        mappedBy = "syllabus",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    private List<Subject> subjects = new ArrayList<>();

    public Syllabus() {
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public String getProcessingStatus() {
        return processingStatus;
    }

    public String getProcessingMessage() {
        return processingMessage;
    }

    public Long getId() {
        return id;
    }

    @JsonIgnore
    public AppUser getOwner() {
        return owner;
    }

    public void setOwner(AppUser owner) {
        this.owner = owner;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public void setExtractedText(String extractedText) {
        this.extractedText = extractedText;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<Subject> getSubjects() {
        return subjects;
    }

    public void setSubjects(List<Subject> subjects) {
        this.subjects = subjects;
    }

    public void addSubject(Subject subject) {
        subjects.add(subject);
        subject.setSyllabus(this);
    }
}
