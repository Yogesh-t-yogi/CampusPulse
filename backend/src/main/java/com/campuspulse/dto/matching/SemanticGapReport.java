package com.campuspulse.dto.matching;

import java.util.List;

public class SemanticGapReport {

    private Long jobId;
    private Long syllabusId;

    private int totalSkills;
    private int matchedSkills;
    private int partialSkills;
    private int gapSkills;

    private double coveragePercentage;

    private List<SemanticSkillMatch> skillDetails;

    public SemanticGapReport(
            Long jobId,
            Long syllabusId,
            int totalSkills,
            int matchedSkills,
            int partialSkills,
            int gapSkills,
            double coveragePercentage,
            List<SemanticSkillMatch> skillDetails) {

        this.jobId = jobId;
        this.syllabusId = syllabusId;
        this.totalSkills = totalSkills;
        this.matchedSkills = matchedSkills;
        this.partialSkills = partialSkills;
        this.gapSkills = gapSkills;
        this.coveragePercentage = coveragePercentage;
        this.skillDetails = skillDetails;
    }

    public Long getJobId() {
        return jobId;
    }

    public Long getSyllabusId() {
        return syllabusId;
    }

    public int getTotalSkills() {
        return totalSkills;
    }

    public int getMatchedSkills() {
        return matchedSkills;
    }

    public int getPartialSkills() {
        return partialSkills;
    }

    public int getGapSkills() {
        return gapSkills;
    }

    public double getCoveragePercentage() {
        return coveragePercentage;
    }

    public List<SemanticSkillMatch> getSkillDetails() {
        return skillDetails;
    }
}