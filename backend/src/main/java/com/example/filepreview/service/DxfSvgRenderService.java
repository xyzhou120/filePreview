package com.example.filepreview.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class DxfSvgRenderService {

    private static final double DEFAULT_WIDTH = 1200;
    private static final double DEFAULT_HEIGHT = 800;
    private static final double MARGIN_RATIO = 0.04;

    public String render(Path dxfPath) throws IOException {
        List<String> lines = readLines(dxfPath);
        List<Pair> pairs = toPairs(lines);
        RenderState state = new RenderState();

        boolean inEntities = false;
        boolean readingPolyline = false;
        List<Point> polylinePoints = new ArrayList<>();
        boolean polylineClosed = false;

        for (int i = 0; i < pairs.size(); i++) {
            Pair pair = pairs.get(i);
            if (!"0".equals(pair.code)) {
                continue;
            }

            String value = pair.value.toUpperCase(Locale.ROOT);
            if ("SECTION".equals(value) && i + 1 < pairs.size()
                    && "2".equals(pairs.get(i + 1).code)
                    && "ENTITIES".equalsIgnoreCase(pairs.get(i + 1).value)) {
                inEntities = true;
                i++;
                continue;
            }
            if ("ENDSEC".equals(value) && inEntities) {
                inEntities = false;
                continue;
            }
            if (!inEntities) {
                continue;
            }

            Entity entity = readEntity(pairs, i);
            i = entity.endIndex - 1;

            switch (entity.type) {
                case "LINE":
                    renderLine(entity, state);
                    break;
                case "CIRCLE":
                    renderCircle(entity, state);
                    break;
                case "ARC":
                    renderArc(entity, state);
                    break;
                case "LWPOLYLINE":
                    renderLightweightPolyline(entity, state);
                    break;
                case "POLYLINE":
                    readingPolyline = true;
                    polylineClosed = ((int) entity.firstDouble("70", 0) & 1) == 1;
                    polylinePoints = new ArrayList<>();
                    break;
                case "VERTEX":
                    if (readingPolyline) {
                        polylinePoints.add(new Point(entity.firstDouble("10", 0), entity.firstDouble("20", 0)));
                    }
                    break;
                case "SEQEND":
                    if (readingPolyline) {
                        renderPolyline(polylinePoints, polylineClosed, state);
                        readingPolyline = false;
                    }
                    break;
                case "TEXT":
                case "MTEXT":
                    renderText(entity, state);
                    break;
                default:
                    break;
            }
        }

        if (readingPolyline) {
            renderPolyline(polylinePoints, polylineClosed, state);
        }

        return buildSvg(state);
    }

    private List<String> readLines(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        Charset charset = looksUtf8(bytes) ? StandardCharsets.UTF_8 : Charset.forName("GBK");
        String content = new String(bytes, charset);
        return new java.io.BufferedReader(new StringReader(content)).lines().collect(Collectors.toList());
    }

    private boolean looksUtf8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8).indexOf('\uFFFD') < 0;
    }

    private List<Pair> toPairs(List<String> lines) {
        List<Pair> pairs = new ArrayList<>();
        for (int i = 0; i + 1 < lines.size(); i += 2) {
            pairs.add(new Pair(lines.get(i).trim(), lines.get(i + 1).trim()));
        }
        return pairs;
    }

    private Entity readEntity(List<Pair> pairs, int start) {
        String type = pairs.get(start).value.toUpperCase(Locale.ROOT);
        Entity entity = new Entity(type);
        int i = start + 1;
        while (i < pairs.size() && !"0".equals(pairs.get(i).code)) {
            entity.pairs.add(pairs.get(i));
            i++;
        }
        entity.endIndex = i;
        return entity;
    }

    private void renderLine(Entity entity, RenderState state) {
        double x1 = entity.firstDouble("10", 0);
        double y1 = entity.firstDouble("20", 0);
        double x2 = entity.firstDouble("11", 0);
        double y2 = entity.firstDouble("21", 0);
        state.addBounds(x1, y1);
        state.addBounds(x2, y2);
        state.geometry.add(String.format(Locale.ROOT,
                "<line x1=\"%.4f\" y1=\"%.4f\" x2=\"%.4f\" y2=\"%.4f\" />", x1, y1, x2, y2));
    }

    private void renderCircle(Entity entity, RenderState state) {
        double cx = entity.firstDouble("10", 0);
        double cy = entity.firstDouble("20", 0);
        double r = entity.firstDouble("40", 0);
        if (r <= 0) {
            return;
        }
        state.addBounds(cx - r, cy - r);
        state.addBounds(cx + r, cy + r);
        state.geometry.add(String.format(Locale.ROOT,
                "<circle cx=\"%.4f\" cy=\"%.4f\" r=\"%.4f\" />", cx, cy, r));
    }

    private void renderArc(Entity entity, RenderState state) {
        double cx = entity.firstDouble("10", 0);
        double cy = entity.firstDouble("20", 0);
        double r = entity.firstDouble("40", 0);
        double start = Math.toRadians(entity.firstDouble("50", 0));
        double end = Math.toRadians(entity.firstDouble("51", 0));
        if (r <= 0) {
            return;
        }

        double x1 = cx + r * Math.cos(start);
        double y1 = cy + r * Math.sin(start);
        double x2 = cx + r * Math.cos(end);
        double y2 = cy + r * Math.sin(end);
        double diff = Math.toDegrees(end - start);
        while (diff < 0) {
            diff += 360;
        }
        int largeArc = diff > 180 ? 1 : 0;

        state.addBounds(cx - r, cy - r);
        state.addBounds(cx + r, cy + r);
        state.geometry.add(String.format(Locale.ROOT,
                "<path d=\"M %.4f %.4f A %.4f %.4f 0 %d 1 %.4f %.4f\" />",
                x1, y1, r, r, largeArc, x2, y2));
    }

    private void renderLightweightPolyline(Entity entity, RenderState state) {
        List<Double> xs = entity.doubles("10");
        List<Double> ys = entity.doubles("20");
        List<Point> points = new ArrayList<>();
        int count = Math.min(xs.size(), ys.size());
        for (int i = 0; i < count; i++) {
            points.add(new Point(xs.get(i), ys.get(i)));
        }
        boolean closed = ((int) entity.firstDouble("70", 0) & 1) == 1;
        renderPolyline(points, closed, state);
    }

    private void renderPolyline(List<Point> points, boolean closed, RenderState state) {
        if (points.size() < 2) {
            return;
        }

        StringBuilder builder = new StringBuilder("<polyline points=\"");
        for (Point point : points) {
            state.addBounds(point.x, point.y);
            builder.append(String.format(Locale.ROOT, "%.4f,%.4f ", point.x, point.y));
        }
        builder.append("\"");
        if (closed) {
            builder.append(" data-closed=\"true\"");
        }
        builder.append(" />");
        state.geometry.add(builder.toString());

        if (closed) {
            Point first = points.get(0);
            Point last = points.get(points.size() - 1);
            state.geometry.add(String.format(Locale.ROOT,
                    "<line x1=\"%.4f\" y1=\"%.4f\" x2=\"%.4f\" y2=\"%.4f\" />",
                    last.x, last.y, first.x, first.y));
        }
    }

    private void renderText(Entity entity, RenderState state) {
        String text = entity.first("1", "");
        if (text.trim().isEmpty()) {
            text = entity.first("3", "");
        }
        if (text.trim().isEmpty()) {
            return;
        }

        double x = entity.firstDouble("10", 0);
        double y = entity.firstDouble("20", 0);
        double height = Math.max(entity.firstDouble("40", 10), 2);
        state.addBounds(x, y);
        state.addBounds(x + text.length() * height * 0.6, y + height);
        state.text.add(String.format(Locale.ROOT,
                "<text x=\"%.4f\" y=\"%.4f\" font-size=\"%.4f\">%s</text>",
                x, -y, height, escapeXml(text)));
    }

    private String buildSvg(RenderState state) {
        double minX = state.hasBounds ? state.minX : 0;
        double minY = state.hasBounds ? state.minY : 0;
        double maxX = state.hasBounds ? state.maxX : DEFAULT_WIDTH;
        double maxY = state.hasBounds ? state.maxY : DEFAULT_HEIGHT;
        double width = Math.max(maxX - minX, 1);
        double height = Math.max(maxY - minY, 1);
        double margin = Math.max(Math.max(width, height) * MARGIN_RATIO, 10);

        minX -= margin;
        maxX += margin;
        minY -= margin;
        maxY += margin;
        width = maxX - minX;
        height = maxY - minY;

        StringBuilder svg = new StringBuilder();
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        svg.append(String.format(Locale.ROOT,
                "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"%.4f %.4f %.4f %.4f\" role=\"img\">\n",
                minX, -maxY, width, height));
        svg.append("<rect x=\"").append(format(minX)).append("\" y=\"").append(format(-maxY))
                .append("\" width=\"").append(format(width)).append("\" height=\"").append(format(height))
                .append("\" fill=\"#ffffff\" />\n");
        svg.append("<g transform=\"scale(1 -1)\" fill=\"none\" stroke=\"#1f2937\" stroke-width=\"")
                .append(format(Math.max(Math.max(width, height) / 1000, 0.5)))
                .append("\" vector-effect=\"non-scaling-stroke\" stroke-linecap=\"round\" stroke-linejoin=\"round\">\n");
        for (String item : state.geometry) {
            svg.append(item).append('\n');
        }
        svg.append("</g>\n");
        svg.append("<g fill=\"#111827\" font-family=\"Arial, sans-serif\">\n");
        for (String item : state.text) {
            svg.append(item).append('\n');
        }
        svg.append("</g>\n");
        if (state.geometry.isEmpty() && state.text.isEmpty()) {
            svg.append("<text x=\"24\" y=\"48\" fill=\"#6b7280\" font-family=\"Arial, sans-serif\" font-size=\"18\">DXF preview contains no supported entities</text>\n");
        }
        svg.append("</svg>\n");
        return svg.toString();
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private String escapeXml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static class Pair {
        private final String code;
        private final String value;

        private Pair(String code, String value) {
            this.code = code;
            this.value = value;
        }
    }

    private static class Entity {
        private final String type;
        private final List<Pair> pairs = new ArrayList<>();
        private int endIndex;

        private Entity(String type) {
            this.type = type;
        }

        private String first(String code, String defaultValue) {
            for (Pair pair : pairs) {
                if (code.equals(pair.code)) {
                    return pair.value;
                }
            }
            return defaultValue;
        }

        private double firstDouble(String code, double defaultValue) {
            String value = first(code, null);
            if (value == null) {
                return defaultValue;
            }
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        private List<Double> doubles(String code) {
            List<Double> values = new ArrayList<>();
            for (Pair pair : pairs) {
                if (!code.equals(pair.code)) {
                    continue;
                }
                try {
                    values.add(Double.parseDouble(pair.value));
                } catch (NumberFormatException ignored) {
                    // Ignore malformed numeric groups in partially supported DXF files.
                }
            }
            return values;
        }
    }

    private static class RenderState {
        private final List<String> geometry = new ArrayList<>();
        private final List<String> text = new ArrayList<>();
        private double minX;
        private double minY;
        private double maxX;
        private double maxY;
        private boolean hasBounds;

        private void addBounds(double x, double y) {
            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                return;
            }
            if (!hasBounds) {
                minX = maxX = x;
                minY = maxY = y;
                hasBounds = true;
                return;
            }
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
    }

    private static class Point {
        private final double x;
        private final double y;

        private Point(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }
}
