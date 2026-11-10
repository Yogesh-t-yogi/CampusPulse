package com.campuspulse.service.job;

import com.campuspulse.entity.job.JobPosting;
import com.campuspulse.entity.job.JobRequiredSkill;
import com.campuspulse.repository.job.JobPostingRepository;
import com.campuspulse.repository.job.JobRequiredSkillRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The bundled data/jobs starter CSV must stay importable through the admin CSV import. */
class StarterDatasetImportTest {

    private static final String NL = System.lineSeparator();

    private static final Path DATASET = Path.of("..", "data", "jobs", "india-tech-roles-starter.csv");

    @Test
    void starterDatasetImportsEveryRowWithSkills() throws Exception {
        JobPostingRepository jobs = mock(JobPostingRepository.class);
        JobRequiredSkillRepository skills = mock(JobRequiredSkillRepository.class);
        List<JobPosting> savedJobs = new ArrayList<>();
        List<JobRequiredSkill> savedSkills = new ArrayList<>();

        when(jobs.save(any(JobPosting.class))).thenAnswer(call -> {
            savedJobs.add(call.getArgument(0));
            return call.getArgument(0);
        });
        when(skills.saveAll(any())).thenAnswer(call -> {
            Iterable<JobRequiredSkill> batch = call.getArgument(0);
            batch.forEach(savedSkills::add);
            return savedSkills;
        });

        byte[] csv = Files.readAllBytes(DATASET);
        long dataRows = Files.readAllLines(DATASET).stream().skip(1).filter(l -> !l.isBlank()).count();

        int imported = new JobCatalogProvider(jobs, skills, new SkillAliasService())
                .importCsv(new MockMultipartFile("file", "starter.csv", "text/csv", csv)).imported();

        assertEquals(dataRows, imported);
        assertTrue(imported >= 30);
        assertTrue(savedSkills.size() >= imported * 5, "each role should list several skills");
        assertTrue(savedJobs.stream().allMatch(job -> job.getLocation() != null));
    }

    @Test
    void importingTheSameFileAgainSkipsExistingJobs() throws Exception {
        JobPostingRepository jobs = mock(JobPostingRepository.class);
        JobRequiredSkillRepository skills = mock(JobRequiredSkillRepository.class);
        when(jobs.existsByOwnerIsNullAndInstitutionIdIsNullAndTitleIgnoreCaseAndCompanyIgnoreCase(any(), any()))
                .thenReturn(true);
        byte[] csv = ("title,company,skills" + NL + "Dev,Acme,Java" + NL + "Dev,Acme,Java" + NL).getBytes();

        var result = new JobCatalogProvider(jobs, skills, new SkillAliasService())
                .importCsv(new MockMultipartFile("file", "again.csv", "text/csv", csv));

        assertEquals(0, result.imported());
        assertEquals(2, result.skipped());
    }
}
