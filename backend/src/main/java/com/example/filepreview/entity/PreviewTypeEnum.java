package com.example.filepreview.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文件预览类型枚举
 */
@Getter
@AllArgsConstructor
public enum PreviewTypeEnum {

    /**
     * 图片类（PNG、JPG、GIF、BMP、WebP、SVG）
     */
    IMAGE("image", "图片预览"),

    /**
     * PDF预览（使用pdf.js）
     */
    PDF("pdf", "PDF预览"),

    /**
     * Excel预览（使用xlsx.js）
     */
    EXCEL("excel", "Excel预览"),

    /**
     * Word预览（使用mammoth.js）
     */
    WORD("word", "Word预览"),

    /**
     * Office预览
     */
    OFFICE("office", "Office预览"),

    /**
     * DWG CAD文件预览（后端转换）
     */
    DWG("dwg", "DWG预览"),

    /**
     * Markdown预览
     */
    MARKDOWN("markdown", "Markdown预览"),

    /**
     * 文本预览
     */
    TEXT("text", "文本预览"),

    /**
     * 音视频预览
     */
    MEDIA("media", "音视频预览"),

    /**
     * 直接下载（不支持预览的格式）
     */
    DOWNLOAD("download", "下载");

    private final String code;
    private final String desc;
}
