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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Exports must use saved results only: generating recommendations made downloads and printing slow. */
class ExportNoAiCallTest {

    @Test
    void pdfAndExcelNeverGenerateRecommendations() throws Exception {
        AnalysisResultService analysis = mock(AnalysisResultService.class);
        OutcomeService outcomes = mock(OutcomeService.class);
        when(analysis.getReport(any(), any(), anyBoolean())).thenReturn(new SemanticGapReport(1L, 2L, 2, 1, 0, 1, 50.0, List.of(
                new SemanticSkillMatch("Java", 5L, "OOP in Java", 0.9, "MATCHED"),
                new SemanticSkillMatch("Docker", null, null, 0.1, "GAP"))));
        when(analysis.savedRecommendations(any(), any())).thenReturn(List.of());
        when(outcomes.outcomesReport(any(), any())).thenReturn(Map.of("poCoverage", List.of()));
        ReportExportService service = new ReportExportService(analysis, outcomes, mock(InstitutionRepository.class));
        Syllabus syllabus = new Syllabus();
        syllabus.setName("MCA Sem 3");
        JobPosting job = new JobPosting();
        job.setTitle("Backend Developer");

        byte[] pdf = service.pdf(syllabus, job);
        service.excel(syllabus, job);

        verify(analysis, never()).getRecommendations(any(), any(), anyBoolean());
        String text;
        try (var document = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            text = new org.apache.pdfbox.text.PDFTextStripper().getText(document).replaceAll("\\s+", " ");
        }
        assertTrue(text.contains("How to read this report"), text);
        assertTrue(text.contains("Recommendations: not generated yet"), text);
    }
}
