package com.campuspulse.service.syllabus;

import com.campuspulse.service.ai.LlmService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/**
 * Reads scanned / garbled PDF pages (plan 14, Phase C): page → JPEG → AI vision model through the
 * provider chain (Llama → Gemini → Groq), then Tesseract only if it is installed. Results are cached
 * by file hash + page so re-processing never calls the AI again.
 */
@Service
public class ScannedPageReader {

    private static final Logger log = LoggerFactory.getLogger(ScannedPageReader.class);

    static final String PROMPT = """
            This image is one page of a university course syllabus. Transcribe ALL of its text exactly,
            in reading order. Keep the structure: put course/subject titles, units and modules on their own
            lines (Markdown headings are fine), keep topic lists as lists, and write tables as simple rows.
            Do not summarise, translate, explain or add anything. If the page has no text, answer EMPTY.
            """;

    /** How one page was read. */
    public record PageResult(int page, String text, String method) {
    }

    private final LlmService llmService;
    private final JdbcTemplate jdbc;
    private final int maxPages;
    private final int parallel;
    private final int dpi;
    private volatile Boolean tesseractAvailable;

    public ScannedPageReader(LlmService llmService, JdbcTemplate jdbc,
                             @Value("${app.pdf.ai-max-pages:60}") int maxPages,
                             @Value("${app.pdf.ai-parallel:3}") int parallel,
                             @Value("${app.pdf.render-dpi:150}") int dpi) {
        this.llmService = llmService;
        this.jdbc = jdbc;
        this.maxPages = Math.max(1, maxPages);
        this.parallel = Math.max(1, Math.min(6, parallel));
        this.dpi = Math.max(72, Math.min(300, dpi));
    }

    /** True if some way to read page images exists (AI with vision, or Tesseract). */
    public boolean canRead() {
        return llmService.status().stream().anyMatch(p -> !String.valueOf(p.get("visionModel")).isBlank())
                || tesseractInstalled();
    }

    /**
     * Reads the given pages (1-based). {@code progress} gets the number of pages done so far.
     * Pages over the limit are returned with method "skipped".
     */
    public List<PageResult> read(byte[] pdf, String sha256, List<Integer> pages, IntConsumer progress) throws IOException {
        List<Integer> toRead = pages.stream().limit(maxPages).toList();
        Map<Integer, PageResult> results = new LinkedHashMap<>();
        Map<Integer, byte[]> images = new LinkedHashMap<>();

        // Cached pages first; render the rest (rendering is single-threaded per document).
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(document);
            for (int page : toRead) {
                PageResult cached = cached(sha256, page);
                if (cached != null) {
                    results.put(page, cached);
                    continue;
                }
                BufferedImage image = renderer.renderImageWithDPI(page - 1, dpi, ImageType.RGB);
                images.put(page, jpeg(image));
                image.flush();
            }
        }
        AtomicInteger done = new AtomicInteger(results.size());
        progress.accept(done.get());

        List<PageResult> fresh = java.util.Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(parallel);
        try {
            Map<Integer, Future<PageResult>> futures = new LinkedHashMap<>();
            images.forEach((page, image) -> futures.put(page, pool.submit(() -> {
                PageResult result = readOne(page, image);
                fresh.add(result);
                progress.accept(done.incrementAndGet());
                return result;
            })));
            for (var entry : futures.entrySet()) {
                try {
                    results.put(entry.getKey(), entry.getValue().get(10, TimeUnit.MINUTES));
                } catch (Exception e) {
                    log.warn("Page {} could not be read: {}", entry.getKey(), e.getMessage());
                    results.put(entry.getKey(), new PageResult(entry.getKey(), "", "failed"));
                }
            }
        } finally {
            pool.shutdownNow();
        }
        storeCache(sha256, fresh);

        List<PageResult> ordered = new ArrayList<>();
        for (int page : pages) {
            ordered.add(results.getOrDefault(page, new PageResult(page, "", "skipped")));
        }
        return ordered;
    }

    private PageResult readOne(int page, byte[] image) {
        String text = null;
        String method = "failed";
        // AI first, with up to two waits when every provider is rate limited.
        for (int attempt = 0; attempt < 3 && text == null; attempt++) {
            try {
                text = clean(llmService.generateFromImages(PROMPT, List.of(image)));
                method = "ai";
            } catch (IllegalStateException e) {
                String message = String.valueOf(e.getMessage());
                if (message.contains("rate limit") && attempt < 2) {
                    sleep(20_000L * (attempt + 1));
                    continue;
                }
                log.warn("AI could not read page {}: {}", page, message);
                break;
            }
        }
        if (text == null && tesseractInstalled()) {
            try {
                text = clean(tesseract(image));
                method = "ocr";
            } catch (IOException e) {
                log.warn("Tesseract could not read page {}: {}", page, e.getMessage());
            }
        }
        if (text == null) return new PageResult(page, "", "failed");
        return new PageResult(page, text, method);
    }

    /** Saves freshly read pages so the same file is never sent to the AI twice. */
    private void storeCache(String sha256, List<PageResult> results) {
        for (PageResult r : results) {
            if (!"ai".equals(r.method()) && !"ocr".equals(r.method())) continue;
            jdbc.update("""
                    INSERT INTO pdf_page_text (sha256, page_no, method, text) VALUES (?, ?, ?, ?)
                    ON CONFLICT (sha256, page_no) DO NOTHING
                    """, sha256, r.page(), r.method(), r.text());
        }
    }

    private PageResult cached(String sha256, int page) {
        List<PageResult> rows = jdbc.query(
                "SELECT method, text FROM pdf_page_text WHERE sha256 = ? AND page_no = ?",
                (rs, i) -> new PageResult(page, rs.getString("text"), rs.getString("method")), sha256, page);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Strips code fences / "EMPTY" answers from the model's transcription. */
    static String clean(String text) {
        if (text == null) return "";
        String t = text.trim();
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "");
        }
        return t.trim().equalsIgnoreCase("EMPTY") ? "" : t.trim();
    }

    /** JPEG, longest side at most 2000 px, quality 0.8 — small enough for every provider. */
    static byte[] jpeg(BufferedImage source) throws IOException {
        BufferedImage image = source;
        int longest = Math.max(source.getWidth(), source.getHeight());
        if (longest > 2000) {
            double scale = 2000.0 / longest;
            int w = (int) Math.round(source.getWidth() * scale);
            int h = (int) Math.round(source.getHeight() * scale);
            image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source, 0, 0, w, h, null);
            g.dispose();
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.8f);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------------ optional Tesseract

    boolean tesseractInstalled() {
        if (tesseractAvailable == null) {
            try {
                Process p = new ProcessBuilder(tesseractCommand(), "--version").redirectErrorStream(true).start();
                tesseractAvailable = p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
            } catch (Exception e) {
                tesseractAvailable = false;
            }
            log.info("Tesseract OCR {}", tesseractAvailable ? "found (used only if AI can't read a page)" : "not installed (not needed)");
        }
        return tesseractAvailable;
    }

    private static String tesseractCommand() {
        return System.getenv().getOrDefault("TESSERACT_CMD", "tesseract");
    }

    private static String tesseract(byte[] jpeg) throws IOException {
        Path imageFile = Files.createTempFile("campuspulse-page-", ".jpg");
        try {
            Files.write(imageFile, jpeg);
            List<String> command = new ArrayList<>(List.of(tesseractCommand(), imageFile.toString(), "stdout", "-l", "eng"));
            String tessdata = System.getenv("TESSDATA_PREFIX");
            if (tessdata != null && !tessdata.isBlank()) command.addAll(List.of("--tessdata-dir", tessdata));
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) output.append(line).append('\n');
            }
            if (!process.waitFor(2, TimeUnit.MINUTES) || process.exitValue() != 0) {
                process.destroyForcibly();
                throw new IOException("Tesseract failed");
            }
            return output.toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("OCR interrupted", e);
        } finally {
            Files.deleteIfExists(imageFile);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
