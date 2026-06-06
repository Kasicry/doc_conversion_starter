package com.docconversion.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.util.Units;
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.CTAnchor;
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.CTInline;
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.STRelFromH;
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.STRelFromV;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDrawing;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PdfToDocxConverter {

    private static final float ROW_TOLERANCE_POINTS = 3.0f;
    private static final float RENDER_DPI = 144.0f;
    private static final float MIN_TEXT_BOX_WIDTH_POINTS = 12.0f;
    private static final float MIN_TEXT_BOX_HEIGHT_POINTS = 8.0f;
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
                XWPFParagraph canvas = docx.createParagraph();
                canvas.setSpacingAfter(0);
                canvas.setSpacingBefore(0);

                addPageBackground(docx, canvas, renderer, page, pageIndex);
                List<DocumentTextLine> lines = groupIntoLines(textByPage.getOrDefault(pageIndex + 1, List.of()));
                if (lines.isEmpty() && ocrService.isAvailable()) {
                    BufferedImage ocrImage = renderer.renderImageWithDPI(pageIndex, ocrService.renderDpi());
                    lines = ocrService.recognize(ocrImage);
                }
                for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                    addEditableTextBox(canvas, lines.get(lineIndex), pageIndex, lineIndex);
                }
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

    private void addPageBackground(
        XWPFDocument document,
        XWPFParagraph paragraph,
        PDFRenderer renderer,
        PDPage page,
        int pageIndex
    ) throws IOException {
        BufferedImage image = renderer.renderImageWithDPI(pageIndex, RENDER_DPI);
        byte[] imageBytes;
        try (ByteArrayOutputStream imageOutput = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", imageOutput);
            imageBytes = imageOutput.toByteArray();
        }

        try (ByteArrayInputStream imageInput = new ByteArrayInputStream(imageBytes)) {
            XWPFRun run = paragraph.createRun();
            run.addPicture(
                imageInput,
                XWPFDocument.PICTURE_TYPE_PNG,
                "page-background-" + (pageIndex + 1) + ".png",
                Units.toEMU(page.getMediaBox().getWidth()),
                Units.toEMU(page.getMediaBox().getHeight())
            );
            moveInlinePictureBehindPage(run, pageIndex);
        } catch (Exception exception) {
            throw new IOException("PDF 페이지 배경을 DOCX에 추가하지 못했습니다.", exception);
        }
    }

    private void moveInlinePictureBehindPage(XWPFRun run, int pageIndex) {
        var drawing = run.getCTR().getDrawingArray(0);
        CTInline inline = drawing.getInlineArray(0);
        CTAnchor anchor = CTAnchor.Factory.newInstance();
        anchor.setDistT(0);
        anchor.setDistB(0);
        anchor.setDistL(0);
        anchor.setDistR(0);
        anchor.setSimplePos2(false);
        anchor.setRelativeHeight(0);
        anchor.setBehindDoc(true);
        anchor.setLocked(false);
        anchor.setLayoutInCell(true);
        anchor.setAllowOverlap(true);
        anchor.addNewSimplePos().setX(0);
        anchor.getSimplePos().setY(0);
        anchor.addNewPositionH().setRelativeFrom(STRelFromH.PAGE);
        anchor.getPositionH().setPosOffset(0);
        anchor.addNewPositionV().setRelativeFrom(STRelFromV.PAGE);
        anchor.getPositionV().setPosOffset(0);
        anchor.setExtent(inline.getExtent());
        anchor.addNewEffectExtent().setL(0);
        anchor.getEffectExtent().setT(0);
        anchor.getEffectExtent().setR(0);
        anchor.getEffectExtent().setB(0);
        anchor.addNewWrapNone();
        anchor.setDocPr(inline.getDocPr());
        anchor.setGraphic(inline.getGraphic());
        anchor.getDocPr().setId(pageIndex + 1L);
        anchor.getDocPr().setName("Page background " + (pageIndex + 1));
        drawing.setAnchorArray(new CTAnchor[] {anchor});
        drawing.removeInline(0);
    }

    private void addEditableTextBox(XWPFParagraph paragraph, DocumentTextLine line, int pageIndex, int lineIndex) throws IOException {
        float boxX = Math.max(0, line.x() - 1.0f);
        float boxY = Math.max(0, line.y() - line.height() - 1.0f);
        float boxWidth = Math.max(MIN_TEXT_BOX_WIDTH_POINTS, line.width() + 3.0f);
        float boxHeight = Math.max(MIN_TEXT_BOX_HEIGHT_POINTS, line.height() * 1.35f);
        int halfPointFontSize = Math.max(10, Math.round(line.fontSize() * 2));
        long x = Units.toEMU(boxX);
        long y = Units.toEMU(boxY);
        long width = Units.toEMU(boxWidth);
        long height = Units.toEMU(boxHeight);
        long shapeId = 10_000L + (pageIndex * 1_000L) + lineIndex;
        String xml = """
            <w:drawing xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                       xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
                       xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                       xmlns:wps="http://schemas.microsoft.com/office/word/2010/wordprocessingShape">
              <wp:anchor distT="0" distB="0" distL="0" distR="0" simplePos="0"
                         relativeHeight="%d" behindDoc="0" locked="0" layoutInCell="1" allowOverlap="1">
                <wp:simplePos x="0" y="0"/>
                <wp:positionH relativeFrom="page"><wp:posOffset>%d</wp:posOffset></wp:positionH>
                <wp:positionV relativeFrom="page"><wp:posOffset>%d</wp:posOffset></wp:positionV>
                <wp:extent cx="%d" cy="%d"/>
                <wp:effectExtent l="0" t="0" r="0" b="0"/>
                <wp:wrapNone/>
                <wp:docPr id="%d" name="editable-text-%d-%d"/>
                <a:graphic>
                  <a:graphicData uri="http://schemas.microsoft.com/office/word/2010/wordprocessingShape">
                    <wps:wsp>
                      <wps:cNvSpPr txBox="1"/>
                      <wps:spPr>
                        <a:xfrm><a:off x="0" y="0"/><a:ext cx="%d" cy="%d"/></a:xfrm>
                        <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
                        <a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill>
                        <a:ln><a:noFill/></a:ln>
                      </wps:spPr>
                      <wps:txbx>
                        <w:txbxContent>
                          <w:p>
                            <w:pPr><w:spacing w:before="0" w:after="0" w:line="240" w:lineRule="auto"/></w:pPr>
                            <w:r>
                              <w:rPr>
                                <w:rFonts w:ascii="%s" w:hAnsi="%s" w:eastAsia="%s"/>
                                <w:sz w:val="%d"/><w:szCs w:val="%d"/>
                              </w:rPr>
                              <w:t xml:space="preserve">%s</w:t>
                            </w:r>
                          </w:p>
                        </w:txbxContent>
                      </wps:txbx>
                      <wps:bodyPr wrap="none" lIns="0" tIns="0" rIns="0" bIns="0"/>
                    </wps:wsp>
                  </a:graphicData>
                </a:graphic>
              </wp:anchor>
            </w:drawing>
            """.formatted(
                251659264 + lineIndex,
                x,
                y,
                width,
                height,
                shapeId,
                pageIndex + 1,
                lineIndex + 1,
                width,
                height,
                KOREAN_FONT,
                KOREAN_FONT,
                KOREAN_FONT,
                halfPointFontSize,
                halfPointFontSize,
                escapeXml(line.text())
            );
        appendDrawing(paragraph, xml);
    }

    private void appendDrawing(XWPFParagraph paragraph, String xml) throws IOException {
        try {
            CTDrawing drawing = CTDrawing.Factory.parse(xml);
            paragraph.getCTP().addNewR().setDrawingArray(new CTDrawing[] {drawing});
        } catch (Exception exception) {
            throw new IOException("DOCX 시각 요소를 생성하지 못했습니다.", exception);
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

    private String escapeXml(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
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
