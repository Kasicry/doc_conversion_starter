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
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.BodyElementType;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfToDocxConverterTest {

    private final PdfToDocxConverter converter = new PdfToDocxConverter();

    @Test
    void productionConverterContainsNoSampleSpecificDocumentRules() throws Exception {
        String productionSource;
        try (var files = Files.walk(Path.of("src/main/java"))) {
            productionSource = files
                .filter(path -> path.toString().endsWith(".java"))
                .map(path -> {
                    try {
                        return Files.readString(path);
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .reduce("", (left, right) -> left + right);
        }
        assertThat(productionSource)
            .doesNotContain("배달의민족")
            .doesNotContain("통신서비스")
            .doesNotContain("Baemin")
            .doesNotContain("Telecom");
    }

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
                .contains("회원탈퇴요청 전에 꼭 확인하세요")
                .contains("배달의민족 개인정보 처리방침");
            assertThat(documentXml).contains("vMerge");
            assertThat(documentXml).contains("D9D9D9");
            assertThat(documentXml).doesNotContain("w:type=\"page\"");
            assertThat(documentXml).doesNotContain("w:drawing");
            assertThat(documentXml).doesNotContain("w:line=\"240\"");
        }
    }

    @Test
    void preservesTelecomCertificateImagesTablesAndSeparatedFooter(@TempDir Path tempDirectory) throws Exception {
        Path pdf = tempDirectory.resolve("telecom-certificate.pdf");
        Files.copy(Path.of("../cv_doc/통신서비스_이용증명원.pdf"), pdf);

        byte[] result = converter.convert(pdf);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(result))) {
            String documentXml = document.getDocument().xmlText();
            String documentText = new XWPFWordExtractor(document).getText();
            assertThat(document.getAllPictures()).hasSize(1);
            assertThat(document.getTables()).hasSize(2);
            assertThat(document.getTables().get(0).getRows()).hasSize(3);
            assertThat(document.getTables().get(0).getRow(0).getTableCells()).hasSize(4);
            assertThat(document.getTables().get(1).getRows()).hasSize(1);
            assertThat(documentText)
                .contains("통신서비스 이용증명원")
                .contains("이용번호", "서비스 구분", "가입/해지일", "현재상태")
                .contains("기타", "모델명", "단말기 일련번호")
                .contains("발급처: 핀다이렉트 홈페이지", "연락처: 1668-5730", "(주)스테이지파이브");
            int firstTable = bodyElementIndex(document, BodyElementType.TABLE, 0);
            int secondTable = bodyElementIndex(document, BodyElementType.TABLE, 1);
            assertThat(secondTable - firstTable).as("paragraph gap between source tables").isEqualTo(2);
            assertThat(documentXml).contains("w:tabs").contains("w:tab");
            assertThat(documentXml).doesNotContain("w:type=\"page\"");
        }
    }

    @Test
    void derivesCellPaddingAlignmentAndFontSizeFromPdfCoordinates(@TempDir Path tempDirectory) throws Exception {
        Path pdf = tempDirectory.resolve("unseen-positioned-table.pdf");
        createPositionedTablePdf(pdf);

        byte[] result = converter.convert(pdf);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(result))) {
            XWPFTable table = document.getTables().get(0);
            var cell = table.getRow(0).getCell(0);
            var margins = cell.getCTTc().getTcPr().getTcMar();
            int leftMargin = Integer.parseInt(margins.getLeft().getW().toString());
            int rightMargin = Integer.parseInt(margins.getRight().getW().toString());
            assertThat(leftMargin).as("PDF-derived left cell padding").isBetween(180, 300);
            assertThat(rightMargin).as("right padding must not consume alignment whitespace").isZero();
            assertThat(cell.getVerticalAlignment()).isEqualTo(org.apache.poi.xwpf.usermodel.XWPFTableCell.XWPFVertAlign.CENTER);
            assertThat(cell.getParagraphs().get(0).getSpacingBefore())
                .as("single-line cells rely on vertical centering")
                .isZero();
            assertThat(cell.getParagraphs().get(0).getRuns().get(0).getFontSizeAsDouble()).isEqualTo(11.5);
        }
    }

    private int bodyElementIndex(XWPFDocument document, BodyElementType type, int occurrence) {
        int found = 0;
        for (int index = 0; index < document.getBodyElements().size(); index++) {
            if (document.getBodyElements().get(index).getElementType() == type) {
                if (found == occurrence) {
                    return index;
                }
                found++;
            }
        }
        return -1;
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

    private void createPositionedTablePdf(Path path) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float left = 72;
                float bottom = 640;
                float cellWidth = 180;
                float cellHeight = 48;
                content.setLineWidth(1);
                content.addRect(left, bottom, cellWidth, cellHeight);
                content.stroke();
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11.5f);
                content.newLineAtOffset(left + 12, bottom + 19);
                content.showText("POSITIONED CELL");
                content.endText();
            }
            document.save(path.toFile());
        }
    }
}
