import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;

public class DocTextExtractor {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: DocTextExtractor <input.doc> <output.txt>");
        }
        try (FileInputStream in = new FileInputStream(args[0]);
             HWPFDocument doc = new HWPFDocument(in);
             WordExtractor extractor = new WordExtractor(doc)) {
            Files.writeString(Path.of(args[1]), extractor.getText(), StandardCharsets.UTF_8);
        }
    }
}
