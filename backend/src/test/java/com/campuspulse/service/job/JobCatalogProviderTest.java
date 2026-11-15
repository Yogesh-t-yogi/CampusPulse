package com.campuspulse.service.job;

import com.campuspulse.entity.job.JobPosting;
import com.campuspulse.entity.job.JobRequiredSkill;
import com.campuspulse.repository.job.JobPostingRepository;
import com.campuspulse.repository.job.JobRequiredSkillRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobCatalogProviderTest {

    @Test
    void importsQuotedCsvAndCanonicalizesSkills() throws IOException {
        JobPostingRepository jobRepository =
                mock(JobPostingRepository.class);
        JobRequiredSkillRepository skillRepository =
                mock(JobRequiredSkillRepository.class);
        AtomicReference<List<JobRequiredSkill>> savedSkills =
                new AtomicReference<>(List.of());

        when(jobRepository.save(any(JobPosting.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(skillRepository.saveAll(any()))
                .thenAnswer(invocation -> {
                    Iterable<JobRequiredSkill> skills =
                            invocation.getArgument(0);
                    List<JobRequiredSkill> captured = new ArrayList<>();
                    skills.forEach(captured::add);
                    savedSkills.set(captured);
                    return captured;
                });

        JobCatalogProvider provider = new JobCatalogProvider(
                jobRepository,
                skillRepository,
                new SkillAliasService()
        );
        String csv = """
                job_title,company,location,description,skills
                "Full Stack, Engineer","Acme, Inc",Bengaluru,"Build apps, services","JS;Postgres;Node.js"
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "jobs.csv",
                "text/csv",
                csv.getBytes(StandardCharsets.UTF_8)
        );

        assertEquals(1, provider.importCsv(file).imported());

        var savedJob = org.mockito.ArgumentCaptor.forClass(JobPosting.class);
        verify(jobRepository).save(savedJob.capture());
        assertEquals("Full Stack, Engineer", savedJob.getValue().getTitle());
        assertEquals("Acme, Inc", savedJob.getValue().getCompany());
        assertEquals(
                List.of("JavaScript", "PostgreSQL", "Node.js"),
                savedSkills.get().stream().map(JobRequiredSkill::getSkill).toList()
        );
    }
}
