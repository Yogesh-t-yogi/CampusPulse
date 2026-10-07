package com.campuspulse.dto.matching;

import java.util.List;

public class SkillMatchResponse {

    private Long jobId;
    private Long syllabusId;

    private List<String> matchedSkills;
    private List<String> missingSkills;

    private int totalJobSkills;
    private int matchedCount;
    private double matchPercentage;

    public SkillMatchResponse() {
    }

    public SkillMatchResponse(
            Long jobId,
            Long syllabusId,
            List<String> matchedSkills,
            List<String> missingSkills,
            int totalJobSkills,
            int matchedCount,
            double matchPercentage) {

        this.jobId = jobId;
        this.syllabusId = syllabusId;
        this.matchedSkills = matchedSkills;
        this.missingSkills = missingSkills;
        this.totalJobSkills = totalJobSkills;
        this.matchedCount = matchedCount;
        this.matchPercentage = matchPercentage;
    }

    public Long getJobId() {
        return jobId;
    }

    public Long getSyllabusId() {
        return syllabusId;
    }

    public List<String> getMatchedSkills() {
        return matchedSkills;
    }

    public List<String> getMissingSkills() {
        return missingSkills;
    }

    public int getTotalJobSkills() {
        return totalJobSkills;
    }

    public int getMatchedCount() {
        return matchedCount;
    }

    public double getMatchPercentage() {
        return matchPercentage;
    }
}