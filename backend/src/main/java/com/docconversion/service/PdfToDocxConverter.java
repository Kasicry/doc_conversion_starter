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
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.TableRowHeightRule;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STShd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
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
            PdfTableLayoutDetector tableDetector = new PdfTableLayoutDetector();

            configureDocument(docx, pdf.getPage(0));
            for (int pageIndex = 0; pageIndex < pdf.getNumberOfPages(); pageIndex++) {
                if (pageIndex > 0) {
                    XWPFParagraph pageBreak = docx.createParagraph();
                    pageBreak.setPageBreak(true);
                }

                PDPage page = pdf.getPage(pageIndex);
                List<TextPosition> pagePositions = textByPage.getOrDefault(pageIndex + 1, List.of());
                List<DocumentTextLine> lines = groupIntoLines(pagePositions);
                if (lines.isEmpty() && ocrService.isAvailable()) {
                    BufferedImage ocrImage = renderer.renderImageWithDPI(pageIndex, ocrService.renderDpi());
                    lines = ocrService.recognize(ocrImage);
                }
                PdfVectorLayoutExtractor vectorExtractor = new PdfVectorLayoutExtractor(page);
                List<PdfTableLayoutDetector.TableRegion> tables = tableDetector.detect(vectorExtractor.extract());
                List<DocumentTextLine> chunks = pagePositions.isEmpty() ? lines : groupIntoChunks(pagePositions);
                writeEditablePage(docx, lines, chunks, tables, vectorExtractor.fillRegions());
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

    private void writeEditablePage(
        XWPFDocument document,
        List<DocumentTextLine> lines,
        List<DocumentTextLine> chunks,
        List<PdfTableLayoutDetector.TableRegion> tables,
        List<PdfVectorLayoutExtractor.FillRegion> fills
    ) {
        float previousBaseline = 0;
        float previousHeight = 0;
        int lineIndex = 0;
        for (PdfTableLayoutDetector.TableRegion table : tables) {
            while (lineIndex < lines.size() && lines.get(lineIndex).y() < table.top()) {
                DocumentTextLine line = lines.get(lineIndex++);
                if (tables.stream().noneMatch(candidate -> candidate.contains(line))) {
                    writeParagraph(document, line, previousBaseline, previousHeight);
                    previousBaseline = line.y();
                    previousHeight = Math.max(line.height(), line.fontSize());
                }
            }
            writeTable(document, table, chunks.stream().filter(table::contains).toList(), fills);
            previousBaseline = table.bottom();
            previousHeight = 0;
            while (lineIndex < lines.size() && table.contains(lines.get(lineIndex))) {
                lineIndex++;
            }
        }
        while (lineIndex < lines.size()) {
            DocumentTextLine line = lines.get(lineIndex++);
            if (tables.stream().noneMatch(table -> table.contains(line))) {
                writeParagraph(document, line, previousBaseline, previousHeight);
                previousBaseline = line.y();
                previousHeight = Math.max(line.height(), line.fontSize());
            }
        }
    }

    private void writeParagraph(
        XWPFDocument document,
        DocumentTextLine line,
        float previousBaseline,
        float previousHeight
    ) {
        float effectiveFontSize = effectiveFontSize(line);
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
    }

    private void writeTable(
        XWPFDocument document,
        PdfTableLayoutDetector.TableRegion region,
        List<DocumentTextLine> chunks,
        List<PdfVectorLayoutExtractor.FillRegion> fills
    ) {
        XWPFTable table = document.createTable(region.rows(), region.columns());
        configureTable(table, region);
        for (int rowIndex = 0; rowIndex < region.rows(); rowIndex++) {
            XWPFTableRow row = table.getRow(rowIndex);
            row.setHeight(Math.max(180, Math.round((region.ys().get(rowIndex + 1) - region.ys().get(rowIndex)) * 20)));
            row.setHeightRule(TableRowHeightRule.AT_LEAST);
            for (int columnIndex = 0; columnIndex < region.columns(); columnIndex++) {
                XWPFTableCell cell = row.getCell(columnIndex);
                clearCell(cell);
                applyCellShading(cell, region, rowIndex, columnIndex, fills);
                XWPFParagraph paragraph = cell.addParagraph();
                paragraph.setSpacingAfter(0);
                paragraph.setSpacingBefore(0);
                int finalRowIndex = rowIndex;
                int finalColumnIndex = columnIndex;
                List<DocumentTextLine> cellLines = chunks.stream()
                    .filter(line -> region.rowOf(line) == finalRowIndex && region.columnOf(line) == finalColumnIndex)
                    .sorted(Comparator.comparing(DocumentTextLine::y).thenComparing(DocumentTextLine::x))
                    .toList();
                for (int index = 0; index < cellLines.size(); index++) {
                    if (index > 0) {
                        paragraph.createRun().addBreak();
                    }
                    DocumentTextLine line = cellLines.get(index);
                    XWPFRun run = paragraph.createRun();
                    run.setFontFamily(KOREAN_FONT);
                    run.setFontSize(Math.round(effectiveFontSize(line)));
                    run.setText(line.text());
                }
            }
        }
        applyMerges(table, region);
    }

    private void applyCellShading(
        XWPFTableCell cell,
        PdfTableLayoutDetector.TableRegion region,
        int rowIndex,
        int columnIndex,
        List<PdfVectorLayoutExtractor.FillRegion> fills
    ) {
        float centerX = (region.xs().get(columnIndex) + region.xs().get(columnIndex + 1)) / 2;
        float centerY = (region.ys().get(rowIndex) + region.ys().get(rowIndex + 1)) / 2;
        fills.stream()
            .filter(fill -> fill.contains(centerX, centerY))
            .findFirst()
            .ifPresent(fill -> {
                var tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
                var shading = tcPr.isSetShd() ? tcPr.getShd() : tcPr.addNewShd();
                shading.setVal(STShd.CLEAR);
                shading.setFill(fill.hexColor());
            });
    }

    private float effectiveFontSize(DocumentTextLine line) {
        return Math.max(6.0f, Math.min(line.fontSize(), 24.0f));
    }

    private void configureTable(XWPFTable table, PdfTableLayoutDetector.TableRegion region) {
        int width = Math.round((region.right() - region.left()) * 20);
        table.setWidth(width);
        var tablePr = table.getCTTbl().getTblPr();
        var indentation = tablePr.isSetTblInd() ? tablePr.getTblInd() : tablePr.addNewTblInd();
        indentation.setType(STTblWidth.DXA);
        indentation.setW(BigInteger.valueOf(Math.round(region.left() * 20)));
        var layout = tablePr.isSetTblLayout() ? tablePr.getTblLayout() : tablePr.addNewTblLayout();
        layout.setType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType.FIXED);

        for (int columnIndex = 0; columnIndex < region.columns(); columnIndex++) {
            int columnWidth = Math.round((region.xs().get(columnIndex + 1) - region.xs().get(columnIndex)) * 20);
            for (XWPFTableRow row : table.getRows()) {
                XWPFTableCell cell = row.getCell(columnIndex);
                var tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
                var tcW = tcPr.isSetTcW() ? tcPr.getTcW() : tcPr.addNewTcW();
                tcW.setType(STTblWidth.DXA);
                tcW.setW(BigInteger.valueOf(columnWidth));
            }
        }
    }

    private void applyMerges(XWPFTable table, PdfTableLayoutDetector.TableRegion region) {
        for (int row = 0; row < region.rows(); row++) {
            for (int boundary = 1; boundary < region.columns(); boundary++) {
                if (!region.hasVerticalBoundary(boundary, row)) {
                    setHorizontalMerge(table.getRow(row).getCell(boundary - 1), STMerge.RESTART);
                    setHorizontalMerge(table.getRow(row).getCell(boundary), STMerge.CONTINUE);
                }
            }
        }
        for (int column = 0; column < region.columns(); column++) {
            for (int boundary = 1; boundary < region.rows(); boundary++) {
                if (!region.hasHorizontalBoundary(boundary, column)) {
                    setVerticalMerge(table.getRow(boundary - 1).getCell(column), STMerge.RESTART);
                    setVerticalMerge(table.getRow(boundary).getCell(column), STMerge.CONTINUE);
                }
            }
        }
    }

    private void setHorizontalMerge(XWPFTableCell cell, STMerge.Enum value) {
        var tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        var merge = tcPr.isSetHMerge() ? tcPr.getHMerge() : tcPr.addNewHMerge();
        merge.setVal(value);
    }

    private void setVerticalMerge(XWPFTableCell cell, STMerge.Enum value) {
        var tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        var merge = tcPr.isSetVMerge() ? tcPr.getVMerge() : tcPr.addNewVMerge();
        merge.setVal(value);
    }

    private void clearCell(XWPFTableCell cell) {
        while (!cell.getParagraphs().isEmpty()) {
            cell.removeParagraph(0);
        }
    }

    private List<DocumentTextLine> groupIntoLines(List<TextPosition> positions) {
        return groupTextPositions(positions, Float.MAX_VALUE);
    }

    private List<DocumentTextLine> groupIntoChunks(List<TextPosition> positions) {
        return groupTextPositions(positions, 12.0f);
    }

    private List<DocumentTextLine> groupTextPositions(List<TextPosition> positions, float splitGap) {
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

        List<DocumentTextLine> result = new ArrayList<>();
        for (List<TextPosition> group : groups) {
            List<TextPosition> line = group.stream().sorted(Comparator.comparing(TextPosition::getXDirAdj)).toList();
            List<TextPosition> chunk = new ArrayList<>();
            TextPosition previous = null;
            for (TextPosition position : line) {
                if (previous != null) {
                    float gap = position.getXDirAdj() - (previous.getXDirAdj() + previous.getWidthDirAdj());
                    if (gap > splitGap && !chunk.isEmpty()) {
                        result.add(toTextLine(chunk));
                        chunk = new ArrayList<>();
                    }
                }
                chunk.add(position);
                previous = position;
            }
            if (!chunk.isEmpty()) {
                result.add(toTextLine(chunk));
            }
        }
        return result.stream().filter(line -> !line.text().isBlank()).toList();
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
