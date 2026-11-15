package com.campuspulse.service.syllabus;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Reads the text layer of every page (fast, free) and decides which pages need an AI vision model
 * (plan 14, Phase C): empty or garbled pages in "auto" mode, every page in "always" mode.
 */
@Service
public class PdfTextExtractionService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PdfTextExtractionService.class);

    /** Fewer visible characters than this on a page = probably a scanned image. */
    static final int MIN_PAGE_CHARS = 80;

    /** The PDF, its page texts and which pages (1-based) still need reading. */
    public record PdfAnalysis(byte[] bytes, String sha256, List<String> pageTexts, List<Integer> pagesToRead) {
        public int pageCount() {
            return pageTexts.size();
        }

        /** Text of the pages that don't need reading, in page order. */
        public String knownText() {
            return joinPages(pageTexts, pagesToRead);
        }
    }

    private final String mode;

    public PdfTextExtractionService(@Value("${app.pdf.ai-mode:auto}") String mode) {
        this.mode = mode == null ? "auto" : mode.trim().toLowerCase(Locale.ROOT);
    }

    public PdfAnalysis analyse(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF file is empty");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF files are allowed");
        }
        byte[] bytes = file.getBytes();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            int pages = document.getNumberOfPages();
            if (pages == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The PDF contains no pages");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            List<String> texts = new ArrayList<>();
            List<Integer> toRead = new ArrayList<>();
            for (int page = 1; page <= pages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document).trim();
                texts.add(text);
                if ("always".equals(mode) || needsReading(text)) toRead.add(page);
            }
            log.info("PDF analysed: {} pages, {} need reading (mode {})", pages, toRead.size(), mode);
            return new PdfAnalysis(bytes, sha256(bytes), texts, toRead);
        }
    }

    /** True if the page's text layer is (nearly) empty or mostly unreadable symbols. */
    static boolean needsReading(String text) {
        if (text == null) return true;
        String visible = text.replaceAll("\\s+", "");
        if (visible.length() < MIN_PAGE_CHARS) return true;
        long readable = visible.chars()
                .filter(c -> Character.isLetterOrDigit(c) || ".,;:()-/&'\"%+#*[]".indexOf(c) >= 0)
                .count();
        long broken = visible.chars().filter(c -> c == '�' || Character.isISOControl(c)).count();
        return readable < visible.length() * 0.7 || broken > visible.length() * 0.05;
    }

    /** Page texts in order, leaving out the given pages (1-based); pages separated by blank lines. */
    public static String joinPages(List<String> pageTexts, List<Integer> skip) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < pageTexts.size(); i++) {
            if (skip.contains(i + 1)) continue;
            String text = pageTexts.get(i);
            if (text == null || text.isBlank()) continue;
            if (!out.isEmpty()) out.append("\n\n");
            out.append(text.trim());
        }
        return out.toString();
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
