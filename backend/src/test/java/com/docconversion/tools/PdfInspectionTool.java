package com.docconversion.tools;

import com.docconversion.service.PdfToDocxConverter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

public class PdfInspectionTool {

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: PdfInspectionTool <pdf-path> <output-directory>");
        }

        Path pdfPath = Path.of(args[0]);
        Path outputDirectory = Path.of(args[1]);
        Files.createDirectories(outputDirectory);

        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            String text = new PDFTextStripper().getText(document).trim();
            System.out.println("pages=" + document.getNumberOfPages());
            System.out.println("extractedTextLength=" + text.length());
            if (!text.isBlank()) {
                System.out.println("extractedTextPreview=" + text.substring(0, Math.min(text.length(), 500)).replace('\n', ' '));
            }

            PDFRenderer renderer = new PDFRenderer(document);
            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                Path imagePath = outputDirectory.resolve("baemin_page_" + (pageIndex + 1) + ".png");
                ImageIO.write(renderer.renderImageWithDPI(pageIndex, 160), "png", imagePath.toFile());
                System.out.println("rendered=" + imagePath.toAbsolutePath());
            }
        }

        Path docxPath = outputDirectory.resolve("baemin_converted.docx");
        Files.write(docxPath, new PdfToDocxConverter().convert(pdfPath));
        System.out.println("converted=" + docxPath.toAbsolutePath());
    }
}
