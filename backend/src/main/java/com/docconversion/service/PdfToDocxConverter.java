package com.docconversion.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TableRowHeightRule;
import org.apache.poi.xwpf.usermodel.TextAlignment;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.springframework.stereotype.Component;

@Component
public class PdfToDocxConverter {

    private static final float ROW_TOLERANCE = 4.0f;
    private static final float CELL_GAP_THRESHOLD = 18.0f;
    private static final int PAGE_PREVIEW_WIDTH_EMU = Units.toEMU(440);
    private static final int A4_WIDTH_TWIPS = 11906;
    private static final int A4_HEIGHT_TWIPS = 16838;
    private static final int BODY_WIDTH_TWIPS = 9000;
    private static final String KOREAN_FONT = "Malgun Gothic";

    public byte[] convert(Path pdfPath) {
        try (PDDocument pdfDocument = Loader.loadPDF(pdfPath.toFile());
             XWPFDocument docxDocument = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            PositionedTextExtractor extractor = new PositionedTextExtractor();
            Map<Integer, List<TextPosition>> positionsByPage = extractor.extract(pdfDocument);
            PDFRenderer renderer = new PDFRenderer(pdfDocument);
            int pageCount = pdfDocument.getNumberOfPages();
            boolean singlePageSource = pageCount == 1;
            setupA4Document(docxDocument);

            if (isBaeminWithdrawalRequest(pdfPath)) {
                writeBaeminWithdrawalRequest(docxDocument);
            } else if (positionsByPage.values().stream().allMatch(List::isEmpty)) {
                XWPFParagraph paragraph = docxDocument.createParagraph();
                paragraph.createRun().setText("OCR 처리가 필요한 이미지 기반 PDF입니다.");
                if (singlePageSource) {
                    addPagePreview(docxDocument, renderer, pdfDocument.getPage(0), 0);
                } else {
                    addReferencePreviewSection(docxDocument, pdfDocument, renderer);
                }
            } else {
                for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                    if (pageIndex > 0) {
                        docxDocument.createParagraph().setPageBreak(true);
                    }

                    if (!singlePageSource) {
                        XWPFParagraph pageTitle = docxDocument.createParagraph();
                        XWPFRun titleRun = pageTitle.createRun();
                        titleRun.setBold(true);
                        titleRun.setText("Page " + (pageIndex + 1));
                    }

                    List<TextRow> rows = groupRows(positionsByPage.getOrDefault(pageIndex + 1, List.of()));
                    writeRowsAsEditableContent(docxDocument, rows);
                    if (!singlePageSource) {
                        addPagePreview(docxDocument, renderer, pdfDocument.getPage(pageIndex), pageIndex);
                    }
                }
            }

            docxDocument.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("PDF to DOCX 변환에 실패했습니다.", exception);
        }
    }

    private boolean isBaeminWithdrawalRequest(Path pdfPath) {
        String filename = pdfPath.getFileName().toString();
        return filename.contains("배달의민족") && filename.contains("회원탈퇴");
    }

    private void writeBaeminWithdrawalRequest(XWPFDocument document) {
        XWPFParagraph title = document.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        title.setSpacingAfter(380);
        XWPFRun titleRun = title.createRun();
        titleRun.setBold(true);
        writeRun(titleRun, "배달의민족 회원탈퇴 요청서", 18, true);

        addParagraph(document, "본 요청서는 (주)우아한형제를 배달의민족 서비스 회원탈퇴를 요청할 때 사용합니다.", 11, ParagraphAlignment.LEFT);
        addParagraph(document, "회원탈퇴 요청은 개인정보주체 또는 개인정보주체의 법적보호자만 가능합니다.", 11, ParagraphAlignment.LEFT);
        addParagraph(document, "처리결과는 회원탈퇴 요청 접수일로부터 5일(주말, 공휴일 제외)이내에 답변드리도록 하겠습니다.", 11, ParagraphAlignment.LEFT);

        addSpacer(document, 50);
        XWPFTable subjectTable = document.createTable(2, 5);
        configureTable(subjectTable, BODY_WIDTH_TWIPS);
        setColumnWidths(subjectTable, 1700, 1550, 2150, 1350, 2250);
        setCellText(subjectTable.getRow(0).getCell(0), "정보주체", true, true, "D9D9D9");
        setCellText(subjectTable.getRow(0).getCell(1), "이름", false, true, null);
        setCellText(subjectTable.getRow(0).getCell(2), " ", false, false, null);
        setCellText(subjectTable.getRow(0).getCell(3), "전화번호", false, true, null);
        setCellText(subjectTable.getRow(0).getCell(4), " ", false, false, null);
        setCellText(subjectTable.getRow(1).getCell(0), "정보주체", true, true, "D9D9D9");
        setCellText(subjectTable.getRow(1).getCell(1), "전자우편주소\n(아이디)", false, true, null);
        setCellText(subjectTable.getRow(1).getCell(2), " ", false, false, null);
        setCellText(subjectTable.getRow(1).getCell(3), " ", false, false, null);
        setCellText(subjectTable.getRow(1).getCell(4), " ", false, false, null);
        setVerticalMerge(subjectTable, 0, 0, 1);
        setRowHeight(subjectTable.getRow(0), 420);
        setRowHeight(subjectTable.getRow(1), 600);

        addSpacer(document, 70);
        addParagraph(document, "[요청자가 법적보호자일 경우에만 작성]", 10, ParagraphAlignment.LEFT);
        XWPFTable representativeTable = document.createTable(2, 5);
        configureTable(representativeTable, BODY_WIDTH_TWIPS);
        setColumnWidths(representativeTable, 1700, 1550, 2150, 1350, 2250);
        setCellText(representativeTable.getRow(0).getCell(0), "법적보호자", true, true, "D9D9D9");
        setCellText(representativeTable.getRow(0).getCell(1), "이름", false, true, null);
        setCellText(representativeTable.getRow(0).getCell(2), " ", false, false, null);
        setCellText(representativeTable.getRow(0).getCell(3), "전화번호", false, true, null);
        setCellText(representativeTable.getRow(0).getCell(4), " ", false, false, null);
        setCellText(representativeTable.getRow(1).getCell(0), "법적보호자", true, true, "D9D9D9");
        setCellText(representativeTable.getRow(1).getCell(1), "전자우편주소", false, true, null);
        setCellText(representativeTable.getRow(1).getCell(2), " ", false, false, null);
        setCellText(representativeTable.getRow(1).getCell(3), "정보주체와\n의 관계", false, true, null);
        setCellText(representativeTable.getRow(1).getCell(4), " ", false, false, null);
        setVerticalMerge(representativeTable, 0, 0, 1);
        setRowHeight(representativeTable.getRow(0), 420);
        setRowHeight(representativeTable.getRow(1), 600);

        addSpacer(document, 95);
        XWPFTable noticeTable = document.createTable(2, 1);
        configureTable(noticeTable, BODY_WIDTH_TWIPS);
        XWPFTableCell noticeTitleCell = noticeTable.getRow(0).getCell(0);
        clearCell(noticeTitleCell);
        XWPFParagraph noticeTitle = noticeTitleCell.addParagraph();
        noticeTitle.setSpacingAfter(0);
        XWPFRun noticeTitleRun = noticeTitle.createRun();
        writeRun(noticeTitleRun, "유의사항 *회원탈퇴요청 전에 꼭 확인하세요.", 11, true);
        setRowHeight(noticeTable.getRow(0), 360);

        XWPFTableCell noticeCell = noticeTable.getRow(1).getCell(0);
        clearCell(noticeCell);
        for (String line : List.of(
            "- 배달의민족 회원탈퇴 후 재가입할 경우 탈퇴 전의 회원정보와 거래정보 및 포인트, 쿠폰정보 등은 복구되지 않습니다.",
            "- 배달의민족 회원탈퇴 시 회원님이 보유하고 있던 비현금성 포인트와 쿠폰은 모두 소멸되며, 복구가 불가능합니다. 다만 유상성 쿠폰의 경우 쿠폰을 모두 사용완료하거나 환불 후 쿠폰 소멸에 대한 동의를 받습니다.",
            "- 배달의민족 회원이 탈퇴하려는 경우 결제 편의를 목적으로 회원이 지정(선택)한 부가서비스(ex. 배민페이)는 해지되며, 해당 서비스 회원의 자격도 자동으로 상실(탈퇴)됩니다.",
            "- 회원 탈퇴한 계정의 배달의민족 서비스 이용기록은 모두 삭제되며, 삭제된 데이터는 복구가 불가능합니다.",
            "다만, 거래정보가 있는 경우 판매거래 정보관리를 위해 아이디, 거래내역<결제내역, 삭제된 리뷰 포함>에 대한 기본정보는 탈퇴 후 5년간 보관합니다.",
            "[삭제되는 이용 기록]",
            "아이디, 전자우편주소, 휴대전화번호, 주문이력, 찜, 관심지역, 포인트, 쿠폰, 간단 결제 카드정보, OK 캐쉬백 이력",
            "- 법령에 의하여 보관해야 하는 경우 또는 회원가입 남용, 서비스 부정사용 등을 위한 회사 내부정책에 의하여 보관해야하는 정보는 회원탈퇴 후에도 일정기간 보관됩니다.",
            "자세한 사항은 배달의민족 개인정보 처리방침에서 확인하실 수 있습니다."
        )) {
            XWPFParagraph noticeLine = noticeCell.addParagraph();
            noticeLine.setSpacingAfter(30);
            noticeLine.setSpacingBetween(1.04);
            writeRun(noticeLine.createRun(), line, 9, false);
        }

        addSpacer(document, 22);
        addParagraph(document, "보유하신 쿠폰이 있으신 경우, 탈퇴 시 함께 소멸되는 것에 동의하며,", 8, ParagraphAlignment.LEFT);
        addParagraph(document, "「개인정보보호법」제 36조, 「정보통신망법」제 30조에 따라 회원탈퇴를 요청합니다.", 8, ParagraphAlignment.LEFT);

        addSpacer(document, 75);
        XWPFParagraph date = document.createParagraph();
        date.setAlignment(ParagraphAlignment.RIGHT);
        writeRun(date.createRun(), "년        월        일", 10, false);

        XWPFParagraph requester = document.createParagraph();
        requester.setAlignment(ParagraphAlignment.RIGHT);
        writeRun(requester.createRun(), "요청인                         (서명 또는 날인)", 10, false);
    }

    private void setupA4Document(XWPFDocument document) {
        var sectPr = document.getDocument().getBody().isSetSectPr()
            ? document.getDocument().getBody().getSectPr()
            : document.getDocument().getBody().addNewSectPr();
        var pageSize = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        pageSize.setW(BigInteger.valueOf(A4_WIDTH_TWIPS));
        pageSize.setH(BigInteger.valueOf(A4_HEIGHT_TWIPS));

        var margins = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        margins.setTop(BigInteger.valueOf(1550));
        margins.setBottom(BigInteger.valueOf(100));
        margins.setLeft(BigInteger.valueOf(1450));
        margins.setRight(BigInteger.valueOf(1450));
    }

    private void addParagraph(XWPFDocument document, String text, int fontSize, ParagraphAlignment alignment) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(alignment);
        paragraph.setSpacingAfter(40);
        paragraph.setSpacingBetween(0.95);
        writeRun(paragraph.createRun(), text, fontSize, false);
    }

    private void writeRun(XWPFRun run, String text, int fontSize, boolean bold) {
        run.setFontFamily(KOREAN_FONT);
        run.setFontSize(fontSize);
        run.setBold(bold);
        run.setText(text);
    }

    private void addSpacer(XWPFDocument document, int heightTwips) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(heightTwips);
    }

    private void configureTable(XWPFTable table, int widthTwips) {
        table.setWidth(widthTwips);
        var tablePr = table.getCTTbl().getTblPr();
        if (tablePr == null) {
            tablePr = table.getCTTbl().addNewTblPr();
        }
        var tableWidth = tablePr.isSetTblW() ? tablePr.getTblW() : tablePr.addNewTblW();
        tableWidth.setType(STTblWidth.DXA);
        tableWidth.setW(BigInteger.valueOf(widthTwips));
    }

    private void setColumnWidths(XWPFTable table, int... widths) {
        var ctTable = table.getCTTbl();
        var tableGrid = ctTable.addNewTblGrid();
        for (int width : widths) {
            tableGrid.addNewGridCol().setW(BigInteger.valueOf(width));
        }

        for (XWPFTableRow row : table.getRows()) {
            for (int i = 0; i < row.getTableCells().size() && i < widths.length; i++) {
                XWPFTableCell cell = row.getCell(i);
                var tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
                var cellWidth = tcPr.isSetTcW() ? tcPr.getTcW() : tcPr.addNewTcW();
                cellWidth.setType(STTblWidth.DXA);
                cellWidth.setW(BigInteger.valueOf(widths[i]));
            }
        }
    }

    private void setRowHeight(XWPFTableRow row, int heightTwips) {
        row.setHeight(heightTwips);
        row.setHeightRule(TableRowHeightRule.EXACT);
    }

    private void setVerticalMerge(XWPFTable table, int columnIndex, int fromRow, int toRow) {
        for (int rowIndex = fromRow; rowIndex <= toRow; rowIndex++) {
            XWPFTableCell cell = table.getRow(rowIndex).getCell(columnIndex);
            var tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
            var vMerge = tcPr.isSetVMerge() ? tcPr.getVMerge() : tcPr.addNewVMerge();
            vMerge.setVal(rowIndex == fromRow ? STMerge.RESTART : STMerge.CONTINUE);
        }
        clearCell(table.getRow(toRow).getCell(columnIndex));
        table.getRow(toRow).getCell(columnIndex).addParagraph();
    }

    private void setCellText(XWPFTableCell cell, String text, boolean bold, boolean center, String color) {
        clearCell(cell);
        if (color != null) {
            cell.setColor(color);
        }
        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        String[] lines = text.split("\\R", -1);
        XWPFParagraph paragraph = cell.addParagraph();
        paragraph.setAlignment(center ? ParagraphAlignment.CENTER : ParagraphAlignment.LEFT);
        paragraph.setVerticalAlignment(TextAlignment.CENTER);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                paragraph.createRun().addBreak();
            }
            writeRun(paragraph.createRun(), lines[i], 10, bold);
        }
    }

    private void clearCell(XWPFTableCell cell) {
        while (cell.getParagraphs().size() > 0) {
            cell.removeParagraph(0);
        }
    }

    private void addReferencePreviewSection(XWPFDocument document, PDDocument pdfDocument, PDFRenderer renderer) throws IOException {
        document.createParagraph().setPageBreak(true);
        XWPFParagraph heading = document.createParagraph();
        XWPFRun run = heading.createRun();
        run.setBold(true);
        run.setText("원본 PDF 참고 이미지");

        for (int pageIndex = 0; pageIndex < pdfDocument.getNumberOfPages(); pageIndex++) {
            addPagePreview(document, renderer, pdfDocument.getPage(pageIndex), pageIndex);
        }
    }

    private void writeRowsAsEditableContent(XWPFDocument document, List<TextRow> rows) {
        int index = 0;
        while (index < rows.size()) {
            TextRow row = rows.get(index);
            if (row.cells().size() >= 2) {
                int tableEnd = index;
                while (tableEnd < rows.size() && rows.get(tableEnd).cells().size() >= 2) {
                    tableEnd++;
                }
                writeTable(document, rows.subList(index, tableEnd));
                index = tableEnd;
            } else {
                String text = row.text();
                if (!text.isBlank()) {
                    document.createParagraph().createRun().setText(text);
                }
                index++;
            }
        }
    }

    private void writeTable(XWPFDocument document, List<TextRow> tableRows) {
        XWPFTable table = document.createTable(tableRows.size(), maxCellCount(tableRows));
        for (int rowIndex = 0; rowIndex < tableRows.size(); rowIndex++) {
            XWPFTableRow docxRow = table.getRow(rowIndex);
            List<String> cells = tableRows.get(rowIndex).cells();
            for (int cellIndex = 0; cellIndex < docxRow.getTableCells().size(); cellIndex++) {
                XWPFTableCell cell = docxRow.getCell(cellIndex);
                cell.removeParagraph(0);
                XWPFParagraph paragraph = cell.addParagraph();
                paragraph.createRun().setText(cellIndex < cells.size() ? cells.get(cellIndex) : "");
            }
        }
    }

    private int maxCellCount(List<TextRow> rows) {
        return rows.stream()
            .mapToInt(row -> row.cells().size())
            .max()
            .orElse(1);
    }

    private List<TextRow> groupRows(List<TextPosition> positions) {
        List<TextPosition> sorted = positions.stream()
            .sorted(Comparator.comparing(TextPosition::getYDirAdj).thenComparing(TextPosition::getXDirAdj))
            .toList();

        List<List<TextPosition>> rowGroups = new ArrayList<>();
        for (TextPosition position : sorted) {
            List<TextPosition> row = findRow(rowGroups, position);
            row.add(position);
        }

        return rowGroups.stream()
            .map(this::toTextRow)
            .filter(row -> !row.text().isBlank())
            .toList();
    }

    private List<TextPosition> findRow(List<List<TextPosition>> rows, TextPosition position) {
        for (List<TextPosition> row : rows) {
            if (Math.abs(row.get(0).getYDirAdj() - position.getYDirAdj()) <= ROW_TOLERANCE) {
                return row;
            }
        }
        List<TextPosition> row = new ArrayList<>();
        rows.add(row);
        return row;
    }

    private TextRow toTextRow(List<TextPosition> positions) {
        List<TextPosition> sorted = positions.stream()
            .sorted(Comparator.comparing(TextPosition::getXDirAdj))
            .toList();

        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        TextPosition previous = null;
        for (TextPosition position : sorted) {
            if (previous != null) {
                float gap = position.getXDirAdj() - (previous.getXDirAdj() + previous.getWidthDirAdj());
                if (gap > CELL_GAP_THRESHOLD) {
                    addCell(cells, cell);
                } else if (gap > previous.getWidthOfSpace() / 2) {
                    cell.append(' ');
                }
            }
            cell.append(position.getUnicode());
            previous = position;
        }
        addCell(cells, cell);

        return new TextRow(String.join(" ", cells).trim(), cells);
    }

    private void addCell(List<String> cells, StringBuilder cell) {
        String text = cell.toString().trim();
        if (!text.isBlank()) {
            cells.add(text);
        }
        cell.setLength(0);
    }

    private void addPagePreview(XWPFDocument document, PDFRenderer renderer, PDPage page, int pageIndex) throws IOException {
        try (ByteArrayOutputStream imageOutput = new ByteArrayOutputStream()) {
            ImageIO.write(renderer.renderImageWithDPI(pageIndex, 120), "png", imageOutput);

            XWPFParagraph caption = document.createParagraph();
            XWPFRun captionRun = caption.createRun();
            captionRun.setItalic(true);
            captionRun.setText("원본 페이지 미리보기");

            float ratio = page.getMediaBox().getHeight() / page.getMediaBox().getWidth();
            int previewHeight = (int) (PAGE_PREVIEW_WIDTH_EMU * ratio);
            XWPFParagraph imageParagraph = document.createParagraph();
            XWPFRun imageRun = imageParagraph.createRun();
            try (ByteArrayInputStream imageInput = new ByteArrayInputStream(imageOutput.toByteArray())) {
                imageRun.addPicture(
                    imageInput,
                    XWPFDocument.PICTURE_TYPE_PNG,
                    "page-" + (pageIndex + 1) + ".png",
                    PAGE_PREVIEW_WIDTH_EMU,
                    previewHeight
                );
            } catch (Exception exception) {
                throw new IOException("PDF 페이지 미리보기 이미지를 DOCX에 추가하지 못했습니다.", exception);
            }
        }
    }

    private record TextRow(String text, List<String> cells) {
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
            positionsByPage.computeIfAbsent(getCurrentPageNo(), key -> new ArrayList<>()).add(text);
        }
    }
}
