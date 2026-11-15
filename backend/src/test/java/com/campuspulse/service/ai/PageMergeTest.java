package com.campuspulse.service.ai;

import com.campuspulse.service.syllabus.ScannedPageReader.PageResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PageMergeTest {

    @Test
    void countsHowEachPageWasRead() {
        var merge = SyllabusProcessingService.mergePages(
                List.of("page one text", "", "", "layer four"),
                List.of(new PageResult(2, "AI two", "ai"),
                        new PageResult(3, "", "failed"),
                        new PageResult(4, "", "failed")));

        assertEquals("page one text\n\nAI two\n\nlayer four", merge.text());
        assertEquals(4, merge.summary().get("pages"));
        assertEquals(Map.of("text", 2, "ai", 1, "failed", 1), merge.summary().get("counts"));
        assertEquals(List.of(3), merge.summary().get("unreadablePages"));
    }
}
