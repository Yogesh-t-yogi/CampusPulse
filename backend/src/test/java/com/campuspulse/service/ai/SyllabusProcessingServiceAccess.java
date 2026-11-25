package com.campuspulse.service.ai;

import com.campuspulse.service.syllabus.ScannedPageReader;

import java.util.List;

/** Test helper: reaches the package-private page merge in SyllabusProcessingService. */
public final class SyllabusProcessingServiceAccess {

    private SyllabusProcessingServiceAccess() {
    }

    public static String merge(List<String> pageTexts, List<ScannedPageReader.PageResult> results) {
        return SyllabusProcessingService.mergePages(pageTexts, results).text();
    }

    /** The "how each page was read" summary saved for the status view. */
    public static java.util.Map<String, Object> summary(List<String> pageTexts, List<ScannedPageReader.PageResult> results) {
        return SyllabusProcessingService.mergePages(pageTexts, results).summary();
    }
}
