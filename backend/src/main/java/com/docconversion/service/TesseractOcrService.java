package com.docconversion.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

@Component
public class TesseractOcrService {

    private static final Duration OCR_TIMEOUT = Duration.ofMinutes(2);
    private static final float OCR_DPI = 200.0f;
    private static final float POINTS_PER_INCH = 72.0f;
    private static final int MIN_CONFIDENCE = 25;

    private final Path executable;
    private final Path tessdata;

    public TesseractOcrService() {
        this.executable = resolveExecutable();
        this.tessdata = resolveTessdata();
    }

    public boolean isAvailable() {
        return executable != null && tessdata != null
            && Files.isRegularFile(tessdata.resolve("eng.traineddata"))
            && Files.isRegularFile(tessdata.resolve("kor.traineddata"));
    }

    public float renderDpi() {
        return OCR_DPI;
    }

    public List<DocumentTextLine> recognize(BufferedImage image) throws IOException {
        if (!isAvailable()) {
            return List.of();
        }

        Path tempImage = Files.createTempFile("doc-conversion-ocr-", ".png");
        try {
            ImageIO.write(image, "png", tempImage.toFile());
            Process process = new ProcessBuilder(
                executable.toString(),
                tempImage.toString(),
                "stdout",
                "--tessdata-dir",
                tessdata.toString(),
                "-l",
                "kor+eng",
                "--psm",
                "6",
                "-c",
                "tessedit_create_tsv=1"
            ).redirectErrorStream(true).start();

            byte[] output = process.getInputStream().readAllBytes();
            boolean completed;
            try {
                completed = process.waitFor(OCR_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("OCR 처리가 중단되었습니다.", exception);
            }
            if (!completed) {
                process.destroyForcibly();
                throw new IOException("OCR 처리 시간이 제한을 초과했습니다.");
            }
            if (process.exitValue() != 0) {
                throw new IOException("OCR 처리에 실패했습니다: " + new String(output, StandardCharsets.UTF_8).trim());
            }
            return parseTsv(new String(output, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(tempImage);
        }
    }

    private List<DocumentTextLine> parseTsv(String tsv) {
        Map<String, List<OcrWord>> wordsByLine = new LinkedHashMap<>();
        for (String row : tsv.split("\\R")) {
            String[] columns = row.split("\\t", 12);
            if (columns.length < 12 || !"5".equals(columns[0]) || columns[11].isBlank()) {
                continue;
            }
            float confidence = parseFloat(columns[10], -1);
            if (confidence < MIN_CONFIDENCE) {
                continue;
            }
            String lineKey = columns[2] + ":" + columns[3] + ":" + columns[4];
            wordsByLine.computeIfAbsent(lineKey, ignored -> new ArrayList<>()).add(new OcrWord(
                columns[11].trim(),
                parseInt(columns[6], 0),
                parseInt(columns[7], 0),
                parseInt(columns[8], 0),
                parseInt(columns[9], 0)
            ));
        }

        float pointScale = POINTS_PER_INCH / OCR_DPI;
        return wordsByLine.values().stream()
            .map(words -> toTextLine(words, pointScale))
            .filter(line -> !line.text().isBlank())
            .toList();
    }

    private DocumentTextLine toTextLine(List<OcrWord> words, float scale) {
        words.sort(java.util.Comparator.comparingInt(OcrWord::left));
        int left = words.stream().mapToInt(OcrWord::left).min().orElse(0);
        int top = words.stream().mapToInt(OcrWord::top).min().orElse(0);
        int right = words.stream().mapToInt(word -> word.left() + word.width()).max().orElse(left);
        int bottom = words.stream().mapToInt(word -> word.top() + word.height()).max().orElse(top);
        StringBuilder text = new StringBuilder();
        OcrWord previous = null;
        for (OcrWord word : words) {
            if (previous != null) {
                int gap = word.left() - (previous.left() + previous.width());
                int spacingThreshold = Math.max(4, Math.round(Math.min(previous.height(), word.height()) * 0.45f));
                if (gap > spacingThreshold) {
                    text.append(' ');
                }
            }
            text.append(word.text());
            previous = word;
        }
        float height = Math.max(8.0f, (bottom - top) * scale);
        return new DocumentTextLine(
            text.toString(),
            left * scale,
            bottom * scale,
            Math.max(12.0f, (right - left) * scale),
            height,
            Math.max(6.0f, height * 0.8f)
        );
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private float parseFloat(String value, float fallback) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private Path resolveExecutable() {
        String configured = System.getenv("TESSERACT_COMMAND");
        if (configured != null && Files.isRegularFile(Path.of(configured))) {
            return Path.of(configured);
        }
        Path windowsDefault = Path.of("C:/Program Files/Tesseract-OCR/tesseract.exe");
        if (Files.isRegularFile(windowsDefault)) {
            return windowsDefault;
        }
        return findOnPath("tesseract");
    }

    private Path resolveTessdata() {
        String configured = System.getenv("TESSDATA_PREFIX");
        if (configured != null && Files.isDirectory(Path.of(configured))) {
            return Path.of(configured);
        }
        for (Path candidate : List.of(
            Path.of("ocr/tessdata"),
            Path.of("backend/ocr/tessdata"),
            Path.of("C:/Program Files/Tesseract-OCR/tessdata")
        )) {
            if (Files.isDirectory(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        return null;
    }

    private Path findOnPath(String command) {
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        for (String directory : path.split(java.io.File.pathSeparator)) {
            Path candidate = Path.of(directory, command);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private record OcrWord(String text, int left, int top, int width, int height) {
    }
}
