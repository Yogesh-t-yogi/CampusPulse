package com.campuspulse.controller.job;

import com.campuspulse.entity.job.JobPosting;
import com.campuspulse.service.CurrentUserService;
import com.campuspulse.service.job.JobCatalogProvider;
import com.campuspulse.service.org.TenantService;
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
    private final CurrentUserService currentUserService;
    private final TenantService tenantService;

    public JobCatalogController(
            JobCatalogProvider jobCatalogProvider,
            CurrentUserService currentUserService,
            TenantService tenantService) {
        this.jobCatalogProvider = jobCatalogProvider;
        this.currentUserService = currentUserService;
        this.tenantService = tenantService;
    }

    private Long actingCollege() {
        return tenantService.actingInstitutionId(currentUserService.getCurrentUser());
    }

    /** Platform admin imports into the global catalog, a college admin into their college's. */
    private Long targetCatalog() {
        var user = currentUserService.getCurrentUser();
        return tenantService.isSuperAdmin(user) ? null : user.getInstitutionId();
    }

    @GetMapping
    public List<JobPosting> getCatalogJobs() {
        return jobCatalogProvider.searchJobs(null, null, 200, actingCollege());
    }

    @GetMapping("/skill-demand")
    public List<Map<String, Object>> getSkillDemand() {
        return jobCatalogProvider.getSkillDemand(actingCollege());
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> importCsv(
            @RequestParam("file") MultipartFile file) throws IOException {
        try {
            var result = jobCatalogProvider.importCsv(file, targetCatalog());
            Map<String, Object> payload = new HashMap<>();
            payload.put("imported", result.imported());
            payload.put("skipped", result.skipped());
            payload.put("message", result.skipped() == 0
                    ? "Imported " + result.imported() + " jobs."
                    : "Imported " + result.imported() + " jobs; skipped " + result.skipped()
                        + " already in the catalog.");
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
