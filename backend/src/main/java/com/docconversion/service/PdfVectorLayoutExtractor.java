package com.docconversion.service;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;

public final class PdfVectorLayoutExtractor extends PDFGraphicsStreamEngine {

    private static final float AXIS_TOLERANCE = 1.0f;

    private final List<LineSegment> currentPath = new ArrayList<>();
    private final List<LineSegment> segments = new ArrayList<>();
    private final List<FillRegion> fillRegions = new ArrayList<>();
    private Point2D.Float currentPoint;
    private Point2D.Float subPathStart;

    public PdfVectorLayoutExtractor(PDPage page) {
        super(page);
    }

    public List<LineSegment> extract() throws IOException {
        processPage(getPage());
        return List.copyOf(segments);
    }

    public List<FillRegion> fillRegions() {
        return List.copyOf(fillRegions);
    }

    @Override
    public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
        addPathLine(p0, p1);
        addPathLine(p1, p2);
        addPathLine(p2, p3);
        addPathLine(p3, p0);
        currentPoint = point(p0);
        subPathStart = point(p0);
    }

    @Override
    public void drawImage(PDImage pdImage) {
    }

    @Override
    public void clip(int windingRule) {
    }

    @Override
    public void moveTo(float x, float y) {
        currentPoint = new Point2D.Float(x, y);
        subPathStart = currentPoint;
    }

    @Override
    public void lineTo(float x, float y) {
        if (currentPoint != null) {
            Point2D.Float next = new Point2D.Float(x, y);
            addPathLine(currentPoint, next);
            currentPoint = next;
        }
    }

    @Override
    public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        currentPoint = new Point2D.Float(x3, y3);
    }

    @Override
    public Point2D getCurrentPoint() {
        return currentPoint;
    }

    @Override
    public void closePath() {
        if (currentPoint != null && subPathStart != null) {
            addPathLine(currentPoint, subPathStart);
            currentPoint = subPathStart;
        }
    }

    @Override
    public void endPath() {
        currentPath.clear();
    }

    @Override
    public void strokePath() {
        segments.addAll(currentPath);
        currentPath.clear();
    }

    @Override
    public void fillPath(int windingRule) {
        captureFillRegion();
        if (!isAreaPath()) {
            segments.addAll(currentPath);
        }
        currentPath.clear();
    }

    @Override
    public void fillAndStrokePath(int windingRule) {
        captureFillRegion();
        segments.addAll(currentPath);
        currentPath.clear();
    }

    @Override
    public void shadingFill(COSName shadingName) {
    }

    private void addPathLine(Point2D from, Point2D to) {
        float pageHeight = getPage().getMediaBox().getHeight();
        float x1 = (float) from.getX();
        float y1 = pageHeight - (float) from.getY();
        float x2 = (float) to.getX();
        float y2 = pageHeight - (float) to.getY();
        if (Math.abs(x1 - x2) <= AXIS_TOLERANCE || Math.abs(y1 - y2) <= AXIS_TOLERANCE) {
            currentPath.add(LineSegment.normalized(x1, y1, x2, y2));
        }
    }

    private Point2D.Float point(Point2D value) {
        return new Point2D.Float((float) value.getX(), (float) value.getY());
    }

    private void captureFillRegion() {
        if (!isAreaPath()) {
            return;
        }
        float left = left();
        float right = right();
        float top = top();
        float bottom = bottom();
        try {
            int rgb = getGraphicsState().getNonStrokingColor().toRGB();
            if ((rgb & 0xFFFFFF) != 0xFFFFFF) {
                fillRegions.add(new FillRegion(left, top, right, bottom, rgb & 0xFFFFFF));
            }
        } catch (IOException ignored) {
            // Some color spaces cannot be converted to RGB. The table structure remains usable without shading.
        }
    }

    private boolean isAreaPath() {
        return !currentPath.isEmpty() && right() - left() >= 4 && bottom() - top() >= 4;
    }

    private float left() {
        return currentPath.stream().map(LineSegment::x1).min(Float::compare).orElse(0f);
    }

    private float right() {
        return currentPath.stream().map(LineSegment::x2).max(Float::compare).orElse(0f);
    }

    private float top() {
        return currentPath.stream().map(LineSegment::y1).min(Float::compare).orElse(0f);
    }

    private float bottom() {
        return currentPath.stream().map(LineSegment::y2).max(Float::compare).orElse(0f);
    }

    public record FillRegion(float left, float top, float right, float bottom, int rgb) {

        public boolean contains(float x, float y) {
            return x >= left - AXIS_TOLERANCE && x <= right + AXIS_TOLERANCE
                && y >= top - AXIS_TOLERANCE && y <= bottom + AXIS_TOLERANCE;
        }

        public String hexColor() {
            return String.format("%06X", rgb);
        }
    }

    public record LineSegment(float x1, float y1, float x2, float y2) {

        static LineSegment normalized(float x1, float y1, float x2, float y2) {
            return new LineSegment(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2), Math.max(y1, y2));
        }

        public boolean horizontal() {
            return Math.abs(y1 - y2) <= AXIS_TOLERANCE;
        }

        public boolean vertical() {
            return Math.abs(x1 - x2) <= AXIS_TOLERANCE;
        }

        public float length() {
            return horizontal() ? x2 - x1 : y2 - y1;
        }
    }
}
