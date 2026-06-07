package com.docconversion.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MicrosoftWordPageCountTest {

    private static final List<Path> SAMPLE_PDFS = List.of(
        Path.of("../cv_doc/배달의민족 회원탈퇴 요청서.pdf"),
        Path.of("../cv_doc/통신서비스_이용증명원.pdf")
    );

    @Test
    void preservesSourcePageCountInMicrosoftWord(@TempDir Path tempDirectory) throws Exception {
        Path verifier = Path.of("scripts/word-page-count.ps1").toAbsolutePath();
        assertThat(verifier).exists();

        List<Path> sourcePdfs = new ArrayList<>(SAMPLE_PDFS);
        Path generatedPdf = tempDirectory.resolve("previously-unseen-dense-layout.pdf");
        createDenseTwoPagePdf(generatedPdf);
        sourcePdfs.add(generatedPdf);

        for (int index = 0; index < sourcePdfs.size(); index++) {
            Path sourcePdf = sourcePdfs.get(index);
            Path docx = tempDirectory.resolve("converted-" + index + ".docx");
            Files.write(docx, new PdfToDocxConverter().convert(sourcePdf));

            Process process = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                verifier.toString(),
                "-DocxPath",
                docx.toString()
            ).redirectErrorStream(true).start();

            boolean finished = process.waitFor(Duration.ofSeconds(45).toSeconds(), TimeUnit.SECONDS);
            String output = new String(process.getInputStream().readAllBytes()).trim();
            assertThat(finished).as("Word page verification timed out for %s", sourcePdf).isTrue();
            assertThat(process.exitValue()).as(output).isZero();
            try (PDDocument pdf = Loader.loadPDF(sourcePdf.toFile())) {
                assertThat(Integer.parseInt(output))
                    .as("Word page count for %s", sourcePdf)
                    .isEqualTo(pdf.getNumberOfPages());
            }
        }
    }

    private void createDenseTwoPagePdf(Path path) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (int pageIndex = 0; pageIndex < 2; pageIndex++) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    for (int line = 0; line < 44; line++) {
                        content.beginText();
                        content.setFont(font, line == 0 ? 16 : 10);
                        content.newLineAtOffset(54, 760 - (line * 16));
                        content.showText("UNSEEN PAGE " + (pageIndex + 1) + " LINE " + (line + 1));
                        content.endText();
                    }
                }
            }
            document.save(path.toFile());
        }
    }
}
