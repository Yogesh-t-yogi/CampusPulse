package com.campuspulse.dto.ai;

import java.util.ArrayList;
import java.util.List;

public class AiSyllabusResponse {

    private List<AiSubject> subjects = new ArrayList<>();

    public AiSyllabusResponse() {
    }

    public List<AiSubject> getSubjects() {
        return subjects;
    }

    public void setSubjects(List<AiSubject> subjects) {
        this.subjects = subjects;
    }
}