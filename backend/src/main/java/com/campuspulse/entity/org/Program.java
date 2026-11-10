package com.campuspulse.entity.org;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A degree program of a department, e.g. "B.E. Computer Science and Engineering" (BE, CSE, 2021). */
@Entity
@Table(name = "program")
public class Program {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "department_id", nullable = false)
    private Long departmentId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 50)
    private String degree;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(length = 50)
    private String regulation;

    @Column(name = "duration_semesters", nullable = false)
    private int durationSemesters = 8;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDegree() { return degree; }
    public void setDegree(String degree) { this.degree = degree; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getRegulation() { return regulation; }
    public void setRegulation(String regulation) { this.regulation = regulation; }
    public int getDurationSemesters() { return durationSemesters; }
    public void setDurationSemesters(int durationSemesters) { this.durationSemesters = durationSemesters; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
