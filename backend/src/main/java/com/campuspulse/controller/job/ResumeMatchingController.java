package com.campuspulse.controller.job;

import com.campuspulse.dto.job.ResumeMatchResponse;
import com.campuspulse.service.CurrentUserService;
import com.campuspulse.service.job.ResumeMatchingService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/resume")
public class ResumeMatchingController {

    private final ResumeMatchingService resumeMatchingService;
    private final CurrentUserService currentUserService;

    public ResumeMatchingController(
            ResumeMatchingService resumeMatchingService,
            CurrentUserService currentUserService) {
        this.resumeMatchingService = resumeMatchingService;
        this.currentUserService = currentUserService;
    }

    @PostMapping(value = "/match", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResumeMatchResponse matchResume(
            @RequestParam("file") MultipartFile file) throws IOException {
        return resumeMatchingService.matchResume(
                file,
                currentUserService.getCurrentUser().getId()
        );
    }
}
