package com.campuspulse.controller.ai;

import com.campuspulse.service.ai.LlmService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiTestController {

    private final LlmService llmService;

    public AiTestController(LlmService llmService) {
        this.llmService = llmService;
    }

    @GetMapping("/test")
    public String testAi(
            @RequestParam(
                    defaultValue = "Explain Spring Boot in one sentence."
            )
            String prompt) {

        return llmService.generateResponse(prompt);
    }
}