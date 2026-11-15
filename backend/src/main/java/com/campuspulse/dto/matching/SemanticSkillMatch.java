package com.campuspulse.dto.matching;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SemanticSkillMatch {

    private String skill;
    private Long topicId;
    private String topic;
    private double similarity;
    private String status;
    // SEMANTIC, KEYWORD (skill named in a topic) or null (no match)
    private String matchedBy;

    @JsonCreator
    public SemanticSkillMatch(
            @JsonProperty("skill") String skill,
            @JsonProperty("topicId") Long topicId,
            @JsonProperty("topic") String topic,
            @JsonProperty("similarity") double similarity,
            @JsonProperty("status") String status,
            @JsonProperty("matchedBy") String matchedBy) {

        this.skill = skill;
        this.topicId = topicId;
        this.topic = topic;
        this.similarity = similarity;
        this.status = status;
        this.matchedBy = matchedBy;
    }

    public SemanticSkillMatch(
            String skill, Long topicId, String topic, double similarity, String status) {
        this(skill, topicId, topic, similarity, status, null);
    }

    public String getMatchedBy() {
        return matchedBy;
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