package com.docconversion.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfToDocxConverterTest {

    private final PdfToDocxConverter converter = new PdfToDocxConverter();

    @Test
    void convertsArbitraryPdfIntoEditableTextWithoutFullPageImage(@TempDir Path tempDirectory) throws Exception {
        Path pdf = tempDirectory.resolve("arbitrary-document.pdf");
        Files.copy(Path.of("../cv_doc/배달의민족 회원탈퇴 요청서.pdf"), pdf);

        byte[] result = converter.convert(pdf);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(result))) {
            String documentXml = document.getDocument().xmlText();
            String documentText = new XWPFWordExtractor(document).getText();
            assertThat(document.getAllPictures()).isEmpty();
            assertThat(documentText).contains("배달의민족 회원탈퇴 요청서");
            assertThat(document.getTables()).hasSize(3);
            assertThat(document.getTables().get(0).getRow(0).getTableCells()).hasSize(5);
            assertThat(document.getTables().get(2).getText())
                .contains("유의사항")
                .contains("회원탈퇴요청 전에 꼭 확인하세요");
            assertThat(documentXml).contains("vMerge");
            assertThat(documentXml).contains("w:shd");
            assertThat(documentXml).doesNotContain("w:type=\"page\"");
            assertThat(documentXml).doesNotContain("w:drawing");
            assertThat(documentXml).doesNotContain("w:line=\"240\"");
        }
    }

    @Test
    void convertsImageOnlyPdfIntoEditableOcrText(@TempDir Path tempDirectory) throws Exception {
        assertThat(new TesseractOcrService().isAvailable()).isTrue();
        Path pdf = tempDirectory.resolve("scanned-document.pdf");
        createImageOnlyPdf(pdf, "SCANNED DOCUMENT 2026", 1);

        byte[] result = converter.convert(pdf);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(result))) {
            String documentXml = document.getDocument().xmlText();
            String documentText = new XWPFWordExtractor(document).getText();
            assertThat(document.getAllPictures()).isEmpty();
            assertThat(documentText).containsIgnoringCase("SCANNED");
            assertThat(documentXml).doesNotContain("w:drawing");
        }
    }

    @Test
    void preservesMultipleScannedPages(@TempDir Path tempDirectory) throws Exception {
        Path pdf = tempDirectory.resolve("multiple-scanned-pages.pdf");
        createImageOnlyPdf(pdf, "MULTIPLE SCANNED PAGE", 2);

        byte[] result = converter.convert(pdf);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(result))) {
            String documentXml = document.getDocument().xmlText();
            String documentText = new XWPFWordExtractor(document).getText();
            assertThat(document.getAllPictures()).isEmpty();
            assertThat(documentText).containsIgnoringCase("MULTIPLE SCANNED PAGE");
            assertThat(documentXml).doesNotContain("w:drawing");
        }
    }

    private void createImageOnlyPdf(Path path, String text, int pageCount) throws Exception {
        BufferedImage image = new BufferedImage(1200, 1600, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font("Arial", Font.BOLD, 54));
        graphics.drawString(text, 120, 260);
        graphics.dispose();

        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream imageOutput = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", imageOutput);
            PDImageXObject pdfImage = PDImageXObject.createFromByteArray(pdf, imageOutput.toByteArray(), "scan");
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                PDPage page = new PDPage();
                pdf.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                    content.drawImage(pdfImage, 0, 0, page.getMediaBox().getWidth(), page.getMediaBox().getHeight());
                }
            }
            pdf.save(path.toFile());
        }
    }
}
