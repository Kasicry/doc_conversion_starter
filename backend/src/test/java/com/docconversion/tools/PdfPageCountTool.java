package com.docconversion.tools;

import java.io.IOException;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

public class PdfPageCountTool {

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: PdfPageCountTool <pdf-path>");
        }

        Path pdfPath = Path.of(args[0]);
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            System.out.println("pages=" + document.getNumberOfPages());
        }
    }
}
