package com.docconversion.tools;

import com.docconversion.service.PdfVectorLayoutExtractor;
import com.docconversion.service.PdfTableLayoutDetector;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

public class InspectVectorLayoutTool {

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: InspectVectorLayoutTool <input-pdf>");
        }
        try (PDDocument document = Loader.loadPDF(Path.of(args[0]).toFile())) {
            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                var segments = new PdfVectorLayoutExtractor(document.getPage(pageIndex)).extract();
                System.out.println("page=" + (pageIndex + 1) + ", segments=" + segments.size());
                var tables = new PdfTableLayoutDetector().detect(segments);
                for (int tableIndex = 0; tableIndex < tables.size(); tableIndex++) {
                    var table = tables.get(tableIndex);
                    System.out.printf(
                        "table=%d, bounds=[%.1f, %.1f, %.1f, %.1f], rows=%d, columns=%d, xs=%s, ys=%s%n",
                        tableIndex + 1,
                        table.left(),
                        table.top(),
                        table.right(),
                        table.bottom(),
                        table.rows(),
                        table.columns(),
                        table.xs(),
                        table.ys()
                    );
                }
            }
        }
    }
}
