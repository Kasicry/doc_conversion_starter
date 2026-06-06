package com.docconversion.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PdfToDocxConverter {

    private static final float ROW_TOLERANCE_POINTS = 3.0f;
    private static final String KOREAN_FONT = "Malgun Gothic";
    private final TesseractOcrService ocrService;

    @Autowired
    public PdfToDocxConverter(TesseractOcrService ocrService) {
        this.ocrService = ocrService;
    }

    public PdfToDocxConverter() {
        this(new TesseractOcrService());
    }

    public byte[] convert(Path pdfPath) {
        try (PDDocument pdf = Loader.loadPDF(pdfPath.toFile());
             XWPFDocument docx = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            if (pdf.getNumberOfPages() == 0) {
                throw new IllegalArgumentException("PDF에 변환할 페이지가 없습니다.");
            }

            PositionedTextExtractor extractor = new PositionedTextExtractor();
            Map<Integer, List<TextPosition>> textByPage = extractor.extract(pdf);
            PDFRenderer renderer = new PDFRenderer(pdf);

            configureDocument(docx, pdf.getPage(0));
            for (int pageIndex = 0; pageIndex < pdf.getNumberOfPages(); pageIndex++) {
                if (pageIndex > 0) {
                    XWPFParagraph pageBreak = docx.createParagraph();
                    pageBreak.setPageBreak(true);
                }

                PDPage page = pdf.getPage(pageIndex);
                List<DocumentTextLine> lines = groupIntoLines(textByPage.getOrDefault(pageIndex + 1, List.of()));
                if (lines.isEmpty() && ocrService.isAvailable()) {
                    BufferedImage ocrImage = renderer.renderImageWithDPI(pageIndex, ocrService.renderDpi());
                    lines = ocrService.recognize(ocrImage);
                }
                writeEditablePage(docx, lines);
            }

            docx.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("PDF를 DOCX로 변환하지 못했습니다.", exception);
        }
    }

    private void configureDocument(XWPFDocument document, PDPage firstPage) {
        var section = document.getDocument().getBody().isSetSectPr()
            ? document.getDocument().getBody().getSectPr()
            : document.getDocument().getBody().addNewSectPr();
        var pageSize = section.isSetPgSz() ? section.getPgSz() : section.addNewPgSz();
        pageSize.setW(pointsToTwips(firstPage.getMediaBox().getWidth()));
        pageSize.setH(pointsToTwips(firstPage.getMediaBox().getHeight()));

        var margins = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
        margins.setTop(BigInteger.ZERO);
        margins.setBottom(BigInteger.ZERO);
        margins.setLeft(BigInteger.ZERO);
        margins.setRight(BigInteger.ZERO);
        margins.setHeader(BigInteger.ZERO);
        margins.setFooter(BigInteger.ZERO);
    }

    private void writeEditablePage(XWPFDocument document, List<DocumentTextLine> lines) {
        float previousBaseline = 0;
        float previousHeight = 0;
        for (DocumentTextLine line : lines) {
            float effectiveFontSize = Math.max(6.0f, Math.min(line.fontSize(), line.height() * 1.15f));
            float verticalGap = previousBaseline == 0
                ? Math.max(0, line.y() - line.height())
                : Math.max(0, line.y() - previousBaseline - previousHeight);

            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setIndentationLeft(Math.max(0, Math.round(line.x() * 20)));
            paragraph.setSpacingBefore(Math.max(0, Math.round(verticalGap * 20)));
            paragraph.setSpacingAfter(0);

            XWPFRun run = paragraph.createRun();
            run.setFontFamily(KOREAN_FONT);
            run.setFontSize(Math.max(6, Math.round(effectiveFontSize)));
            run.setText(line.text());

            previousBaseline = line.y();
            previousHeight = Math.max(line.height(), effectiveFontSize);
        }
    }

    private List<DocumentTextLine> groupIntoLines(List<TextPosition> positions) {
        List<TextPosition> sorted = positions.stream()
            .filter(position -> !position.getUnicode().isBlank())
            .sorted(Comparator.comparing(TextPosition::getYDirAdj).thenComparing(TextPosition::getXDirAdj))
            .toList();

        List<List<TextPosition>> groups = new ArrayList<>();
        for (TextPosition position : sorted) {
            List<TextPosition> matchingLine = groups.stream()
                .filter(line -> Math.abs(line.get(0).getYDirAdj() - position.getYDirAdj()) <= ROW_TOLERANCE_POINTS)
                .findFirst()
                .orElseGet(() -> {
                    List<TextPosition> line = new ArrayList<>();
                    groups.add(line);
                    return line;
                });
            matchingLine.add(position);
        }

        return groups.stream()
            .map(this::toTextLine)
            .filter(line -> !line.text().isBlank())
            .toList();
    }

    private DocumentTextLine toTextLine(List<TextPosition> positions) {
        List<TextPosition> sorted = positions.stream()
            .sorted(Comparator.comparing(TextPosition::getXDirAdj))
            .toList();
        StringBuilder text = new StringBuilder();
        TextPosition previous = null;
        for (TextPosition position : sorted) {
            if (previous != null) {
                float gap = position.getXDirAdj() - (previous.getXDirAdj() + previous.getWidthDirAdj());
                if (gap > Math.max(previous.getWidthOfSpace() / 2, 1.5f)) {
                    text.append(' ');
                }
            }
            text.append(position.getUnicode());
            previous = position;
        }

        float x = sorted.stream().map(TextPosition::getXDirAdj).min(Float::compare).orElse(0f);
        float y = sorted.stream().map(TextPosition::getYDirAdj).min(Float::compare).orElse(0f);
        float right = sorted.stream()
            .map(position -> position.getXDirAdj() + position.getWidthDirAdj())
            .max(Float::compare)
            .orElse(x);
        float height = sorted.stream().map(TextPosition::getHeightDir).max(Float::compare).orElse(10f);
        float fontSize = sorted.stream().map(TextPosition::getFontSizeInPt).max(Float::compare).orElse(10f);
        return new DocumentTextLine(text.toString(), x, y, right - x, height, fontSize);
    }

    private BigInteger pointsToTwips(float points) {
        return BigInteger.valueOf(Math.round(points * 20));
    }

    private static class PositionedTextExtractor extends PDFTextStripper {

        private final Map<Integer, List<TextPosition>> positionsByPage = new HashMap<>();

        PositionedTextExtractor() throws IOException {
            setSortByPosition(true);
        }

        Map<Integer, List<TextPosition>> extract(PDDocument document) throws IOException {
            writeText(document, new java.io.StringWriter());
            return positionsByPage;
        }

        @Override
        protected void processTextPosition(TextPosition text) {
            positionsByPage.computeIfAbsent(getCurrentPageNo(), ignored -> new ArrayList<>()).add(text);
        }
    }
}
