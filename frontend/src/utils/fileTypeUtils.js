/**
 * 文件类型工具函数
 */

// 图片类文件（浏览器原生支持）
export const IMAGE_TYPES = ['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp', 'svg', 'ico']

// PDF文件
export const PDF_TYPES = ['pdf']

// Excel文件
export const EXCEL_TYPES = ['xls', 'xlsx']

// Word文件
export const WORD_TYPES = ['doc', 'docx']

// PPT文件
export const PPT_TYPES = ['ppt', 'pptx']

// DWG文件
export const DWG_TYPES = ['dwg', 'dxf']

// Markdown文件
export const MD_TYPES = ['md', 'markdown']

// 音视频文件
export const MEDIA_TYPES = ['mp3', 'mp4', 'wav', 'ogg', 'webm', 'avi', 'mov']

// 纯文本文件
export const TEXT_TYPES = ['txt', 'log', 'json', 'xml', 'html', 'css', 'js', 'java', 'py', 'sql']

/**
 * 提取文件扩展名
 * @param {string} fileName 
 * @returns {string}
 */
export function extractFileType(fileName) {
  if (!fileName) return ''
  const lastDotIndex = fileName.lastIndexOf('.')
  if (lastDotIndex === -1 || lastDotIndex === fileName.length - 1) {
    return ''
  }
  return fileName.substring(lastDotIndex + 1).toLowerCase()
}

/**
 * 判断预览类型
 * @param {string} fileType 
 * @returns {string} previewType: image | pdf | excel | word | dwg | media | text | download
 */
export function determinePreviewType(fileType) {
  if (!fileType) return 'download'

  if (IMAGE_TYPES.includes(fileType)) return 'image'
  if (PDF_TYPES.includes(fileType)) return 'pdf'
  if (EXCEL_TYPES.includes(fileType)) return 'excel'
  if (WORD_TYPES.includes(fileType)) return 'word'
  if (PPT_TYPES.includes(fileType)) return 'office'
  if (DWG_TYPES.includes(fileType)) return 'dwg'
  if (MD_TYPES.includes(fileType)) return 'markdown'
  if (MEDIA_TYPES.includes(fileType)) return 'media'
  if (TEXT_TYPES.includes(fileType)) return 'text'

  return 'download'
}

/**
 * 获取文件图标
 * @param {string} fileName 
 * @returns {string} emoji图标
 */
export function getFileIcon(fileName) {
  const fileType = extractFileType(fileName)
  
  const iconMap = {
    pdf: '📄',
    png: '🖼️',
    jpg: '🖼️',
    jpeg: '🖼️',
    gif: '🖼️',
    xls: '📊',
    xlsx: '📊',
    doc: '📝',
    docx: '📝',
    ppt: '📽️',
    pptx: '📽️',
    dwg: '📐',
    dxf: '📐',
    mp3: '🎵',
    mp4: '🎬',
    zip: '📦',
    rar: '📦',
    txt: '📃',
    md: '📋'
  }
  
  return iconMap[fileType] || '📁'
}

/**
 * 格式化文件大小
 * @param {number} bytes 
 * @returns {string}
 */
export function formatFileSize(bytes) {
  if (!bytes || bytes === 0) return '0 B'
  
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let unitIndex = 0
  let size = bytes
  
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024
    unitIndex++
  }
  
  return size.toFixed(unitIndex > 0 ? 2 : 0) + ' ' + units[unitIndex]
}

/**
 * 获取文件MIME类型
 * @param {string} fileType 
 * @returns {string}
 */
export function getMimeType(fileType) {
  const mimeMap = {
    pdf: 'application/pdf',
    png: 'image/png',
    jpg: 'image/jpeg',
    jpeg: 'image/jpeg',
    gif: 'image/gif',
    bmp: 'image/bmp',
    webp: 'image/webp',
    svg: 'image/svg+xml',
    xls: 'application/vnd.ms-excel',
    xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    doc: 'application/msword',
    docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    ppt: 'application/vnd.ms-powerpoint',
    pptx: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
    dwg: 'image/vnd.dwg',
    dxf: 'image/vnd.dxf',
    txt: 'text/plain',
    mp3: 'audio/mpeg',
    mp4: 'video/mp4',
    wav: 'audio/wav'
  }
  
  return mimeMap[fileType] || 'application/octet-stream'
}
