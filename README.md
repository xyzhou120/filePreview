# 文件预览系统

一套支持多种文件类型的前端预览 + Spring Boot 后端解决方案。

---

## 支持的文件类型

| 类型 | 格式 | 预览方式 | 实现 |
|------|------|----------|------|
| 🖼️ 图片 | PNG, JPG, GIF, BMP, WebP, SVG | `<img>` | 前端原生 |
| 📄 PDF | PDF | pdf.js | 前端库 |
| 📊 Excel | XLS, XLSX | SheetJS (xlsx.js) | 前端库 |
| 📝 Word | DOC, DOCX | mammoth.js | 前端库 |
| 📐 CAD | DWG, DXF | SVG | 后端转换/渲染 |
| 🎬 音视频 | MP3, MP4, WAV | `<audio>`/`<video>` | 前端原生 |
| 📋 文本 | TXT, MD, JSON... | `<pre>`/marked | 前端库 |

---

## 快速开始

### 1. 后端部署

#### 依赖

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

#### 配置文件

```yaml
# application.yml
file:
  upload:
    dir: /data/uploads
  preview:
    base-url: http://your-server:8080

dwg:
  converter:
    path: ../resources/oda/ODAFileConverter.app/Contents/MacOS/ODAFileConverter
    output-dir: /tmp/dwg-preview
    out-format: SVG
    output-version: ACAD2018
    timeout-seconds: 300
```

#### API 接口

```bash
# 获取文件预览信息
GET /api/file/preview?fileName=test.xlsx&fileUrl=http://xxx/test.xlsx

# DWG/DXF预览转换，返回 image/svg+xml
GET /api/file/dwg/preview?fileUrl=http://xxx/test.dwg&format=SVG

# 文件上传
POST /api/file/upload
```

---

### 2. 前端集成

#### 安装依赖

```bash
npm install xlsx mammoth marked axios
```

#### 使用预览组件

```vue
<template>
  <FilePreview
    :file-id="1"
    :file-name="file.name"
    :file-url="file.url"
    :file-size="file.size"
    api-base-url="http://your-api-server:8080"
    @error="handleError"
    @loaded="handleLoaded"
  />
</template>

<script setup>
import FilePreview from './components/FilePreview.vue'
</script>
```

#### 直接调用预览工具函数

```javascript
import { 
  extractFileType,
  determinePreviewType,
  formatFileSize,
  getFileIcon 
} from './utils/fileTypeUtils.js'

const fileType = extractFileType('test.xlsx')  // 'xlsx'
const previewType = determinePreviewType('xlsx')  // 'excel'
const icon = getFileIcon('test.pdf')  // '📄'
const size = formatFileSize(1024 * 1024)  // '1.00 MB'
```

---

## 文件预览组件使用示例

### 基础用法

```vue
<FilePreview
  :file-name="attachment.fileName"
  :file-url="attachment.fileUrl"
  :file-size="attachment.fileSize"
/>
```

### 附件列表 + 预览弹窗

```vue
<template>
  <div class="attachment-list">
    <div 
      v-for="file in attachments" 
      :key="file.id"
      class="attachment-item"
      @click="openPreview(file)"
    >
      <span class="icon">{{ getFileIcon(file.fileName) }}</span>
      <span class="name">{{ file.fileName }}</span>
      <span class="size">{{ formatFileSize(file.fileSize) }}</span>
    </div>
  </div>

  <!-- 预览弹窗 -->
  <el-dialog v-model="previewVisible" title="文件预览" width="80%">
    <FilePreview
      v-if="previewFile"
      :file-name="previewFile.fileName"
      :file-url="previewFile.fileUrl"
      :file-size="previewFile.fileSize"
      @error="handlePreviewError"
    />
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import FilePreview from './components/FilePreview.vue'

const attachments = ref([
  { id: 1, fileName: '报告.pdf', fileUrl: '/files/report.pdf', fileSize: 1024000 },
  { id: 2, fileName: '数据.xlsx', fileUrl: '/files/data.xlsx', fileSize: 2048000 },
  { id: 3, fileName: '图纸.dwg', fileUrl: '/files/drawing.dwg', fileSize: 5120000 }
])

const previewVisible = ref(false)
const previewFile = ref(null)

function openPreview(file) {
  previewFile.value = file
  previewVisible.value = true
}
</script>
```

---

## DWG 预览配置

当前项目的 CAD 预览链路：

1. DXF 文件：后端直接解析常见实体并渲染为 SVG。
2. DWG 文件：后端先调用 ODA File Converter 转为 DXF，再渲染为 SVG。

内置 DXF 渲染器支持常见的 `LINE`、`CIRCLE`、`ARC`、`LWPOLYLINE`、`POLYLINE`、`TEXT/MTEXT`。复杂 CAD 特性（外部参照、填充、复杂块、三维对象等）需要接入更完整的 CAD 渲染引擎。

### 安装 ODA File Converter

1. 下载：https://www.opendesign.com/resources/oda-file-converter
2. 解压到指定目录（如 `/opt/ODAFileConverter/`）
3. 授权激活

```bash
# Linux 示例
wget https://downloads.opendesign.com/ODAFileConverter_lnxX64_7.8.tgz
tar -xzf ODAFileConverter_lnxX64_7.8.tgz -C /opt/
/opt/ODAFileConverter/ODAFileConverter -getActivationCode
```

### Docker 部署 ODA Converter（推荐）

```yaml
# docker-compose.yml
services:
  oda-converter:
    image: keking/oda-converter:latest
    container_name: oda-converter
    ports:
      - "8090:8090"
    volumes:
      - /data/oda:/data
    restart: unless-stopped
```

---

## 项目结构

```
file-preview-demo/
├── backend/
│   ├── src/main/java/com/example/filepreview/
│   │   ├── controller/
│   │   │   └── FileController.java      # 文件接口
│   │   ├── service/
│   │   │   ├── FilePreviewService.java  # 预览服务
│   │   │   └── DwgConvertService.java  # DWG转换服务
│   │   ├── entity/
│   │   │   ├── FilePreviewVO.java
│   │   │   └── PreviewTypeEnum.java
│   │   └── resources/
│   │       └── application.yml
│   └── pom.xml
│
├── frontend/
│   ├── src/
│   │   ├── components/
│   │   │   └── FilePreview.vue         # 预览组件
│   │   └── utils/
│   │       └── fileTypeUtils.js        # 工具函数
│   └── package.json
│
└── README.md
```

---

## 性能说明

| 文件类型 | 文件大小 | 预览加载时间 | 说明 |
|----------|----------|--------------|------|
| 图片 | < 10MB | < 1s | 直接加载 |
| PDF | < 50MB | 1-3s | pdf.js 流式加载 |
| Excel | < 10MB | 1-3s | 前端解析渲染 |
| Word | < 5MB | 1-2s | mammoth 解析 |
| DWG | < 50MB | 5-30s | ODA转DXF后渲染SVG，首次慢 |

---

## 常见问题

### Q: Excel 预览显示不完整？
A: SheetJS 对复杂公式和宏支持有限，大文件建议限制只显示前100行。

### Q: DWG 预览报 500 错误？
A: 检查 `dwg.converter.path` 是否指向可执行的 ODA File Converter。DXF 文件不依赖 ODA，DWG 文件需要 ODA 先转 DXF。

### Q: 跨域问题？
A: 后端需要配置 CORS，或通过 Nginx 代理。

---

*本工具由 斯佳丽(Scarlett) 生成*
