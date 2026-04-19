package com.example.filepreview.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件预览信息VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilePreviewVO {

    /**
     * 文件ID
     */
    private Long fileId;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件类型（小写，如：pdf、png、xlsx、dwg）
     */
    private String fileType;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * MIME类型
     */
    private String mimeType;

    /**
     * 预览方式：AUTO（自动选择）、IMAGE（图片）、PDF（PDF预览）、OFFICE（Office预览）、DWG（DWG预览）、DOWNLOAD（下载）
     */
    private String previewType;

    /**
     * 预览URL（前端可直接使用）
     */
    private String previewUrl;

    /**
     * 原始文件URL（用于下载）
     */
    private String fileUrl;

    /**
     * 缩略图URL（图片/PDF用）
     */
    private String thumbnailUrl;

    /**
     * 是否支持预览
     */
    private Boolean supportPreview;
}
