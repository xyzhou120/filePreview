package com.example.filepreview.controller;

import com.example.filepreview.entity.FilePreviewVO;
import com.example.filepreview.service.DwgConvertService;
import com.example.filepreview.service.FilePreviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 文件预览控制器
 * 
 * 提供文件预览相关接口
 */
@Slf4j
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    private final FilePreviewService filePreviewService;
    private final DwgConvertService dwgConvertService;

    @Value("${file.upload.dir:/data/uploads}")
    private String uploadDir;

    @Value("${file.preview.base-url:http://localhost:8080}")
    private String previewBaseUrl;

    /**
     * 获取文件预览信息
     * 
     * 根据文件类型自动判断预览方式
     *
     * @param fileId   文件ID（可选）
     * @param fileName 文件名（必填）
     * @param fileUrl  文件访问URL（必填）
     * @param fileSize 文件大小（可选）
     * @return 预览信息
     */
    @GetMapping("/preview")
    public ResponseEntity<FilePreviewVO> getPreview(
            @RequestParam(required = false) Long fileId,
            @RequestParam String fileName,
            @RequestParam String fileUrl,
            @RequestParam(required = false) Long fileSize) {
        
        log.info("获取预览信息: fileName={}, fileUrl={}", fileName, fileUrl);
        
        FilePreviewVO vo = filePreviewService.buildPreviewVO(
                fileId,
                fileName,
                fileUrl,
                fileSize
        );
        
        return ResponseEntity.ok(vo);
    }

    /**
     * 上传文件
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        log.info("上传文件: {}", file.getOriginalFilename());
        
        try {
            // 创建上传目录
            File uploadDirFile = new File(uploadDir);
            if (!uploadDirFile.exists()) {
                uploadDirFile.mkdirs();
            }
            
            // 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String fileType = filePreviewService.extractFileType(originalFilename);
            String newFileName = System.currentTimeMillis() + "_" + originalFilename;
            
            // 保存文件
            Path filePath = Paths.get(uploadDir, newFileName);
            Files.copy(file.getInputStream(), filePath);
            
            // 构建访问URL（假设文件可通过静态资源访问）
            String fileUrl = "/uploads/" + newFileName;
            
            // 获取预览信息
            FilePreviewVO previewVO = filePreviewService.buildPreviewVO(
                    null,
                    originalFilename,
                    fileUrl,
                    file.getSize()
            );
            
            Map<String, Object> result = new HashMap<>();
            result.put("fileId", newFileName);
            result.put("fileName", originalFilename);
            result.put("fileUrl", fileUrl);
            result.put("fileSize", file.getSize());
            result.put("preview", previewVO);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("文件上传失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("message", "文件上传失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * DWG文件预览接口
     * 
     * 将DWG文件转换为PNG后返回预览
     *
     * @param fileUrl DWG文件的URL
     * @param format  输出格式：PNG（默认）/ PDF
     * @return 转换后的图片/PDF
     */
    @GetMapping("/dwg/preview")
    public ResponseEntity<Resource> dwgPreview(
            @RequestParam String fileUrl,
            @RequestParam(defaultValue = "SVG") String format) {
        
        log.info("DWG预览请求: fileUrl={}, format={}", fileUrl, format);
        
        try {
            // 执行转换
            String convertedFilePath = dwgConvertService.convert(fileUrl, format);
            
            // 返回转换后的文件
            File file = new File(convertedFilePath);
            Resource resource = new FileSystemResource(file);
            
            String contentType = "image/svg+xml";
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=preview." + format.toLowerCase())
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(resource);
                    
        } catch (Exception e) {
            log.error("DWG预览生成失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(null);
        }
    }

    /**
     * 检查文件类型是否支持预览
     */
    @GetMapping("/support-preview")
    public ResponseEntity<Map<String, Object>> checkSupportPreview(@RequestParam String fileName) {
        String fileType = filePreviewService.extractFileType(fileName);
        FilePreviewVO vo = filePreviewService.buildPreviewVO(null, fileName, null, null);
        
        Map<String, Object> result = new HashMap<>();
        result.put("fileType", fileType);
        result.put("supportPreview", vo.getSupportPreview());
        result.put("previewType", vo.getPreviewType());
        result.put("mimeType", vo.getMimeType());
        
        return ResponseEntity.ok(result);
    }

    /**
     * 获取支持的预览文件类型列表
     */
    @GetMapping("/supported-types")
    public ResponseEntity<Map<String, Object>> getSupportedTypes() {
        Map<String, Object> result = new HashMap<>();
        
        result.put("image", new String[]{"png", "jpg", "jpeg", "gif", "bmp", "webp", "svg"});
        result.put("pdf", new String[]{"pdf"});
        result.put("excel", new String[]{"xls", "xlsx"});
        result.put("word", new String[]{"doc", "docx"});
        result.put("ppt", new String[]{"ppt", "pptx"});
        result.put("dwg", new String[]{"dwg", "dxf"});
        result.put("media", new String[]{"mp3", "mp4", "wav", "ogg", "webm"});
        result.put("text", new String[]{"txt", "md", "json", "xml", "html", "css", "js"});
        
        return ResponseEntity.ok(result);
    }
}
