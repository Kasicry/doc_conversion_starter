package com.docconversion.tools;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;

public class CreateScannedPdfTool {

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: CreateScannedPdfTool <input-pdf> <output-pdf>");
        }

        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        Files.createDirectories(output.toAbsolutePath().getParent());

        try (PDDocument source = Loader.loadPDF(input.toFile()); PDDocument scanned = new PDDocument()) {
            PDFRenderer renderer = new PDFRenderer(source);
            for (int pageIndex = 0; pageIndex < source.getNumberOfPages(); pageIndex++) {
                PDPage sourcePage = source.getPage(pageIndex);
                PDPage scannedPage = new PDPage(sourcePage.getMediaBox());
                scanned.addPage(scannedPage);
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, 200);
                try (ByteArrayOutputStream imageOutput = new ByteArrayOutputStream()) {
                    ImageIO.write(image, "png", imageOutput);
                    PDImageXObject pageImage = PDImageXObject.createFromByteArray(
                        scanned,
                        imageOutput.toByteArray(),
                        "scanned-page-" + (pageIndex + 1)
                    );
                    try (PDPageContentStream content = new PDPageContentStream(scanned, scannedPage)) {
                        content.drawImage(
                            pageImage,
                            0,
                            0,
                            scannedPage.getMediaBox().getWidth(),
                            scannedPage.getMediaBox().getHeight()
                        );
                    }
                }
            }
            scanned.save(output.toFile());
        }
        System.out.println("scanned=" + output.toAbsolutePath());
    }
}
