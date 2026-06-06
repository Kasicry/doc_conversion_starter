package com.docconversion.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

public class RenderPdfTool {

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: RenderPdfTool <pdf-path> <output-prefix>");
        }

        Path pdfPath = Path.of(args[0]);
        Path outputPrefix = Path.of(args[1]);
        Path outputDirectory = outputPrefix.toAbsolutePath().getParent();
        if (outputDirectory != null) {
            Files.createDirectories(outputDirectory);
        }

        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                Path imagePath = Path.of(outputPrefix + "_" + (pageIndex + 1) + ".png");
                ImageIO.write(renderer.renderImageWithDPI(pageIndex, 160), "png", imagePath.toFile());
                System.out.println("rendered=" + imagePath.toAbsolutePath());
            }
        }
    }
}
