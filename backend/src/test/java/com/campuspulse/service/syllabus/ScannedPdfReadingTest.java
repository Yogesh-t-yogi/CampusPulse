package com.campuspulse.service.syllabus;

import com.campuspulse.service.ai.LlmService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Plan 14 Phase C: text pages are read for free, scanned pages go to the AI vision model. */
class ScannedPdfReadingTest {

    private static final String UNIT_TEXT = "UNIT 1 Introduction to Data Structures: arrays, linked lists, stacks, "
            + "queues, time complexity and Big-O notation with examples.";

    /** Page 1 has a text layer; page 2 is only a picture (like a scan). */
    static byte[] twoPagePdf() throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage textPage = new PDPage();
            doc.addPage(textPage);
            try (PDPageContentStream cs = new PDPageContentStream(doc, textPage)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                cs.newLineAtOffset(40, 700);
                cs.showText(UNIT_TEXT);
                cs.endText();
            }
            PDPage scanPage = new PDPage();
            doc.addPage(scanPage);
            BufferedImage picture = new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = picture.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 400, 200);
            g.setColor(Color.BLACK);
            g.drawString("UNIT 2 Trees and Graphs", 20, 100);
            g.dispose();
            try (PDPageContentStream cs = new PDPageContentStream(doc, scanPage)) {
                cs.drawImage(LosslessFactory.createFromImage(doc, picture), 40, 400, 400, 200);
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void analyseFindsOnlyTheScannedPage() throws Exception {
        var pdf = new PdfTextExtractionService("auto")
                .analyse(new MockMultipartFile("file", "s.pdf", "application/pdf", twoPagePdf()));
        assertEquals(2, pdf.pageCount());
        assertEquals(List.of(2), pdf.pagesToRead());
        assertTrue(pdf.knownText().contains("Data Structures"));
        assertEquals(64, pdf.sha256().length());
    }

    @Test
    void alwaysModeSendsEveryPage() throws Exception {
        var pdf = new PdfTextExtractionService("always")
                .analyse(new MockMultipartFile("file", "s.pdf", "application/pdf", twoPagePdf()));
        assertEquals(List.of(1, 2), pdf.pagesToRead());
    }

    @Test
    void scannedPageIsReadByAiAndMergedInOrder() throws Exception {
        LlmService llm = mock(LlmService.class);
        when(llm.generateFromImages(anyString(), anyList())).thenReturn("```markdown\n## UNIT 2 Trees and Graphs\n```");
        JdbcTemplate jdbc = mock(JdbcTemplate.class); // no cached pages
        ScannedPageReader reader = new ScannedPageReader(llm, jdbc, 60, 2, 100);
        byte[] bytes = twoPagePdf();

        List<Integer> progress = new ArrayList<>();
        var results = reader.read(bytes, "abc", List.of(2), progress::add);

        assertEquals(1, results.size());
        assertEquals("ai", results.get(0).method());
        assertEquals("## UNIT 2 Trees and Graphs", results.get(0).text());
        assertEquals(1, progress.get(progress.size() - 1));
        verify(llm, times(1)).generateFromImages(anyString(), anyList());

        var merged = com.campuspulse.service.ai.SyllabusProcessingServiceAccess.merge(List.of(UNIT_TEXT, ""), results);
        assertTrue(merged.indexOf("UNIT 1") < merged.indexOf("UNIT 2"), merged);
    }

    @Test
    void pagesOverTheLimitAreSkippedWithoutCallingAi() throws Exception {
        LlmService llm = mock(LlmService.class);
        ScannedPageReader reader = new ScannedPageReader(llm, mock(JdbcTemplate.class), 1, 1, 100);
        when(llm.generateFromImages(anyString(), anyList())).thenReturn("page one");
        var results = reader.read(twoPagePdf(), "abc", List.of(1, 2), done -> { });
        assertEquals("ai", results.get(0).method());
        assertEquals("skipped", results.get(1).method());
        verify(llm, times(1)).generateFromImages(anyString(), anyList());
    }

    @Test
    void aiFailureWithoutTesseractMarksPageFailed() throws Exception {
        LlmService llm = mock(LlmService.class);
        when(llm.generateFromImages(anyString(), anyList())).thenThrow(new IllegalStateException("No AI provider could answer"));
        ScannedPageReader reader = new ScannedPageReader(llm, mock(JdbcTemplate.class), 60, 1, 100) {
            @Override
            boolean tesseractInstalled() {
                return false;
            }
        };
        var results = reader.read(twoPagePdf(), "abc", List.of(2), done -> { });
        assertEquals("failed", results.get(0).method());
        assertEquals("", results.get(0).text());
    }

    @Test
    void textChecksAndCleanup() {
        assertTrue(PdfTextExtractionService.needsReading(""));
        assertTrue(PdfTextExtractionService.needsReading("Page 3"));
        assertTrue(PdfTextExtractionService.needsReading("�".repeat(200)));
        assertFalse(PdfTextExtractionService.needsReading(UNIT_TEXT));
        assertEquals("", ScannedPageReader.clean("EMPTY"));
        assertEquals("a\nb", ScannedPageReader.clean("```\na\nb\n```"));
        assertEquals("a\n\nc", PdfTextExtractionService.joinPages(List.of("a", "b", "c"), List.of(2)));
    }

    @Test
    void noAiCallWhenNothingToRead() throws Exception {
        LlmService llm = mock(LlmService.class);
        new ScannedPageReader(llm, mock(JdbcTemplate.class), 60, 1, 100).read(twoPagePdf(), "abc", List.of(), d -> { });
        verify(llm, never()).generateFromImages(anyString(), anyList());
    }
}
