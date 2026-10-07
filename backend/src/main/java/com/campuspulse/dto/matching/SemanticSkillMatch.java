package com.campuspulse.dto.matching;

public class SemanticSkillMatch {

    private String skill;
    private Long topicId;
    private String topic;
    private double similarity;
    private String status;

    public SemanticSkillMatch(
            String skill,
            Long topicId,
            String topic,
            double similarity,
            String status) {

        this.skill = skill;
        this.topicId = topicId;
        this.topic = topic;
        this.similarity = similarity;
        this.status = status;
    }

    public String getSkill() {
        return skill;
    }

    public Long getTopicId() {
        return topicId;
    }

    public String getTopic() {
        return topic;
    }

    public double getSimilarity() {
        return similarity;
    }

    public String getStatus() {
        return status;
    }
}