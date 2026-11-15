package com.campuspulse.service.export;

import com.campuspulse.dto.matching.SemanticGapReport;
import com.campuspulse.dto.matching.SemanticSkillMatch;
import com.campuspulse.entity.job.JobPosting;
import com.campuspulse.entity.syllabus.Syllabus;
import com.campuspulse.repository.org.InstitutionRepository;
import com.campuspulse.service.analysis.AnalysisResultService;
import com.campuspulse.service.outcome.OutcomeService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportExportServiceTest {

    private ReportExportService service() {
        AnalysisResultService analysis = mock(AnalysisResultService.class);
        OutcomeService outcomes = mock(OutcomeService.class);
        SemanticGapReport report = new SemanticGapReport(1L, 2L, 3, 1, 1, 1, 50.0, List.of(
                new SemanticSkillMatch("Java", 5L, "OOP in Java", 0.9, "MATCHED"),
                new SemanticSkillMatch("Docker – containers", 6L, "Virtualisation", 0.4, "PARTIAL"),
                new SemanticSkillMatch("Kubernetes ☸", null, null, 0.1, "GAP")));
        when(analysis.getReport(any(), any(), anyBoolean())).thenReturn(report);
        when(analysis.getRecommendations(any(), any(), anyBoolean())).thenThrow(new RuntimeException("no LLM"));
        when(outcomes.outcomesReport(any(), any())).thenReturn(Map.of("poCoverage",
                List.of(Map.of("code", "PO1", "title", "Engineering knowledge", "coverage", 62.5))));
        return new ReportExportService(analysis, outcomes, mock(InstitutionRepository.class));
    }

    private static Syllabus syllabus() {
        Syllabus s = new Syllabus();
        s.setName("BE CSE Sem 5");
        return s;
    }

    private static JobPosting job() {
        JobPosting j = new JobPosting();
        j.setTitle("Backend Developer");
        return j;
    }

    @Test
    void pdfIsAValidPdfEvenWithUnicodeAndNoRecommendations() {
        byte[] pdf = service().pdf(syllabus(), job());
        assertTrue(pdf.length > 500);
        assertEquals("%PDF", new String(pdf, 0, 4));
    }

    @Test
    void excelIsAZipWorkbook() {
        byte[] xlsx = service().excel(syllabus(), job());
        assertEquals('P', xlsx[0]);
        assertEquals('K', xlsx[1]);
    }
}
