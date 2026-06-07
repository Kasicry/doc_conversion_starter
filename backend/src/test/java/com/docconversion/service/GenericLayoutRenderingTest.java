package com.docconversion.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenericLayoutRenderingTest {

    private static final Path BAEMIN_PDF = Path.of("../cv_doc/배달의민족 회원탈퇴 요청서.pdf");
    private static final Path TELECOM_PDF = Path.of("../cv_doc/통신서비스_이용증명원.pdf");

    @Test
    void rendersBaeminFormAsOnePageWithSimilarVisibleBounds(@TempDir Path tempDirectory) throws Exception {
        assertRenderedLayout(tempDirectory, BAEMIN_PDF, 0.95, 1.05, 0.95, 1.06);
    }

    @Test
    void rendersTelecomCertificateAsOnePageWithSimilarVisibleBounds(@TempDir Path tempDirectory) throws Exception {
        assertRenderedLayout(tempDirectory, TELECOM_PDF, 0.95, 1.05, 0.95, 1.06);
    }

    private void assertRenderedLayout(
        Path tempDirectory,
        Path sourcePdf,
        double minimumWidthRatio,
        double maximumWidthRatio,
        double minimumHeightRatio,
        double maximumHeightRatio
    ) throws Exception {
        Path libreOffice = findLibreOffice();
        assertThat(libreOffice)
            .as("LibreOffice executable required for rendered layout verification")
            .isNotNull();

        Path docx = tempDirectory.resolve("converted.docx");
        Files.write(docx, new PdfToDocxConverter().convert(sourcePdf));

        Path profile = tempDirectory.resolve("libreoffice-profile");
        Process process = new ProcessBuilder(
            libreOffice.toString(),
            "-env:UserInstallation=" + profile.toUri(),
            "--headless",
            "--convert-to",
            "pdf",
            "--outdir",
            tempDirectory.toString(),
            docx.toString()
        ).redirectErrorStream(true).start();

        boolean finished = process.waitFor(Duration.ofSeconds(60).toSeconds(), TimeUnit.SECONDS);
        String processOutput = new String(process.getInputStream().readAllBytes());
        assertThat(finished).as(processOutput).isTrue();
        assertThat(process.exitValue()).as(processOutput).isZero();

        Path renderedPdf = tempDirectory.resolve("converted.pdf");
        assertThat(renderedPdf).exists();
        try (PDDocument source = Loader.loadPDF(sourcePdf.toFile());
             PDDocument rendered = Loader.loadPDF(renderedPdf.toFile())) {
            assertThat(source.getNumberOfPages()).isOne();
            assertThat(rendered.getNumberOfPages()).isOne();

            BufferedImage sourceImage = new PDFRenderer(source).renderImageWithDPI(0, 100);
            BufferedImage renderedImage = new PDFRenderer(rendered).renderImageWithDPI(0, 100);
            InkBounds sourceBounds = inkBounds(sourceImage);
            InkBounds renderedBounds = inkBounds(renderedImage);
            assertThat((double) renderedBounds.width() / sourceBounds.width())
                .as("rendered/source visible width ratio")
                .isBetween(minimumWidthRatio, maximumWidthRatio);
            assertThat((double) renderedBounds.height() / sourceBounds.height())
                .as("rendered/source visible height ratio")
                .isBetween(minimumHeightRatio, maximumHeightRatio);
            assertThat((double) renderedBounds.inkPixels() / sourceBounds.inkPixels())
                .as("rendered/source foreground pixel ratio")
                .isBetween(0.70, 1.30);
            assertThat(Math.abs(renderedBounds.left() - sourceBounds.left()))
                .as("left visible margin delta")
                .isLessThanOrEqualTo(Math.round(sourceImage.getWidth() * 0.03f));
            assertThat(Math.abs(renderedBounds.top() - sourceBounds.top()))
                .as("top visible margin delta")
                .isLessThanOrEqualTo(Math.round(sourceImage.getHeight() * 0.03f));
            assertThat(Math.abs(renderedBounds.right() - sourceBounds.right()))
                .as("right visible margin delta")
                .isLessThanOrEqualTo(Math.round(sourceImage.getWidth() * 0.03f));
            assertThat(Math.abs(renderedBounds.bottom() - sourceBounds.bottom()))
                .as("bottom visible margin delta")
                .isLessThanOrEqualTo(Math.round(sourceImage.getHeight() * 0.03f));
        }
    }

    private Path findLibreOffice() {
        return List.of(
                Path.of("C:/Program Files/LibreOffice/program/soffice.com"),
                Path.of("C:/Program Files/LibreOffice/program/soffice.exe"),
                Path.of("/usr/bin/soffice"),
                Path.of("/usr/bin/libreoffice")
            ).stream()
            .filter(Files::isRegularFile)
            .findFirst()
            .orElse(null);
    }

    private InkBounds inkBounds(BufferedImage image) {
        int left = image.getWidth();
        int top = image.getHeight();
        int right = -1;
        int bottom = -1;
        int inkPixels = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;
                if (red < 245 || green < 245 || blue < 245) {
                    inkPixels++;
                    left = Math.min(left, x);
                    top = Math.min(top, y);
                    right = Math.max(right, x);
                    bottom = Math.max(bottom, y);
                }
            }
        }
        assertThat(right).as("rightmost visible pixel").isGreaterThanOrEqualTo(left);
        assertThat(bottom).as("bottommost visible pixel").isGreaterThanOrEqualTo(top);
        return new InkBounds(left, top, right, bottom, inkPixels);
    }

    private record InkBounds(int left, int top, int right, int bottom, int inkPixels) {

        int width() {
            return right - left + 1;
        }

        int height() {
            return bottom - top + 1;
        }
    }
}
