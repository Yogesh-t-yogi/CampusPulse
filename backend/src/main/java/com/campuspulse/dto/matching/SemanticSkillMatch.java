package com.campuspulse.dto.matching;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SemanticSkillMatch {

    private String skill;
    private Long topicId;
    private String topic;
    private double similarity;
    private String status;

    @JsonCreator
    public SemanticSkillMatch(
            @JsonProperty("skill") String skill,
            @JsonProperty("topicId") Long topicId,
            @JsonProperty("topic") String topic,
            @JsonProperty("similarity") double similarity,
            @JsonProperty("status") String status) {

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