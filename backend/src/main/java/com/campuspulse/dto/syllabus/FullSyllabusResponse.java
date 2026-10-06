package com.campuspulse.dto.syllabus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class FullSyllabusResponse {

    private Long id;
    private String name;
    private String fileName;
    private LocalDateTime createdAt;
    private List<SubjectResponse> subjects = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<SubjectResponse> getSubjects() {
        return subjects;
    }

    public void setSubjects(List<SubjectResponse> subjects) {
        this.subjects = subjects;
    }

    public static class SubjectResponse {

        private Long id;
        private String name;
        private String code;
        private List<UnitResponse> units = new ArrayList<>();

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public List<UnitResponse> getUnits() {
            return units;
        }

        public void setUnits(List<UnitResponse> units) {
            this.units = units;
        }
    }

    public static class UnitResponse {

        private Long id;
        private Integer unitNumber;
        private String name;
        private List<TopicResponse> topics = new ArrayList<>();

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Integer getUnitNumber() {
            return unitNumber;
        }

        public void setUnitNumber(Integer unitNumber) {
            this.unitNumber = unitNumber;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<TopicResponse> getTopics() {
            return topics;
        }

        public void setTopics(List<TopicResponse> topics) {
            this.topics = topics;
        }
    }

    public static class TopicResponse {

        private Long id;
        private String name;
        private String description;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
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
    }
}