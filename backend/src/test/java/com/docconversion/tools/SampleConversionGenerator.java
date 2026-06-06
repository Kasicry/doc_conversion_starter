package com.docconversion.tools;

import com.docconversion.service.PdfToDocxConverter;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

public class SampleConversionGenerator {

    public static void main(String[] args) throws IOException {
        Path outputDirectory = args.length > 0 ? Path.of(args[0]) : Path.of("cv_doc");
        Files.createDirectories(outputDirectory);

        Path pdfPath = outputDirectory.resolve("sample_document.pdf");
        Path docxPath = outputDirectory.resolve("sample_document.docx");

        createSamplePdf(pdfPath);
        byte[] docx = new PdfToDocxConverter().convert(pdfPath);
        Files.write(docxPath, docx);

        System.out.println("PDF: " + pdfPath.toAbsolutePath());
        System.out.println("DOCX: " + docxPath.toAbsolutePath());
    }

    private static void createSamplePdf(Path pdfPath) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                PDType1Font titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font bodyFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

                content.beginText();
                content.setFont(titleFont, 18);
                content.newLineAtOffset(72, 720);
                content.showText("Sample Document Conversion");
                content.endText();

                String[] lines = {
                    "Created: " + LocalDate.now(),
                    "Purpose: Verify PDF to editable DOCX conversion.",
                    "",
                    "Section 1. Overview",
                    "This PDF contains extractable text generated for conversion testing.",
                    "The backend uses Apache PDFBox to read text and Apache POI to create DOCX.",
                    "",
                    "Section 2. Sample Items",
                    "- Upload a PDF file.",
                    "- Create a conversion job.",
                    "- Download the converted DOCX file.",
                };

                float y = 680;
                for (String line : lines) {
                    content.beginText();
                    content.setFont(bodyFont, 11);
                    content.newLineAtOffset(72, y);
                    content.showText(line);
                    content.endText();
                    y -= 18;
                }

                drawTable(content, bodyFont, 72, 430);
                drawImage(document, content, 360, 400);

                content.beginText();
                content.setFont(bodyFont, 11);
                content.newLineAtOffset(72, 300);
                content.showText("Section 3. Expected Result");
                content.endText();

                content.beginText();
                content.setFont(bodyFont, 11);
                content.newLineAtOffset(72, 282);
                content.showText("The DOCX output should include editable text, a detected table, and a page preview image.");
                content.endText();
            }

            document.save(pdfPath.toFile());
        }
    }

    private static void drawTable(PDPageContentStream content, PDType1Font bodyFont, float startX, float startY) throws IOException {
        String[][] rows = {
            {"Item", "Owner", "Status"},
            {"Upload", "User", "Ready"},
            {"Convert", "System", "Done"},
            {"Download", "User", "Ready"}
        };
        float[] widths = {120, 120, 120};
        float rowHeight = 24;

        float tableWidth = widths[0] + widths[1] + widths[2];
        for (int row = 0; row <= rows.length; row++) {
            float y = startY - (row * rowHeight);
            content.moveTo(startX, y);
            content.lineTo(startX + tableWidth, y);
        }

        float x = startX;
        for (float width : widths) {
            content.moveTo(x, startY);
            content.lineTo(x, startY - (rows.length * rowHeight));
            x += width;
        }
        content.moveTo(startX + tableWidth, startY);
        content.lineTo(startX + tableWidth, startY - (rows.length * rowHeight));
        content.stroke();

        for (int row = 0; row < rows.length; row++) {
            float textY = startY - 17 - (row * rowHeight);
            float textX = startX + 8;
            for (int column = 0; column < rows[row].length; column++) {
                content.beginText();
                content.setFont(bodyFont, 10);
                content.newLineAtOffset(textX, textY);
                content.showText(rows[row][column]);
                content.endText();
                textX += widths[column];
            }
        }
    }

    private static void drawImage(PDDocument document, PDPageContentStream content, float x, float y) throws IOException {
        BufferedImage image = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(35, 76, 95));
        graphics.fillRect(0, 0, 120, 80);
        graphics.setColor(new Color(238, 245, 243));
        graphics.fillOval(18, 14, 84, 52);
        graphics.setColor(Color.WHITE);
        graphics.drawString("PDF", 48, 45);
        graphics.dispose();

        try (ByteArrayOutputStream imageOutput = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", imageOutput);
            PDImageXObject pdfImage = PDImageXObject.createFromByteArray(document, imageOutput.toByteArray(), "sample-image");
            content.drawImage(pdfImage, x, y, 120, 80);
        }
    }
}
