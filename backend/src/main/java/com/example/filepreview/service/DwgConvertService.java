package com.example.filepreview.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class DwgConvertService {

    private final DxfSvgRenderService dxfSvgRenderService;

    @Value("${dwg.converter.path:/opt/ODAFileConverter/ODAFileConverter}")
    private String odaConverterPath;

    @Value("${dwg.converter.output-dir:/tmp/dwg-preview}")
    private String outputDir;

    @Value("${dwg.converter.output-version:ACAD2018}")
    private String outputVersion;

    @Value("${dwg.converter.timeout-seconds:300}")
    private long timeoutSeconds;

    @Value("${file.upload.dir:/data/uploads}")
    private String uploadDir;

    /**
     * 返回可直接用于预览的 SVG 文件路径。
     *
     * DWG 会先通过 ODA File Converter 转成 DXF，再由内置 DXF 渲染器转 SVG。
     * DXF 直接进入内置渲染器。
     */
    public String convert(String fileUrl, String targetFormat) {
        String normalizedFormat = normalizeFormat(targetFormat);
        if (!"SVG".equals(normalizedFormat)) {
            throw new IllegalArgumentException("DWG预览当前输出SVG，暂不支持格式: " + targetFormat);
        }

        Path workDir = Paths.get(outputDir, UUID.randomUUID().toString());
        Path inputDir = workDir.resolve("input");
        Path odaOutputDir = workDir.resolve("oda-output");
        Path previewDir = Paths.get(outputDir, "preview");

        try {
            Files.createDirectories(inputDir);
            Files.createDirectories(odaOutputDir);
            Files.createDirectories(previewDir);

            Path sourceFile = copySourceToWorkDir(fileUrl, inputDir);
            String sourceType = getExtension(sourceFile.getFileName().toString());
            Path dxfFile = "dxf".equals(sourceType) ? sourceFile : convertDwgToDxf(sourceFile, inputDir, odaOutputDir);

            String svg = dxfSvgRenderService.render(dxfFile);
            Path svgFile = previewDir.resolve(stripExtension(sourceFile.getFileName().toString()) + "-" + UUID.randomUUID() + ".svg");
            Files.write(svgFile, svg.getBytes(StandardCharsets.UTF_8));
            log.info("CAD预览生成成功: {}", svgFile);
            return svgFile.toString();
        } catch (Exception e) {
            log.error("CAD预览生成失败: {}", fileUrl, e);
            throw new RuntimeException("CAD预览生成失败: " + e.getMessage(), e);
        } finally {
            deleteQuietly(workDir);
        }
    }

    public String convertToSvg(String fileUrl) {
        return convert(fileUrl, "SVG");
    }

    public String convertToPng(String fileUrl) {
        throw new UnsupportedOperationException("当前DWG预览链路输出SVG，请调用convertToSvg");
    }

    public String convertToPdf(String fileUrl) {
        throw new UnsupportedOperationException("当前DWG预览链路输出SVG，请调用convertToSvg");
    }

    public boolean isConverterAvailable() {
        Path converter = Paths.get(odaConverterPath);
        return Files.isRegularFile(converter) && Files.isExecutable(converter);
    }

    private Path copySourceToWorkDir(String fileUrl, Path inputDir) throws IOException {
        if (!StringUtils.hasText(fileUrl)) {
            throw new IllegalArgumentException("fileUrl不能为空");
        }

        URI uri = parseUri(fileUrl);
        String extension = getExtension(uri != null && StringUtils.hasText(uri.getPath()) ? uri.getPath() : fileUrl);
        if (!"dwg".equals(extension) && !"dxf".equals(extension)) {
            throw new IllegalArgumentException("仅支持DWG/DXF文件预览");
        }

        String sourcePath = uri != null && StringUtils.hasText(uri.getPath()) ? uri.getPath() : fileUrl;
        String normalizedPath = stripQuery(sourcePath.replace('\\', '/'));
        int slashIndex = normalizedPath.lastIndexOf('/');
        String fileName = slashIndex >= 0 ? normalizedPath.substring(slashIndex + 1) : normalizedPath;
        if (!StringUtils.hasText(fileName) || !fileName.toLowerCase(Locale.ROOT).endsWith("." + extension)) {
            fileName = UUID.randomUUID() + "." + extension;
        }
        Path target = inputDir.resolve(fileName);

        if (uri != null && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            URLConnection connection = uri.toURL().openConnection();
            connection.setConnectTimeout((int) Duration.ofSeconds(15).toMillis());
            connection.setReadTimeout((int) Duration.ofMinutes(5).toMillis());
            try (InputStream inputStream = connection.getInputStream()) {
                Files.copy(inputStream, target);
            }
            return target;
        }

        Path localPath = resolveLocalPath(fileUrl, uri);
        if (!Files.isRegularFile(localPath)) {
            throw new IOException("源文件不存在: " + localPath);
        }
        Files.copy(localPath, target);
        return target;
    }

    private Path convertDwgToDxf(Path sourceFile, Path inputDir, Path odaOutputDir) throws IOException, InterruptedException {
        if (!isConverterAvailable()) {
            throw new IllegalStateException("ODA File Converter不可用，请配置dwg.converter.path");
        }

        Path logFile = odaOutputDir.resolve("oda-converter.log");
        ProcessBuilder processBuilder = new ProcessBuilder(
                odaConverterPath,
                inputDir.toString(),
                odaOutputDir.toString(),
                outputVersion,
                "DXF",
                "0",
                "1"
        );
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(logFile.toFile());

        log.info("执行ODA转换: {}", String.join(" ", processBuilder.command()));
        Process process = processBuilder.start();
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("ODA转换超时");
        }
        if (process.exitValue() != 0) {
            String output = Files.exists(logFile)
                    ? new String(Files.readAllBytes(logFile), StandardCharsets.UTF_8)
                    : "";
            throw new RuntimeException("ODA转换失败，退出码: " + process.exitValue() + "，输出: " + abbreviate(output, 1000));
        }

        String expectedBaseName = stripExtension(sourceFile.getFileName().toString()).toLowerCase(Locale.ROOT);
        try (Stream<Path> files = Files.walk(odaOutputDir)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> "dxf".equals(getExtension(path.getFileName().toString())))
                    .filter(path -> stripExtension(path.getFileName().toString()).toLowerCase(Locale.ROOT).equals(expectedBaseName))
                    .findFirst()
                    .orElseGet(() -> findLatestDxf(odaOutputDir));
        }
    }

    private Path findLatestDxf(Path odaOutputDir) {
        try (Stream<Path> files = Files.walk(odaOutputDir)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> "dxf".equals(getExtension(path.getFileName().toString())))
                    .max(Comparator.comparingLong(path -> path.toFile().lastModified()))
                    .orElseThrow(() -> new RuntimeException("ODA转换后未找到DXF文件"));
        } catch (IOException e) {
            throw new RuntimeException("读取ODA输出目录失败", e);
        }
    }

    private Path resolveLocalPath(String fileUrl, URI uri) {
        if (uri != null && "file".equalsIgnoreCase(uri.getScheme())) {
            return Paths.get(uri);
        }

        String path = stripQuery(fileUrl);
        if (path.startsWith("/uploads/")) {
            return Paths.get(uploadDir, path.substring("/uploads/".length()));
        }
        Path candidate = Paths.get(path);
        if (candidate.isAbsolute()) {
            return candidate;
        }
        return Paths.get(uploadDir, path);
    }

    private URI parseUri(String value) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String normalizeFormat(String targetFormat) {
        if (!StringUtils.hasText(targetFormat)) {
            return "SVG";
        }
        return targetFormat.trim().toUpperCase(Locale.ROOT);
    }

    private String getExtension(String value) {
        String clean = stripQuery(value).toLowerCase(Locale.ROOT);
        int dot = clean.lastIndexOf('.');
        if (dot < 0 || dot == clean.length() - 1) {
            return "";
        }
        return clean.substring(dot + 1);
    }

    private String stripExtension(String fileName) {
        String clean = stripQuery(fileName);
        int dot = clean.lastIndexOf('.');
        return dot < 0 ? clean : clean.substring(0, dot);
    }

    private String stripQuery(String value) {
        int queryIndex = value.indexOf('?');
        int fragmentIndex = value.indexOf('#');
        int endIndex = value.length();
        if (queryIndex >= 0) {
            endIndex = Math.min(endIndex, queryIndex);
        }
        if (fragmentIndex >= 0) {
            endIndex = Math.min(endIndex, fragmentIndex);
        }
        return value.substring(0, endIndex);
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private void deleteQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (IOException e) {
                    log.warn("清理临时文件失败: {}", item);
                }
            });
        } catch (IOException e) {
            log.warn("清理临时目录失败: {}", path);
        }
    }
}
