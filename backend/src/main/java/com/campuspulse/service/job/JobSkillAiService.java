package com.campuspulse.service.job;

import com.campuspulse.entity.job.JobPosting;
import com.campuspulse.entity.job.JobRequiredSkill;
import com.campuspulse.repository.job.JobPostingRepository;
import com.campuspulse.repository.job.JobRequiredSkillRepository;
import com.campuspulse.service.ai.LlmService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class JobSkillAiService {

    private final JobPostingRepository jobPostingRepository;
    private final JobRequiredSkillRepository jobRequiredSkillRepository;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    public JobSkillAiService(
            JobPostingRepository jobPostingRepository,
            JobRequiredSkillRepository jobRequiredSkillRepository,
            LlmService llmService,
            ObjectMapper objectMapper) {

        this.jobPostingRepository = jobPostingRepository;
        this.jobRequiredSkillRepository = jobRequiredSkillRepository;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
    }

    public List<String> extractSkills(Long jobId) {

        // 1. Find the job
        JobPosting job = jobPostingRepository.findById(jobId)
                .orElseThrow(() ->
                        new RuntimeException("Job not found: " + jobId));

        // 2. Get job description
        String description = job.getDescription();

        if (description == null || description.isBlank()) {
            throw new RuntimeException(
                    "Job description is empty for job: " + jobId);
        }

        // 3. Create AI prompt
        String prompt = """
                You are an expert job market skill extraction system.

                Analyze the following job description.

                Extract ONLY skills that are explicitly required or strongly
                implied by the job description.

                Include:
                - Programming languages
                - Frameworks
                - Libraries
                - Databases
                - Cloud technologies
                - DevOps tools
                - Development tools
                - APIs
                - Professional technical skills

                Do NOT include:
                - Company names
                - Job titles
                - Locations
                - Soft skills
                - Generic words such as "development" or "experience"

                Return ONLY valid JSON in exactly this format:

                {
                  "skills": [
                    "Java",
                    "Spring Boot",
                    "PostgreSQL"
                  ]
                }

                Do not include markdown.
                Do not include ```json.
                Do not include explanations.

                Job Description:
                %s
                """.formatted(description);

        // 4. Send prompt to Groq
        String aiResponse = llmService.generateResponse(prompt);

        // 5. Parse AI response
        List<String> skills = parseSkills(aiResponse);

        // 6. Remove old skills to avoid duplicates
        jobRequiredSkillRepository
                .deleteAll(jobRequiredSkillRepository
                        .findByJobPostingId(jobId));

        // 7. Save new AI-extracted skills
        for (String skill : skills) {

            JobRequiredSkill requiredSkill =
                    new JobRequiredSkill();

            requiredSkill.setSkill(skill);
            requiredSkill.setJobPosting(job);

            jobRequiredSkillRepository.save(requiredSkill);
        }

        return skills;
    }

    private List<String> parseSkills(String aiResponse) {

        try {

            String cleanedResponse = aiResponse.trim();

            // Remove markdown code fences if the LLM accidentally returns them
            if (cleanedResponse.startsWith("```")) {
                cleanedResponse = cleanedResponse
                        .replaceFirst("^```json\\s*", "")
                        .replaceFirst("^```\\s*", "")
                        .replaceFirst("\\s*```$", "");
            }

            JsonNode root =
                    objectMapper.readTree(cleanedResponse);

            JsonNode skillsNode =
                    root.get("skills");

            if (skillsNode == null || !skillsNode.isArray()) {
                throw new RuntimeException(
                        "AI response does not contain a skills array");
            }

            List<String> skills = new ArrayList<>();

            for (JsonNode skillNode : skillsNode) {

                if (skillNode.isTextual()) {

                    String skill = skillNode.asText().trim();

                    if (!skill.isBlank()
                            && !skills.contains(skill)) {

                        skills.add(skill);
                    }
                }
            }

            if (skills.isEmpty()) {
                throw new RuntimeException(
                        "AI did not extract any skills");
            }

            return skills;

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to parse AI skill response: "
                            + e.getMessage(),
                    e);
        }
    }
}