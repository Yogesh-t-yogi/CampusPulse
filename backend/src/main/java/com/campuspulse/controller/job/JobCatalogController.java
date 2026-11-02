package com.campuspulse.controller.job;

import com.campuspulse.entity.job.JobPosting;
import com.campuspulse.service.job.JobCatalogProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/job-catalog")
public class JobCatalogController {

    private final JobCatalogProvider jobCatalogProvider;

    public JobCatalogController(JobCatalogProvider jobCatalogProvider) {
        this.jobCatalogProvider = jobCatalogProvider;
    }

    @GetMapping
    public List<JobPosting> getCatalogJobs() {
        return jobCatalogProvider.searchJobs(null, null, 200);
    }

    @GetMapping("/skill-demand")
    public List<Map<String, Object>> getSkillDemand() {
        return jobCatalogProvider.getSkillDemand();
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> importCsv(
            @RequestParam("file") MultipartFile file) throws IOException {
        try {
            int imported = jobCatalogProvider.importCsv(file);
            Map<String, Object> payload = new HashMap<>();
            payload.put("imported", imported);
            payload.put("message", "Dataset jobs imported successfully.");
            return ResponseEntity.ok(payload);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put(
                    "error",
                    e.getMessage() == null ? "Invalid CSV dataset." : e.getMessage()
            );
            return ResponseEntity.badRequest().body(error);
        }
    }
}
