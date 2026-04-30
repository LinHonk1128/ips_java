package com.example.exam.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TextExtractionService {
    private final String tesseractCommand;

    public TextExtractionService(@Value("${app.ocr.tesseract-command}") String tesseractCommand) {
        this.tesseractCommand = tesseractCommand;
    }

    public String extract(Path path, String originalName, String contentType) {
        String lower = originalName.toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith(".pdf")) {
                return extractPdf(path);
            }
            if (lower.endsWith(".docx")) {
                return extractDocx(path);
            }
            if (lower.endsWith(".doc")) {
                return extractDoc(path);
            }
            if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".csv")) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
            if (contentType != null && contentType.startsWith("image/")) {
                return extractImageWithOcr(path);
            }
            return "This file type is not supported for automatic extraction yet. Paste or edit text here, then save it to the knowledge base.";
        } catch (Exception ex) {
            return "Automatic extraction failed: " + ex.getMessage() + "\nEdit the text manually, then save it to the knowledge base.";
        }
    }

    private String extractPdf(Path path) throws IOException {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String extractDocx(Path path) throws IOException {
        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(path));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String extractDoc(Path path) throws IOException {
        try (HWPFDocument document = new HWPFDocument(Files.newInputStream(path));
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String extractImageWithOcr(Path path) throws IOException, InterruptedException {
        Path outputBase = Files.createTempFile("smart-exam-ocr", "");
        Files.deleteIfExists(outputBase);
        Process process = new ProcessBuilder(tesseractCommand, path.toString(), outputBase.toString(), "-l", "chi_sim+eng")
                .redirectErrorStream(true)
                .start();
        int code = process.waitFor();
        String logs = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        Path textFile = Path.of(outputBase + ".txt");
        if (code == 0 && Files.exists(textFile)) {
            String text = Files.readString(textFile, StandardCharsets.UTF_8);
            Files.deleteIfExists(textFile);
            return text;
        }
        return "OCR did not finish. Make sure Tesseract and the chi_sim language pack are installed. Output:\n" + logs;
    }
}
