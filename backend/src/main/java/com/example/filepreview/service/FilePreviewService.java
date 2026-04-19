package com.example.filepreview.service;

import com.example.filepreview.entity.FilePreviewVO;
import com.example.filepreview.entity.PreviewTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 文件预览服务
 * 
 * 支持的文件类型：
 * - 图片：PNG、JPG、JPEG、GIF、BMP、WebP、SVG
 * - 文档：PDF
 * - Office：XLS、XLSX、DOC、DOCX、PPT、PPTX
 * - CAD：DWG、DXF
 * - 其他：TXT、MD
 */
@Slf4j
@Service
public class FilePreviewService {

    @Value("${file.preview.base-url:}")
    private String previewBaseUrl;

    // 图片类文件（浏览器原生支持）
    private static final Set<String> IMAGE_TYPES = new HashSet<>(Arrays.asList(
            "png", "jpg", "jpeg", "gif", "bmp", "webp", "svg", "ico"
    ));

    // PDF文件
    private static final Set<String> PDF_TYPES = new HashSet<>(Arrays.asList(
            "pdf"
    ));

    // Excel文件
    private static final Set<String> EXCEL_TYPES = new HashSet<>(Arrays.asList(
            "xls", "xlsx"
    ));

    // Word文件
    private static final Set<String> WORD_TYPES = new HashSet<>(Arrays.asList(
            "doc", "docx"
    ));

    // PPT文件
    private static final Set<String> PPT_TYPES = new HashSet<>(Arrays.asList(
            "ppt", "pptx"
    ));

    // DWG文件
    private static final Set<String> DWG_TYPES = new HashSet<>(Arrays.asList(
            "dwg", "dxf"
    ));

    // Markdown文件
    private static final Set<String> MD_TYPES = new HashSet<>(Arrays.asList(
            "md", "markdown"
    ));

    // 音视频文件
    private static final Set<String> MEDIA_TYPES = new HashSet<>(Arrays.asList(
            "mp3", "mp4", "wav", "ogg", "webm", "avi", "mov"
    ));

    // 纯文本文件（可直接显示）
    private static final Set<String> TEXT_TYPES = new HashSet<>(Arrays.asList(
            "txt", "log", "json", "xml", "html", "css", "js", "java", "py", "sql"
    ));

    /**
     * 根据文件信息构建预览VO
     *
     * @param fileId   文件ID
     * @param fileName 文件名
     * @param fileUrl  文件URL（完整访问地址）
     * @param fileSize 文件大小
     * @return 预览信息
     */
    public FilePreviewVO buildPreviewVO(Long fileId, String fileName, String fileUrl, Long fileSize) {
        String fileType = extractFileType(fileName);
        PreviewTypeEnum previewType = determinePreviewType(fileType);

        return FilePreviewVO.builder()
                .fileId(fileId)
                .fileName(fileName)
                .fileType(fileType)
                .fileSize(fileSize)
                .mimeType(getMimeType(fileType))
                .previewType(previewType.getCode())
                .supportPreview(previewType != PreviewTypeEnum.DOWNLOAD)
                .fileUrl(fileUrl)
                .previewUrl(buildPreviewUrl(fileUrl, previewType))
                .build();
    }

    /**
     * 提取文件扩展名
     */
    public String extractFileType(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDotIndex + 1).toLowerCase();
    }

    /**
     * 判断预览类型
     */
    public PreviewTypeEnum determinePreviewType(String fileType) {
        if (!StringUtils.hasText(fileType)) {
            return PreviewTypeEnum.DOWNLOAD;
        }

        if (IMAGE_TYPES.contains(fileType)) {
            return PreviewTypeEnum.IMAGE;
        }
        if (PDF_TYPES.contains(fileType)) {
            return PreviewTypeEnum.PDF;
        }
        if (EXCEL_TYPES.contains(fileType)) {
            return PreviewTypeEnum.EXCEL;
        }
        if (WORD_TYPES.contains(fileType)) {
            return PreviewTypeEnum.WORD;
        }
        if (PPT_TYPES.contains(fileType)) {
            return PreviewTypeEnum.OFFICE;
        }
        if (DWG_TYPES.contains(fileType)) {
            return PreviewTypeEnum.DWG;
        }
        if (MD_TYPES.contains(fileType)) {
            return PreviewTypeEnum.MARKDOWN;
        }
        if (MEDIA_TYPES.contains(fileType)) {
            return PreviewTypeEnum.MEDIA;
        }
        if (TEXT_TYPES.contains(fileType)) {
            return PreviewTypeEnum.TEXT;
        }

        return PreviewTypeEnum.DOWNLOAD;
    }

    /**
     * 构建预览URL
     */
    private String buildPreviewUrl(String fileUrl, PreviewTypeEnum previewType) {
        if (!StringUtils.hasText(fileUrl)) {
            return null;
        }

        switch (previewType) {
            case IMAGE:
            case TEXT:
                // 图片和文本直接返回URL
                return fileUrl;
            case PDF:
            case EXCEL:
            case WORD:
            case MARKDOWN:
            case MEDIA:
                // 这些类型前端会自己处理，返回原始URL
                return fileUrl;
            case DWG:
                // DWG需要后端转换，返回转换接口
                return previewBaseUrl + "/api/file/dwg/preview?fileUrl=" + encodeUrl(fileUrl);
            default:
                return null;
        }
    }

    /**
     * 获取MIME类型
     */
    private String getMimeType(String fileType) {
        switch (fileType) {
            case "pdf": return "application/pdf";
            case "png": return "image/png";
            case "jpg":
            case "jpeg": return "image/jpeg";
            case "gif": return "image/gif";
            case "bmp": return "image/bmp";
            case "webp": return "image/webp";
            case "svg": return "image/svg+xml";
            case "xls": return "application/vnd.ms-excel";
            case "xlsx": return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "doc": return "application/msword";
            case "docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "ppt": return "application/vnd.ms-powerpoint";
            case "pptx": return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "dwg": return "image/vnd.dwg";
            case "dxf": return "image/vnd.dxf";
            case "txt": return "text/plain";
            case "mp3": return "audio/mpeg";
            case "mp4": return "video/mp4";
            default: return "application/octet-stream";
        }
    }

    private String encodeUrl(String url) {
        try {
            return java.net.URLEncoder.encode(url, "UTF-8");
        } catch (Exception e) {
            return url;
        }
    }
}
