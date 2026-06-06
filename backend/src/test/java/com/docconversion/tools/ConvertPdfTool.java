package com.docconversion.tools;

import com.docconversion.service.PdfToDocxConverter;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConvertPdfTool {

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: ConvertPdfTool <input-pdf> <output-docx>");
        }

        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.write(output, new PdfToDocxConverter().convert(input));
        System.out.println("converted=" + output.toAbsolutePath());
    }
}
