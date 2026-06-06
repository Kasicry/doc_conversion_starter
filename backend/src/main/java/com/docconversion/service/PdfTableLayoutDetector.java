package com.docconversion.service;

import com.docconversion.service.PdfVectorLayoutExtractor.LineSegment;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PdfTableLayoutDetector {

    private static final float CONNECT_TOLERANCE = 2.0f;
    private static final float COORDINATE_TOLERANCE = 2.0f;
    private static final float MIN_SEGMENT_LENGTH = 10.0f;
    private static final float MIN_COLUMN_WIDTH = 4.0f;

    public List<TableRegion> detect(List<LineSegment> source) {
        List<LineSegment> segments = source.stream()
            .filter(segment -> segment.length() >= MIN_SEGMENT_LENGTH)
            .toList();
        boolean[] visited = new boolean[segments.size()];
        List<TableRegion> tables = new ArrayList<>();

        for (int index = 0; index < segments.size(); index++) {
            if (visited[index]) {
                continue;
            }
            createRegion(collectComponent(segments, index, visited)).ifPresent(tables::add);
        }
        return tables.stream().sorted(Comparator.comparing(TableRegion::top)).toList();
    }

    private java.util.Optional<TableRegion> createRegion(List<LineSegment> component) {
        List<Float> xs = removeNarrowIntervals(clusteredCoordinates(component.stream()
            .filter(LineSegment::vertical)
            .map(LineSegment::x1)
            .toList()));
        List<Float> ys = clusteredCoordinates(component.stream()
            .filter(LineSegment::horizontal)
            .map(LineSegment::y1)
            .toList());
        if (xs.size() < 2 || ys.size() < 2
            || xs.get(xs.size() - 1) - xs.get(0) < 100
            || ys.get(ys.size() - 1) - ys.get(0) < 15) {
            return java.util.Optional.empty();
        }
        TableRegion region = new TableRegion(xs, ys, component);
        if (region.rows() == 1 && region.columns() == 1 && region.bottom() - region.top() > 120) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(region);
    }

    private List<LineSegment> collectComponent(List<LineSegment> segments, int start, boolean[] visited) {
        List<LineSegment> component = new ArrayList<>();
        List<Integer> queue = new ArrayList<>();
        queue.add(start);
        visited[start] = true;
        for (int cursor = 0; cursor < queue.size(); cursor++) {
            int index = queue.get(cursor);
            LineSegment current = segments.get(index);
            component.add(current);
            for (int candidateIndex = 0; candidateIndex < segments.size(); candidateIndex++) {
                if (!visited[candidateIndex] && connected(current, segments.get(candidateIndex))) {
                    visited[candidateIndex] = true;
                    queue.add(candidateIndex);
                }
            }
        }
        return component;
    }

    private boolean connected(LineSegment first, LineSegment second) {
        return rangesOverlap(first.x1(), first.x2(), second.x1(), second.x2())
            && rangesOverlap(first.y1(), first.y2(), second.y1(), second.y2());
    }

    private boolean rangesOverlap(float firstStart, float firstEnd, float secondStart, float secondEnd) {
        return firstStart <= secondEnd + CONNECT_TOLERANCE && secondStart <= firstEnd + CONNECT_TOLERANCE;
    }

    private List<Float> clusteredCoordinates(List<Float> values) {
        List<Float> sorted = values.stream().sorted().toList();
        List<List<Float>> clusters = new ArrayList<>();
        for (float value : sorted) {
            if (clusters.isEmpty()
                || Math.abs(clusters.get(clusters.size() - 1).get(0) - value) > COORDINATE_TOLERANCE) {
                clusters.add(new ArrayList<>());
            }
            clusters.get(clusters.size() - 1).add(value);
        }
        return clusters.stream()
            .map(cluster -> (float) cluster.stream().mapToDouble(Float::doubleValue).average().orElse(0))
            .toList();
    }

    private List<Float> removeNarrowIntervals(List<Float> coordinates) {
        if (coordinates.size() < 3) {
            return coordinates;
        }
        List<Float> result = new ArrayList<>();
        for (float coordinate : coordinates) {
            if (result.isEmpty() || coordinate - result.get(result.size() - 1) >= MIN_COLUMN_WIDTH) {
                result.add(coordinate);
            }
        }
        if (result.size() == 1 && coordinates.size() > 1) {
            result.add(coordinates.get(coordinates.size() - 1));
        }
        return result;
    }

    public record TableRegion(List<Float> xs, List<Float> ys, List<LineSegment> segments) {

        public float left() {
            return xs.get(0);
        }

        public float right() {
            return xs.get(xs.size() - 1);
        }

        public float top() {
            return ys.get(0);
        }

        public float bottom() {
            return ys.get(ys.size() - 1);
        }

        public int rows() {
            return ys.size() - 1;
        }

        public int columns() {
            return xs.size() - 1;
        }

        boolean contains(DocumentTextLine line) {
            float centerX = line.x() + (line.width() / 2);
            return centerX >= left() - 2 && centerX <= right() + 2
                && line.y() >= top() - 2 && line.y() <= bottom() + 2;
        }

        int rowOf(DocumentTextLine line) {
            return intervalOf(ys, line.y());
        }

        int columnOf(DocumentTextLine line) {
            return intervalOf(xs, line.x() + (line.width() / 2));
        }

        boolean hasVerticalBoundary(int boundaryIndex, int rowIndex) {
            float x = xs.get(boundaryIndex);
            float top = ys.get(rowIndex);
            float bottom = ys.get(rowIndex + 1);
            return segments.stream().anyMatch(segment -> segment.vertical()
                && Math.abs(segment.x1() - x) <= COORDINATE_TOLERANCE
                && segment.y1() <= top + CONNECT_TOLERANCE
                && segment.y2() >= bottom - CONNECT_TOLERANCE);
        }

        boolean hasHorizontalBoundary(int boundaryIndex, int columnIndex) {
            float y = ys.get(boundaryIndex);
            float left = xs.get(columnIndex);
            float right = xs.get(columnIndex + 1);
            return segments.stream().anyMatch(segment -> segment.horizontal()
                && Math.abs(segment.y1() - y) <= COORDINATE_TOLERANCE
                && segment.x1() <= left + CONNECT_TOLERANCE
                && segment.x2() >= right - CONNECT_TOLERANCE);
        }

        private int intervalOf(List<Float> boundaries, float value) {
            for (int index = 0; index < boundaries.size() - 1; index++) {
                if (value >= boundaries.get(index) - CONNECT_TOLERANCE
                    && value <= boundaries.get(index + 1) + CONNECT_TOLERANCE) {
                    return index;
                }
            }
            return -1;
        }
    }
}
