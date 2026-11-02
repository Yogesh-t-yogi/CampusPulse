package com.campuspulse.dto.job;

import java.util.List;

public record ResumeMatchResponse(
        List<String> extractedSkills,
        List<ResumeJobMatch> matches) {
}
