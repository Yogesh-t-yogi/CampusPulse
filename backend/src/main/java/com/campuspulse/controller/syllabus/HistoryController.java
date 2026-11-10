package com.campuspulse.controller.syllabus;

import com.campuspulse.service.CurrentUserService;
import com.campuspulse.service.analysis.HistoryService;
import com.campuspulse.service.job.JobPostingService;
import com.campuspulse.service.syllabus.SyllabusService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** Syllabus versions, report history and version diff (Phase 5.3). */
@RestController
@RequestMapping("/api/syllabus/{id}")
public class HistoryController {

    private final HistoryService history;
    private final SyllabusService syllabusService;
    private final JobPostingService jobPostingService;
    private final CurrentUserService currentUserService;

    public HistoryController(HistoryService history, SyllabusService syllabusService,
                             JobPostingService jobPostingService, CurrentUserService currentUserService) {
        this.history = history;
        this.syllabusService = syllabusService;
        this.jobPostingService = jobPostingService;
        this.currentUserService = currentUserService;
    }

    /** Earlier versions the user can read (an unreadable one ends the list). */
    @GetMapping("/versions")
    public List<Map<String, Object>> versions(@PathVariable Long id) {
        requireReadable(id);
        return readableChain(id);
    }

    /** Coverage of this job over time, across this syllabus's versions. */
    @GetMapping("/history")
    public List<Map<String, Object>> history(@PathVariable Long id, @RequestParam Long jobId) {
        requireReadable(id);
        jobPostingService.requireAccessibleJob(jobId, userId());
        List<Long> ids = readableChain(id).stream().map(row -> ((Number) row.get("id")).longValue()).toList();
        return history.reportHistory(ids, jobId);
    }

    /** What changed from {@code against} (older) to this version. */
    @GetMapping("/diff")
    public Map<String, Object> diff(@PathVariable Long id, @RequestParam Long against,
                                    @RequestParam(required = false) Long jobId) {
        requireReadable(id);
        requireReadable(against);
        if (jobId != null) jobPostingService.requireAccessibleJob(jobId, userId());
        return history.diff(against, id, jobId);
    }

    private List<Map<String, Object>> readableChain(Long id) {
        List<Map<String, Object>> chain = history.versions(id);
        int end = chain.size();
        for (int i = 0; i < chain.size(); i++) {
            Long sid = ((Number) chain.get(i).get("id")).longValue();
            if (syllabusService.getSyllabusById(sid, userId()).isEmpty()) {
                end = i;
                break;
            }
        }
        return chain.subList(0, end);
    }

    private void requireReadable(Long id) {
        if (syllabusService.getSyllabusById(id, userId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Syllabus not found");
        }
    }

    private Long userId() {
        return currentUserService.getCurrentUser().getId();
    }
}
