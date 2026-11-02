package com.campuspulse.dto.recommendation;

import java.util.List;

public class AiRecommendation {

    private String skill;
    private String status;
    private String explanation;
    private String curriculumRecommendation;
    private String studentRecommendation;
    private String priority;
    private List<LearningRoadmapStep> learningRoadmap;

    public AiRecommendation() {
    }

    public AiRecommendation(
            String skill,
            String status,
            String explanation,
            String curriculumRecommendation,
            String studentRecommendation,
            String priority) {

        this.skill = skill;
        this.status = status;
        this.explanation = explanation;
        this.curriculumRecommendation =
                curriculumRecommendation;
        this.studentRecommendation =
                studentRecommendation;
        this.priority = priority;
    }

    public String getSkill() {
        return skill;
    }

    public String getStatus() {
        return status;
    }

    public String getExplanation() {
        return explanation;
    }

    public String getCurriculumRecommendation() {
        return curriculumRecommendation;
    }

    public String getStudentRecommendation() {
        return studentRecommendation;
    }

    public String getPriority() {
        return priority;
    }

    public List<LearningRoadmapStep> getLearningRoadmap() {
        return learningRoadmap;
    }

    public void setLearningRoadmap(
            List<LearningRoadmapStep> learningRoadmap) {
        this.learningRoadmap = learningRoadmap;
    }
}