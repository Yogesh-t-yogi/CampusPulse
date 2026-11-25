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

    // ------------------------------------------------------------------ TC-18

    /** Overview figures as the report page shows them (same SemanticGapReport + PO coverage the API returns). */
    private static final SemanticGapReport OVERVIEW = new SemanticGapReport(1L, 2L, 6, 3, 2, 1, 66.7, List.of(
            new SemanticSkillMatch("Java", 5L, "OOP in Java", 0.91, "MATCHED"),
            new SemanticSkillMatch("SQL", 7L, "Relational databases", 0.83, "MATCHED"),
            new SemanticSkillMatch("REST API", 8L, "Web services", 0.77, "MATCHED"),
            new SemanticSkillMatch("Docker", 6L, "Virtualisation", 0.41, "PARTIAL"),
            new SemanticSkillMatch("Git", 9L, "Software tools", 0.33, "PARTIAL"),
            new SemanticSkillMatch("Kubernetes", null, null, 0.12, "GAP")));
    private static final List<Map<String, Object>> PO_COVERAGE = List.of(
            Map.of("code", "PO1", "title", "Engineering knowledge", "coverage", 68.8, "courseOutcomes", List.of("CO1", "CO2")),
            Map.of("code", "PO5", "title", "Modern tool usage", "coverage", 50.0, "courseOutcomes", List.of("CO2")));

    private ReportExportService overviewService() {
        AnalysisResultService analysis = mock(AnalysisResultService.class);
        OutcomeService outcomes = mock(OutcomeService.class);
        when(analysis.getReport(any(), any(), anyBoolean())).thenReturn(OVERVIEW);
        when(analysis.getRecommendations(any(), any(), anyBoolean())).thenReturn(List.of());
        when(outcomes.outcomesReport(any(), any())).thenReturn(Map.of("poCoverage", PO_COVERAGE));
        return new ReportExportService(analysis, outcomes, mock(InstitutionRepository.class));
    }

    private static Map<String, String> summarySheet(org.apache.poi.ss.usermodel.Workbook workbook) {
        Map<String, String> values = new java.util.LinkedHashMap<>();
        for (org.apache.poi.ss.usermodel.Row row : workbook.getSheet("Summary")) {
            values.put(row.getCell(0).getStringCellValue(), row.getCell(1).getStringCellValue());
        }
        return values;
    }

    @Test
    void tc18_excelHasTheSameCoverageFiguresAsTheReportOverview() throws Exception {
        byte[] xlsx = overviewService().excel(syllabus(), job());
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(xlsx))) {
            Map<String, String> summary = summarySheet(workbook);
            assertEquals(OVERVIEW.getCoveragePercentage(), Double.parseDouble(summary.get("Coverage %")));
            assertEquals(OVERVIEW.getTotalSkills(), Integer.parseInt(summary.get("Skills")));
            assertEquals(OVERVIEW.getMatchedSkills(), Integer.parseInt(summary.get("Covered")));
            assertEquals(OVERVIEW.getPartialSkills(), Integer.parseInt(summary.get("Partly covered")));
            assertEquals(OVERVIEW.getGapSkills(), Integer.parseInt(summary.get("Gaps")));
            assertEquals("BE CSE Sem 5", summary.get("Syllabus"));
            assertEquals("Backend Developer", summary.get("Job role"));

            var skills = workbook.getSheet("Skills");
            assertEquals(OVERVIEW.getSkillDetails().size(), skills.getLastRowNum(), "one row per skill");
            Map<String, Long> statusCounts = new java.util.HashMap<>();
            for (int r = 1; r <= skills.getLastRowNum(); r++) {
                var row = skills.getRow(r);
                SemanticSkillMatch expected = OVERVIEW.getSkillDetails().get(r - 1);
                assertEquals(expected.getSkill(), row.getCell(0).getStringCellValue());
                assertEquals(expected.getStatus(), row.getCell(1).getStringCellValue());
                assertEquals(expected.getSimilarity(), row.getCell(3).getNumericCellValue(), 0.005);
                statusCounts.merge(row.getCell(1).getStringCellValue(), 1L, Long::sum);
            }
            assertEquals(Map.of("MATCHED", 3L, "PARTIAL", 2L, "GAP", 1L), statusCounts, "rows agree with the totals");

            var po = workbook.getSheet("PO coverage");
            assertEquals(PO_COVERAGE.size(), po.getLastRowNum());
            for (int r = 1; r <= po.getLastRowNum(); r++) {
                assertEquals(PO_COVERAGE.get(r - 1).get("code"), po.getRow(r).getCell(0).getStringCellValue());
                assertEquals((Double) PO_COVERAGE.get(r - 1).get("coverage"), po.getRow(r).getCell(3).getNumericCellValue());
            }
        }
    }

    @Test
    void tc18_pdfShowsTheSameCoverageFiguresAsTheReportOverview() throws Exception {
        byte[] pdf = overviewService().pdf(syllabus(), job());
        String text;
        try (var document = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
        }
        String flat = text.replaceAll("\\s+", " ");
        assertTrue(flat.contains("Coverage: " + Math.round(OVERVIEW.getCoveragePercentage()) + "%"), flat);
        assertTrue(flat.contains("Covered " + OVERVIEW.getMatchedSkills() + " Partly " + OVERVIEW.getPartialSkills()
                + " Gaps " + OVERVIEW.getGapSkills() + " (of " + OVERVIEW.getTotalSkills() + " skills)"), flat);
        for (SemanticSkillMatch skill : OVERVIEW.getSkillDetails()) {
            assertTrue(flat.contains(skill.getSkill()), skill.getSkill() + " missing from PDF");
        }
        assertTrue(flat.contains("PO1 Engineering knowledge - 69%"), flat);
        assertTrue(flat.contains("PO5 Modern tool usage - 50%"), flat);
    }

    @Test
    void tc18_pdfAndExcelAgreeWithEachOther() throws Exception {
        ReportExportService service = overviewService();
        Map<String, String> summary;
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(service.excel(syllabus(), job())))) {
            summary = summarySheet(workbook);
        }
        String pdfText;
        try (var document = org.apache.pdfbox.Loader.loadPDF(service.pdf(syllabus(), job()))) {
            pdfText = new org.apache.pdfbox.text.PDFTextStripper().getText(document).replaceAll("\\s+", " ");
        }
        long rounded = Math.round(Double.parseDouble(summary.get("Coverage %")));
        assertTrue(pdfText.contains("Coverage: " + rounded + "%"), pdfText);
        assertTrue(pdfText.contains("Covered " + summary.get("Covered") + " Partly " + summary.get("Partly covered")
                + " Gaps " + summary.get("Gaps") + " (of " + summary.get("Skills") + " skills)"), pdfText);
    }
}
