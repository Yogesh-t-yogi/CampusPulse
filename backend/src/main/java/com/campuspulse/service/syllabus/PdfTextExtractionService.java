package com.campuspulse.service.syllabus;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.text.PDFTextStripper;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class PdfTextExtractionService {

    public String extractText(MultipartFile file) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "PDF file is empty"
            );
        }

        String fileName = file.getOriginalFilename();

        if (fileName == null ||
                !fileName.toLowerCase().endsWith(".pdf")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only PDF files are allowed"
            );
        }

        try (PDDocument document = Loader.loadPDF(file.getBytes())) {

            if (document.getNumberOfPages() == 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "The PDF contains no pages"
                );
            }

            // First, extract selectable PDF text.
            PDFTextStripper stripper = new PDFTextStripper();
            String extractedText = stripper.getText(document);

            if (extractedText != null &&
                    !extractedText.isBlank()) {

                System.out.println(
                        "PDF text extraction completed. Characters: "
                                + extractedText.length()
                );

                return extractedText.trim();
            }

            // No selectable text: use the installed Tesseract CLI.
            String tessdataPath = System.getenv().getOrDefault(
                    "TESSDATA_PREFIX",
                    "C:/Program Files/Tesseract-OCR/tessdata"
            );

            String tesseractCommand = System.getenv().getOrDefault(
                    "TESSERACT_CMD",
                    "tesseract"
            );

            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder result = new StringBuilder();

            for (int page = 0;
                    page < document.getNumberOfPages();
                    page++) {

                Path imageFile = Files.createTempFile(
                        "campuspulse-page-", ".png"
                );

                try {
                    BufferedImage image = renderer.renderImageWithDPI(
                            page, 300, ImageType.RGB
                    );

                    try {
                        if (!javax.imageio.ImageIO.write(
                                image, "png", imageFile.toFile())) {
                            throw new IOException(
                                    "Could not create OCR image for page "
                                            + (page + 1)
                            );
                        }
                    } finally {
                        image.flush();
                    }

                    ProcessBuilder processBuilder = new ProcessBuilder(
                            tesseractCommand,
                            imageFile.toAbsolutePath().toString(),
                            "stdout",
                            "-l", "eng",
                            "--tessdata-dir", tessdataPath
                    );

                    processBuilder.redirectErrorStream(true);

                    Process process = processBuilder.start();

                    StringBuilder output = new StringBuilder();

                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(
                                    process.getInputStream(),
                                    StandardCharsets.UTF_8))) {

                        String line;

                        while ((line = reader.readLine()) != null) {
                            output.append(line)
                                    .append(System.lineSeparator());
                        }
                    }

                    int exitCode;

                    try {
                        exitCode = process.waitFor();
                    } catch (InterruptedException e) {
                        process.destroyForcibly();
                        Thread.currentThread().interrupt();

                        throw new IOException(
                                "OCR was interrupted on page "
                                        + (page + 1),
                                e
                        );
                    }

                    if (exitCode != 0) {
                        throw new IOException(
                                "Tesseract failed on page "
                                        + (page + 1)
                                        + ": " + output
                        );
                    }

                    result.append(output);

                    System.out.println(
                            "OCR completed for page " + (page + 1)
                    );

                } finally {
                    Files.deleteIfExists(imageFile);
                }
            }

            String ocrText = result.toString().trim();

            if (ocrText.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "OCR could not recognize text in this PDF."
                );
            }

            System.out.println(
                    "OCR completed. Extracted characters: "
                            + ocrText.length()
            );

            return ocrText;
        }
    }
}