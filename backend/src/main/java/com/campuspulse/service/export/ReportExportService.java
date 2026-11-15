package com.campuspulse.service.export;

import com.campuspulse.dto.matching.SemanticGapReport;
import com.campuspulse.dto.matching.SemanticSkillMatch;
import com.campuspulse.dto.recommendation.AiRecommendation;
import com.campuspulse.entity.job.JobPosting;
import com.campuspulse.entity.org.Institution;
import com.campuspulse.entity.syllabus.Syllabus;
import com.campuspulse.repository.org.InstitutionRepository;
import com.campuspulse.service.analysis.AnalysisResultService;
import com.campuspulse.service.outcome.OutcomeService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Report exports (Phase 5.4): a printable PDF and an Excel workbook of one syllabus × job report.
 * Both use the saved gap report; recommendations and CO/PO are included when available.
 */
@Service
public class ReportExportService {

    private static final Logger log = LoggerFactory.getLogger(ReportExportService.class);

    private final AnalysisResultService analysisResultService;
    private final OutcomeService outcomeService;
    private final InstitutionRepository institutions;

    public ReportExportService(AnalysisResultService analysisResultService, OutcomeService outcomeService,
                               InstitutionRepository institutions) {
        this.analysisResultService = analysisResultService;
        this.outcomeService = outcomeService;
        this.institutions = institutions;
    }

    /** Everything an export needs, gathered once. */
    record Data(String college, Syllabus syllabus, JobPosting job, SemanticGapReport report,
                List<AiRecommendation> recommendations, List<Map<String, Object>> poCoverage) {
    }

    Data gather(Syllabus syllabus, JobPosting job) {
        SemanticGapReport report = analysisResultService.getReport(job.getId(), syllabus.getId(), false);
        List<AiRecommendation> recommendations = List.of();
        try {
            recommendations = analysisResultService.getRecommendations(job.getId(), syllabus.getId(), false);
        } catch (RuntimeException e) {
            log.warn("Export without recommendations: {}", e.getMessage());
        }
        List<Map<String, Object>> poCoverage = List.of();
        try {
            Object po = outcomeService.outcomesReport(syllabus, job.getId()).get("poCoverage");
            if (po instanceof List<?> list) {
                List<Map<String, Object>> rows = new ArrayList<>();
                for (Object item : list) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> row = (Map<String, Object>) item;
                    rows.add(row);
                }
                poCoverage = rows;
            }
        } catch (RuntimeException e) {
            log.warn("Export without CO/PO: {}", e.getMessage());
        }
        String college = syllabus.getInstitutionId() == null ? "" :
                institutions.findById(syllabus.getInstitutionId()).map(Institution::getName).orElse("");
        return new Data(college, syllabus, job, report, recommendations, poCoverage);
    }

    // ================================================================ Excel

    public byte[] excel(Syllabus syllabus, JobPosting job) {
        Data d = gather(syllabus, job);
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle bold = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            bold.setFont(font);

            Sheet summary = wb.createSheet("Summary");
            String[][] lines = {
                    {"College", d.college()},
                    {"Syllabus", syllabus.getName()},
                    {"Job role", job.getTitle()},
                    {"Date", LocalDate.now().toString()},
                    {"Coverage %", String.valueOf(d.report().getCoveragePercentage())},
                    {"Skills", String.valueOf(d.report().getTotalSkills())},
                    {"Covered", String.valueOf(d.report().getMatchedSkills())},
                    {"Partly covered", String.valueOf(d.report().getPartialSkills())},
                    {"Gaps", String.valueOf(d.report().getGapSkills())},
            };
            for (int i = 0; i < lines.length; i++) {
                Row row = summary.createRow(i);
                row.createCell(0).setCellValue(lines[i][0]);
                row.getCell(0).setCellStyle(bold);
                row.createCell(1).setCellValue(lines[i][1]);
            }
            summary.autoSizeColumn(0);
            summary.autoSizeColumn(1);

            Sheet skills = wb.createSheet("Skills");
            header(skills, bold, "Skill", "Status", "Closest topic", "Similarity", "Matched by");
            int r = 1;
            for (SemanticSkillMatch m : d.report().getSkillDetails()) {
                Row row = skills.createRow(r++);
                row.createCell(0).setCellValue(m.getSkill());
                row.createCell(1).setCellValue(m.getStatus());
                row.createCell(2).setCellValue(m.getTopic() == null ? "" : m.getTopic());
                row.createCell(3).setCellValue(Math.round(m.getSimilarity() * 100) / 100.0);
                row.createCell(4).setCellValue(m.getMatchedBy() == null ? "" : m.getMatchedBy());
            }
            autosize(skills, 5);

            Sheet recs = wb.createSheet("Recommendations");
            header(recs, bold, "Skill", "Priority", "Why", "Curriculum change", "For students");
            r = 1;
            for (AiRecommendation rec : d.recommendations()) {
                Row row = recs.createRow(r++);
                row.createCell(0).setCellValue(nz(rec.getSkill()));
                row.createCell(1).setCellValue(nz(rec.getPriority()));
                row.createCell(2).setCellValue(nz(rec.getExplanation()));
                row.createCell(3).setCellValue(nz(rec.getCurriculumRecommendation()));
                row.createCell(4).setCellValue(nz(rec.getStudentRecommendation()));
            }
            autosize(recs, 2);

            Sheet po = wb.createSheet("PO coverage");
            header(po, bold, "Code", "Title", "Mapped COs", "Coverage %");
            r = 1;
            for (Map<String, Object> p : d.poCoverage()) {
                Row row = po.createRow(r++);
                row.createCell(0).setCellValue(String.valueOf(p.get("code")));
                row.createCell(1).setCellValue(String.valueOf(p.get("title")));
                row.createCell(2).setCellValue(String.valueOf(p.getOrDefault("courseOutcomes", "")));
                Object cov = p.get("coverage");
                if (cov instanceof Number n) row.createCell(3).setCellValue(n.doubleValue());
                else row.createCell(3).setCellValue("—");
            }
            autosize(po, 4);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void header(Sheet sheet, CellStyle bold, String... names) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < names.length; i++) {
            row.createCell(i).setCellValue(names[i]);
            row.getCell(i).setCellStyle(bold);
        }
    }

    private static void autosize(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
            if (sheet.getColumnWidth(i) > 60 * 256) sheet.setColumnWidth(i, 60 * 256);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    // ================================================================ PDF

    public byte[] pdf(Syllabus syllabus, JobPosting job) {
        Data d = gather(syllabus, job);
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Pdf pdf = new Pdf(doc);
            pdf.text(d.college().isBlank() ? "CampusPulse" : d.college(), 10, false, Color.GRAY);
            pdf.text("Skill gap report", 20, true, Color.BLACK);
            pdf.text(syllabus.getName() + "  vs  " + job.getTitle(), 12, false, Color.DARK_GRAY);
            pdf.text("Generated " + LocalDate.now(), 9, false, Color.GRAY);
            pdf.gap(10);

            SemanticGapReport rep = d.report();
            pdf.text("Coverage: " + Math.round(rep.getCoveragePercentage()) + "%", 14, true, Color.BLACK);
            pdf.bar(rep.getMatchedSkills(), rep.getPartialSkills(), rep.getGapSkills());
            pdf.text("Covered " + rep.getMatchedSkills() + "   Partly " + rep.getPartialSkills()
                    + "   Gaps " + rep.getGapSkills() + "   (of " + rep.getTotalSkills() + " skills)", 10, false, Color.DARK_GRAY);
            pdf.gap(12);

            pdf.text("Skills", 14, true, Color.BLACK);
            for (SemanticSkillMatch m : rep.getSkillDetails()) {
                String topic = m.getTopic() == null || "GAP".equals(m.getStatus()) ? "" : "  -  " + m.getTopic();
                pdf.text(label(m.getStatus()) + "  " + m.getSkill() + topic, 9, false, statusColor(m.getStatus()));
            }

            if (!d.recommendations().isEmpty()) {
                pdf.gap(12);
                pdf.text("Recommendations", 14, true, Color.BLACK);
                for (AiRecommendation rec : d.recommendations()) {
                    pdf.gap(4);
                    pdf.text(nz(rec.getSkill()) + (rec.getPriority() == null ? "" : "  (" + rec.getPriority() + ")"), 10, true, Color.BLACK);
                    pdf.paragraph("Curriculum: " + nz(rec.getCurriculumRecommendation()), 9);
                    pdf.paragraph("Students: " + nz(rec.getStudentRecommendation()), 9);
                }
            }

            if (!d.poCoverage().isEmpty()) {
                pdf.gap(12);
                pdf.text("Program outcome coverage (NBA)", 14, true, Color.BLACK);
                for (Map<String, Object> p : d.poCoverage()) {
                    Object cov = p.get("coverage");
                    String value = cov instanceof Number n ? Math.round(n.doubleValue()) + "%" : "not mapped";
                    pdf.text(p.get("code") + "  " + p.get("title") + "  -  " + value, 9, false, Color.DARK_GRAY);
                }
            }
            pdf.close();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String label(String status) {
        return switch (status == null ? "" : status) {
            case "MATCHED" -> "[covered]";
            case "PARTIAL" -> "[partly]";
            default -> "[gap]";
        };
    }

    private static Color statusColor(String status) {
        return switch (status == null ? "" : status) {
            case "MATCHED" -> new Color(21, 128, 61);
            case "PARTIAL" -> new Color(180, 83, 9);
            default -> new Color(185, 28, 28);
        };
    }

    /** Tiny top-down writer with page breaks and word wrap (standard fonts, WinAnsi text only). */
    private static final class Pdf {
        private static final float MARGIN = 50;
        private final PDDocument doc;
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPageContentStream stream;
        private float y;

        Pdf(PDDocument doc) throws IOException {
            this.doc = doc;
            newPage();
        }

        private void newPage() throws IOException {
            if (stream != null) stream.close();
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            stream = new PDPageContentStream(doc, page);
            y = page.getMediaBox().getHeight() - MARGIN;
        }

        private void ensure(float height) throws IOException {
            if (y - height < MARGIN) newPage();
        }

        void gap(float h) {
            y -= h;
        }

        void text(String raw, float size, boolean bold, Color color) throws IOException {
            ensure(size + 4);
            y -= size + 4;
            stream.beginText();
            stream.setFont(bold ? boldFont : regular, size);
            stream.setNonStrokingColor(color);
            stream.newLineAtOffset(MARGIN, y);
            stream.showText(safe(raw, bold ? boldFont : regular));
            stream.endText();
        }

        void paragraph(String raw, float size) throws IOException {
            float width = PDRectangle.A4.getWidth() - 2 * MARGIN;
            StringBuilder line = new StringBuilder();
            for (String word : safe(raw, regular).split("\\s+")) {
                String next = line.isEmpty() ? word : line + " " + word;
                if (regular.getStringWidth(next) / 1000 * size > width && !line.isEmpty()) {
                    text(line.toString(), size, false, Color.DARK_GRAY);
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(next);
                }
            }
            if (!line.isEmpty()) text(line.toString(), size, false, Color.DARK_GRAY);
        }

        /** Stacked bar: covered / partly / gap. */
        void bar(int matched, int partial, int gaps) throws IOException {
            int total = Math.max(1, matched + partial + gaps);
            float width = PDRectangle.A4.getWidth() - 2 * MARGIN;
            ensure(22);
            y -= 18;
            float x = MARGIN;
            int[] parts = {matched, partial, gaps};
            Color[] colors = {statusColor("MATCHED"), statusColor("PARTIAL"), statusColor("GAP")};
            for (int i = 0; i < 3; i++) {
                float w = width * parts[i] / total;
                stream.setNonStrokingColor(colors[i]);
                stream.addRect(x, y, w, 12);
                stream.fill();
                x += w;
            }
        }

        void close() throws IOException {
            stream.close();
        }

        /** Drop characters the standard font can't encode (keeps the export from failing). */
        private static String safe(String raw, PDType1Font font) {
            if (raw == null) return "";
            StringBuilder sb = new StringBuilder();
            for (char c : raw.replace('–', '-').replace('—', '-').replace('’', '\'').toCharArray()) {
                try {
                    font.encode(String.valueOf(c));
                    sb.append(c == '\n' || c == '\t' ? ' ' : c);
                } catch (IOException | IllegalArgumentException e) {
                    sb.append('?');
                }
            }
            return sb.toString();
        }
    }
}
