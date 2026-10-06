package com.campuspulse.dto.ai;

import java.util.ArrayList;
import java.util.List;

public class AiUnit {

    private Integer unitNumber;

    private String name;

    private List<AiTopic> topics = new ArrayList<>();

    public AiUnit() {
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

    public List<AiTopic> getTopics() {
        return topics;
    }

    public void setTopics(List<AiTopic> topics) {
        this.topics = topics;
    }
}