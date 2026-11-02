package com.campuspulse.dto.job;

import java.util.List;

public record ResumeJobMatch(
        Long jobId,
        String title,
        String company,
        String location,
        int matchPercentage,
        List<String> matchedSkills,
        List<String> missingSkills) {
}
