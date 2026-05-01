package com.example.exam.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

    public TextExtractionService(@Value("${app.ocr.tesseract-command:tesseract}") String tesseractCommand) {
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
            return "当前文件类型暂不支持自动抽取。请在这里粘贴或编辑正文，然后保存为知识库。";
        } catch (Exception ex) {
            return "自动抽取失败：" + ex.getMessage() + "\n请手动编辑正文后，再保存为知识库。";
        }
    }

    public boolean isExtractionPlaceholder(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("automatic extraction failed:")
                || lower.contains("cannot run tesseract")
                || lower.contains("ocr did not finish")
                || lower.contains("this file type is not supported for automatic extraction yet")
                || text.contains("自动抽取失败：")
                || text.contains("OCR 没有完成")
                || text.contains("当前文件类型暂不支持自动抽取")
                || text.contains("请手动编辑正文后，再保存为知识库")
                || text.contains("请在这里粘贴或编辑正文，然后保存为知识库");
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
        String command = resolveTesseractCommand();
        Process process;
        try {
            process = new ProcessBuilder(command, path.toString(), outputBase.toString(), "-l", "chi_sim+eng")
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException ex) {
            throw new IOException("Cannot run Tesseract command \"" + command
                    + "\". If you installed it recently, restart the backend so it gets the updated PATH, "
                    + "or set app.ocr.tesseract-command/TESSERACT_CMD to the full tesseract.exe path.", ex);
        }
        int code = process.waitFor();
        String logs = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        Path textFile = Path.of(outputBase + ".txt");
        if (code == 0 && Files.exists(textFile)) {
            String text = Files.readString(textFile, StandardCharsets.UTF_8);
            Files.deleteIfExists(textFile);
            return text;
        }
        return "OCR 没有完成。请确认已安装 Tesseract 和 chi_sim 中文语言包。\n输出信息：\n" + logs;
    }

    private String resolveTesseractCommand() throws IOException {
        String configured = normalizeCommand(tesseractCommand);
        if (!configured.isBlank() && !isDefaultCommand(configured)) {
            return configured;
        }

        String envCommand = normalizeCommand(System.getenv("TESSERACT_CMD"));
        if (!envCommand.isBlank()) {
            return envCommand;
        }

        for (String candidate : windowsTesseractCandidates()) {
            if (Files.isRegularFile(Path.of(candidate))) {
                return candidate;
            }
        }

        return configured.isBlank() ? "tesseract" : configured;
    }

    private String normalizeCommand(String command) {
        if (command == null) {
            return "";
        }
        String trimmed = command.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private boolean isDefaultCommand(String command) {
        return "tesseract".equalsIgnoreCase(command) || "tesseract.exe".equalsIgnoreCase(command);
    }

    private List<String> windowsTesseractCandidates() {
        List<String> candidates = new ArrayList<>();
        addProgramFilesCandidate(candidates, "ProgramFiles");
        addProgramFilesCandidate(candidates, "ProgramFiles(x86)");
        addProgramFilesCandidate(candidates, "LOCALAPPDATA");
        return candidates;
    }

    private void addProgramFilesCandidate(List<String> candidates, String envName) {
        String base = System.getenv(envName);
        if (base == null || base.isBlank()) {
            return;
        }
        candidates.add(Path.of(base, "Tesseract-OCR", "tesseract.exe").toString());
        candidates.add(Path.of(base, "Programs", "Tesseract-OCR", "tesseract.exe").toString());
    }
}
