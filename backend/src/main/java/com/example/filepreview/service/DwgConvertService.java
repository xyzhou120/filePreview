package com.example.filepreview.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * DWG文件转换服务
 * 
 * 使用ODA File Converter将DWG转换为PDF/PNG
 * 
 * 安装ODA File Converter：
 * 1. 下载：https://www.opendesign.com/resources/oda-file-converter
 * 2. Linux: 解压到 /opt/ODAFileConverter/
 * 3. 授权：运行一次 ./ODAFileConverter - getActivationCode，然后激活
 * 
 * Docker部署：
 * docker run -d --name oda-converter -v /data/oda:/data keking/oda-converter:latest
 */
@Slf4j
@Service
public class DwgConvertService {

    @Value("${dwg.converter.path:/opt/ODAFileConverter/ODAFileConverter}")
    private String odaConverterPath;

    @Value("${dwg.converter.output-dir:/tmp/dwg-preview}")
    private String outputDir;

    @Value("${dwg.converter.out-format:PNG")
    private String outputFormat;

    /**
     * 将DWG文件转换为PNG图片
     *
     * @param dwgUrl DWG文件的URL（需要是ODA能访问到的地址）
     * @return 转换后的PNG文件路径
     */
    public String convertToPng(String dwgUrl) {
        return convert(dwgUrl, "PNG");
    }

    /**
     * 将DWG文件转换为PDF
     *
     * @param dwgUrl DWG文件的URL
     * @return 转换后的PDF文件路径
     */
    public String convertToPdf(String dwgUrl) {
        return convert(dwgUrl, "PDF");
    }

    /**
     * 执行DWG转换
     *
     * @param dwgUrl      DWG文件URL
     * @param targetFormat 目标格式：PNG / PDF / SVG / DXF
     * @return 转换后的文件路径
     */
    public String convert(String dwgUrl, String targetFormat) {
        log.info("开始转换DWG文件: {}, 目标格式: {}", dwgUrl, targetFormat);

        // 创建输出目录
        File outputDirFile = new File(outputDir);
        if (!outputDirFile.exists()) {
            outputDirFile.mkdirs();
        }

        // 生成唯一输出文件名
        String outputFileName = UUID.randomUUID().toString() + "." + targetFormat.toLowerCase();
        String outputFilePath = outputDir + "/" + outputFileName;

        // 下载DWG文件到临时目录
        String tempDwgPath = null;
        try {
            tempDwgPath = downloadDwgFile(dwgUrl);
            
            // 执行转换命令
            // ODA File Converter 用法：ODAFileConverter <input_file> <output_dir> <output_format>
            ProcessBuilder pb = new ProcessBuilder(
                    odaConverterPath,
                    tempDwgPath,
                    outputDir,
                    targetFormat
            );
            
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 等待转换完成（最多5分钟）
            boolean finished = process.waitFor(5, TimeUnit.MINUTES);
            
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("DWG转换超时");
            }

            int exitCode = process.waitFor();
            
            // 读取转换输出
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("ODA输出: {}", line);
                }
            }

            if (exitCode != 0) {
                throw new RuntimeException("DWG转换失败，退出码: " + exitCode);
            }

            // 检查输出文件
            File outputFile = new File(outputFilePath);
            if (!outputFile.exists()) {
                // ODA可能输出文件名不同，查找最新生成的文件
                File[] files = outputDirFile.listFiles((dir, name) -> 
                        name.endsWith("." + targetFormat.toLowerCase()));
                if (files != null && files.length > 0) {
                    // 返回最新创建的文件
                    long latest = 0;
                    for (File f : files) {
                        if (f.lastModified() > latest) {
                            latest = f.lastModified();
                            outputFilePath = f.getAbsolutePath();
                        }
                    }
                } else {
                    throw new RuntimeException("转换后的文件不存在");
                }
            }

            log.info("DWG转换成功: {}", outputFilePath);
            return outputFilePath;

        } catch (Exception e) {
            log.error("DWG转换失败", e);
            throw new RuntimeException("DWG转换失败: " + e.getMessage(), e);
        } finally {
            // 清理临时DWG文件
            if (tempDwgPath != null) {
                try {
                    Files.deleteIfExists(Path.of(tempDwgPath));
                } catch (IOException e) {
                    log.warn("清理临时文件失败: {}", tempDwgPath);
                }
            }
        }
    }

    /**
     * 下载DWG文件到本地
     */
    private String downloadDwgFile(String dwgUrl) throws IOException {
        log.debug("下载DWG文件: {}", dwgUrl);
        
        String tempFile = outputDir + "/" + UUID.randomUUID().toString() + ".dwg";
        
        try (InputStream in = new java.net.URL(dwgUrl).openStream();
             FileOutputStream out = new FileOutputStream(tempFile)) {
            
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
        }
        
        log.debug("DWG文件下载完成: {}", tempFile);
        return tempFile;
    }

    /**
     * 获取转换后的预览URL
     * 
     * @param dwgUrl 原始DWG的URL
     * @param basePreviewUrl 预览服务的基础URL
     * @return 预览URL
     */
    public String getPreviewUrl(String dwgUrl, String basePreviewUrl) {
        String encodedUrl;
        try {
            encodedUrl = java.net.URLEncoder.encode(dwgUrl, "UTF-8");
        } catch (Exception e) {
            encodedUrl = dwgUrl;
        }
        return basePreviewUrl + "/dwg/preview?fileUrl=" + encodedUrl;
    }

    /**
     * 检查ODA Converter是否可用
     */
    public boolean isConverterAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder(odaConverterPath, "-h");
            Process process = pb.start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            log.warn("ODA Converter不可用: {}", e.getMessage());
            return false;
        }
    }
}
