<template>
  <div class="file-preview-container">
    <!-- 加载状态 -->
    <div v-if="loading" class="preview-loading">
      <div class="spinner"></div>
      <p>加载中...</p>
    </div>

    <!-- 错误状态 -->
    <div v-else-if="error" class="preview-error">
      <p>❌ {{ error }}</p>
      <button @click="retry" class="retry-btn">重试</button>
    </div>

    <!-- 不支持预览 -->
    <div v-else-if="!supportPreview" class="preview-unsupported">
      <div class="file-icon">{{ fileIcon }}</div>
      <p class="file-name">{{ fileName }}</p>
      <p class="file-size">{{ formattedFileSize }}</p>
      <p class="unsupported-tip">该文件类型暂不支持预览</p>
      <button @click="downloadFile" class="download-btn">下载文件</button>
    </div>

    <!-- 图片预览 -->
    <div v-else-if="previewType === 'image'" class="preview-image">
      <img :src="fileUrl" :alt="fileName" @error="handleImageError" />
    </div>

    <!-- PDF预览 -->
    <div v-else-if="previewType === 'pdf'" class="preview-pdf">
      <iframe 
        :src="pdfJsUrl" 
        class="pdf-frame"
        frameborder="0"
      ></iframe>
    </div>

    <!-- Excel预览 -->
    <div v-else-if="previewType === 'excel'" class="preview-excel">
      <div v-html="excelHtml"></div>
      <div v-if="excelLoading" class="preview-loading">
        <div class="spinner"></div>
        <p>解析Excel中...</p>
      </div>
    </div>

    <!-- Word预览 -->
    <div v-else-if="previewType === 'word'" class="preview-word">
      <div v-html="wordHtml"></div>
      <div v-if="wordLoading" class="preview-loading">
        <div class="spinner"></div>
        <p>解析Word中...</p>
      </div>
    </div>

    <!-- DWG预览 -->
    <div v-else-if="previewType === 'dwg'" class="preview-dwg">
      <div v-if="dwgLoading" class="preview-loading">
        <div class="spinner"></div>
        <p>CAD文件转换中，请稍候...</p>
      </div>
      <img 
        v-else-if="dwgPreviewUrl" 
        :src="dwgPreviewUrl" 
        :alt="fileName" 
        class="dwg-image"
      />
      <div v-else class="preview-error">
        <p>CAD预览生成失败</p>
        <button @click="downloadFile" class="download-btn">下载CAD文件</button>
      </div>
    </div>

    <!-- 音视频预览 -->
    <div v-else-if="previewType === 'media'" class="preview-media">
      <video 
        v-if="isVideo" 
        :src="fileUrl" 
        controls 
        class="media-video"
      ></video>
      <audio 
        v-else 
        :src="fileUrl" 
        controls 
        class="media-audio"
      ></audio>
    </div>

    <!-- 文本预览 -->
    <div v-else-if="previewType === 'text'" class="preview-text">
      <pre>{{ textContent }}</pre>
    </div>

    <!-- Markdown预览 -->
    <div v-else-if="previewType === 'markdown'" class="preview-markdown">
      <div v-html="markdownHtml"></div>
    </div>

    <!-- 下载按钮（通用） -->
    <div v-if="supportPreview && previewType !== 'image'" class="preview-footer">
      <button @click="downloadFile" class="download-btn">
        📥 下载文件
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import * as XLSX from 'xlsx'
import mammoth from 'mammoth'
import { marked } from 'marked'
import { determinePreviewType, extractFileType, formatFileSize, getFileIcon } from '../utils/fileTypeUtils'
import axios from 'axios'

const props = defineProps({
  /** 文件ID */
  fileId: {
    type: [String, Number],
    default: null
  },
  /** 文件名 */
  fileName: {
    type: String,
    required: true
  },
  /** 文件URL */
  fileUrl: {
    type: String,
    required: true
  },
  /** 文件大小（字节） */
  fileSize: {
    type: Number,
    default: 0
  },
  /** 后端API基础URL */
  apiBaseUrl: {
    type: String,
    default: '/api'
  },
  /** PDF.js CDN路径 */
  pdfJsCdn: {
    type: String,
    default: 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174'
  }
})

const emit = defineEmits(['error', 'loaded'])

const loading = ref(false)
const error = ref(null)
const excelHtml = ref('')
const wordHtml = ref('')
const textContent = ref('')
const markdownHtml = ref('')
const excelLoading = ref(false)
const wordLoading = ref(false)
const dwgPreviewUrl = ref(null)
const dwgLoading = ref(false)
const imageError = ref(false)

// 计算属性
const fileType = computed(() => extractFileType(props.fileName))
const previewType = computed(() => determinePreviewType(fileType.value))
const supportPreview = computed(() => previewType.value !== 'download')
const isVideo = computed(() => ['mp4', 'avi', 'mov', 'webm'].includes(fileType.value))
const formattedFileSize = computed(() => formatFileSize(props.fileSize))
const fileIcon = computed(() => getFileIcon(props.fileName))

// PDF.js URL
const pdfJsUrl = computed(() => {
  const encodedUrl = encodeURIComponent(props.fileUrl)
  return `${props.pdfJsCdn}/web/viewer.html?file=${encodedUrl}`
})

const normalizedApiBaseUrl = computed(() => {
  const baseUrl = props.apiBaseUrl.replace(/\/+$/, '')
  return baseUrl.endsWith('/api') ? baseUrl : `${baseUrl}/api`
})

/**
 * 处理图片加载错误
 */
function handleImageError() {
  imageError.value = true
  error.value = '图片加载失败'
}

/**
 * 重试
 */
function retry() {
  error.value = null
  loadPreview()
}

/**
 * 下载文件
 */
function downloadFile() {
  const link = document.createElement('a')
  link.href = props.fileUrl
  link.download = props.fileName
  link.click()
}

/**
 * 加载预览内容
 */
async function loadPreview() {
  loading.value = false
  error.value = null
  imageError.value = false

  try {
    switch (previewType.value) {
      case 'excel':
        await loadExcel()
        break
      case 'word':
        await loadWord()
        break
      case 'text':
        await loadText()
        break
      case 'markdown':
        await loadMarkdown()
        break
      case 'dwg':
        await loadDwg()
        break
    }
    emit('loaded')
  } catch (e) {
    console.error('预览加载失败:', e)
    error.value = e.message || '预览加载失败'
    emit('error', e)
  } finally {
    loading.value = false
  }
}

/**
 * 加载Excel
 */
async function loadExcel() {
  excelLoading.value = true
  try {
    const response = await fetch(props.fileUrl)
    const arrayBuffer = await response.arrayBuffer()
    const workbook = XLSX.read(new Uint8Array(arrayBuffer), { type: 'array' })
    
    // 获取第一个sheet
    const firstSheetName = workbook.SheetNames[0]
    const worksheet = workbook.Sheets[firstSheetName]
    
    // 转换为HTML表格
    excelHtml.value = XLSX.utils.sheet_to_html(worksheet, { 
      editable: false,
      width: 1280
    })
  } finally {
    excelLoading.value = false
  }
}

/**
 * 加载Word
 */
async function loadWord() {
  wordLoading.value = true
  try {
    const response = await fetch(props.fileUrl)
    const arrayBuffer = await response.arrayBuffer()
    const result = await mammoth.convertToHtml({ arrayBuffer })
    wordHtml.value = result.value
  } finally {
    wordLoading.value = false
  }
}

/**
 * 加载文本文件
 */
async function loadText() {
  const response = await fetch(props.fileUrl)
  textContent.value = await response.text()
}

/**
 * 加载Markdown
 */
async function loadMarkdown() {
  const response = await fetch(props.fileUrl)
  const mdContent = await response.text()
  markdownHtml.value = marked(mdContent)
}

/**
 * 加载DWG预览
 */
async function loadDwg() {
  dwgLoading.value = true
  revokeDwgPreviewUrl()
  
  try {
    const response = await axios.get(`${normalizedApiBaseUrl.value}/file/dwg/preview`, {
      params: {
        fileUrl: props.fileUrl,
        format: 'SVG'
      },
      responseType: 'blob',
      headers: {
        Accept: 'image/svg+xml'
      }
    })
    
    dwgPreviewUrl.value = URL.createObjectURL(response.data)
  } catch (e) {
    console.error('DWG预览生成失败:', e)
    throw new Error('DWG预览生成失败，请下载后查看')
  } finally {
    dwgLoading.value = false
  }
}

function revokeDwgPreviewUrl() {
  if (dwgPreviewUrl.value) {
    URL.revokeObjectURL(dwgPreviewUrl.value)
    dwgPreviewUrl.value = null
  }
}

// 监听fileUrl变化，重新加载预览
watch(() => props.fileUrl, loadPreview, { immediate: true })

onBeforeUnmount(() => {
  revokeDwgPreviewUrl()
})
</script>

<style scoped>
.file-preview-container {
  width: 100%;
  min-height: 400px;
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
}

.preview-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  color: #666;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 3px solid #f3f3f3;
  border-top: 3px solid #3498db;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.preview-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  color: #e74c3c;
}

.retry-btn,
.download-btn {
  margin-top: 16px;
  padding: 8px 24px;
  background: #3498db;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
}

.retry-btn:hover,
.download-btn:hover {
  background: #2980b9;
}

.preview-unsupported {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  padding: 40px;
  text-align: center;
}

.file-icon {
  font-size: 64px;
  margin-bottom: 16px;
}

.file-name {
  font-size: 18px;
  font-weight: bold;
  color: #333;
  margin-bottom: 8px;
  word-break: break-all;
}

.file-size {
  color: #999;
  font-size: 14px;
}

.unsupported-tip {
  color: #999;
  margin: 16px 0;
}

.preview-image {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 20px;
  background: #f5f5f5;
  max-height: 80vh;
  overflow: auto;
}

.preview-image img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.preview-pdf {
  width: 100%;
  height: 80vh;
}

.pdf-frame {
  width: 100%;
  height: 100%;
}

.preview-excel,
.preview-word {
  padding: 20px;
  overflow: auto;
  max-height: 80vh;
}

.preview-excel :deep(table),
.preview-word :deep(table) {
  border-collapse: collapse;
  width: 100%;
  font-size: 14px;
}

.preview-excel :deep(th),
.preview-excel :deep(td),
.preview-word :deep(th),
.preview-word :deep(td) {
  border: 1px solid #ddd;
  padding: 8px;
  text-align: left;
}

.preview-excel :deep(th),
.preview-word :deep(th) {
  background: #f5f5f5;
  font-weight: bold;
}

.preview-dwg {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 20px;
  background: #f5f5f5;
  min-height: 400px;
}

.dwg-image {
  max-width: 100%;
  max-height: 80vh;
  object-fit: contain;
}

.preview-media {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 20px;
  background: #000;
  min-height: 400px;
}

.media-video {
  max-width: 100%;
  max-height: 80vh;
}

.media-audio {
  width: 100%;
  max-width: 600px;
}

.preview-text {
  padding: 20px;
  overflow: auto;
  max-height: 80vh;
  background: #f5f5f5;
}

.preview-text pre {
  margin: 0;
  white-space: pre-wrap;
  word-wrap: break-word;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 13px;
  line-height: 1.5;
}

.preview-markdown {
  padding: 20px;
  overflow: auto;
  max-height: 80vh;
  line-height: 1.6;
}

.preview-footer {
  padding: 16px;
  border-top: 1px solid #eee;
  display: flex;
  justify-content: center;
}
</style>
