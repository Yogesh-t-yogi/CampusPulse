package com.campuspulse.controller.org;

import com.campuspulse.service.CurrentUserService;
import com.campuspulse.service.org.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Department / college dashboard (staff). Scope and role checks in DashboardService. */
@RestController
public class DashboardController {

    private final DashboardService dashboardService;
    private final CurrentUserService currentUserService;

    public DashboardController(DashboardService dashboardService, CurrentUserService currentUserService) {
        this.dashboardService = dashboardService;
        this.currentUserService = currentUserService;
    }

    // ?departmentId= (admins; HOD/faculty always get their own) &jobIds=1,2 (benchmark roles, ≤5)
    @GetMapping("/api/dashboard")
    public Map<String, Object> overview(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) List<Long> jobIds) {
        return dashboardService.overview(currentUserService.getCurrentUser(), departmentId, jobIds);
    }
}
