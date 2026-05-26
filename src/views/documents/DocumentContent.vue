<template>
  <div class="page-container">
    <div class="page-container__header">
      <div class="header-left">
        <el-button @click="$router.back()" text>
          <el-icon><ArrowLeft /></el-icon> 返回
        </el-button>
        <h2>{{ doc?.fileName || '文档浏览' }}</h2>
        <el-tag v-if="doc" :type="DOCUMENT_STATUS_MAP[doc.status]?.type || 'info'" size="small">
          {{ DOCUMENT_STATUS_MAP[doc.status]?.label || doc.status }}
        </el-tag>
      </div>
      <div class="header-right">
        <el-button v-if="doc" @click="handleDownload" :loading="loading">
          <el-icon><Download /></el-icon> 下载原文件
        </el-button>
        <el-button v-if="doc && !isTextType" @click="openRawFile">
          <el-icon><View /></el-icon> 查看原文件
        </el-button>
      </div>
    </div>

    <div class="doc-meta" v-if="doc">
      <span>类型: {{ doc.fileType?.toUpperCase() }}</span>
      <span>大小: {{ formatFileSize(doc.fileSize) }}</span>
      <span>分块数: {{ doc.chunkCount || 0 }}</span>
      <span>上传时间: {{ formatDate(doc.uploadAt || doc.createdAt) }}</span>
    </div>

    <!-- 清洗后的 Markdown 内容 (默认视图) -->
    <div class="content-container" v-loading="loading">
      <div v-if="error" class="error-msg">
        <el-icon><WarningFilled /></el-icon> {{ error }}
      </div>
      <div v-else-if="!loading && renderedHtml" class="markdown-body" v-html="renderedHtml"></div>
      <div v-else-if="!loading && !error" class="empty-state">
        <el-icon :size="36"><Document /></el-icon>
        <p v-if="isProcessing">文档正在处理中，清洗内容尚未生成...</p>
        <p v-else>暂无清洗后的内容</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft, Download, WarningFilled, View, Document } from '@element-plus/icons-vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { fileApi } from '@/api/modules/files'
import { getAccessToken } from '@/utils/token'
import { formatDate, formatFileSize } from '@/utils/format'
import { DOCUMENT_STATUS_MAP } from '@/utils/constants'
import type { FileVO } from '@/api/types/file'
import { ElMessage } from 'element-plus'

const TEXT_TYPES = ['txt', 'md', 'markdown', 'csv', 'json', 'xml', 'html', 'htm', 'log']

const route = useRoute()
const id = Number(route.params.id)

const doc = ref<FileVO | null>(null)
const content = ref('')
const loading = ref(false)
const error = ref('')

const fileType = computed(() => doc.value?.fileType?.toLowerCase() || '')
const isTextType = computed(() => TEXT_TYPES.includes(fileType.value))
const isProcessing = computed(() => {
  const s = doc.value?.status
  return s === 'UPLOADED' || s === 'PARSING' || s === 'CLEANING' || s === 'PENDING_REVIEW'
})

const renderedHtml = computed(() => {
  if (!content.value) return ''
  const raw = marked.parse(content.value, { breaks: true }) as string
  return DOMPurify.sanitize(raw)
})

onMounted(async () => {
  loading.value = true
  try {
    doc.value = await fileApi.detail(id)
    content.value = await fileApi.getContent(id)
  } catch (e: any) {
    error.value = e?.message || '加载文档内容失败'
  } finally {
    loading.value = false
  }
})

function openRawFile() {
  const token = getAccessToken()
  const url = `${import.meta.env.VITE_API_BASE_URL}/files/${id}/raw?token=${encodeURIComponent(token || '')}`
  window.open(url, '_blank')
}

async function handleDownload() {
  if (!doc.value) return
  try {
    await fileApi.download(doc.value.id, doc.value.fileName)
  } catch {
    ElMessage.error('下载失败')
  }
}
</script>

<style scoped>
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.header-right {
  display: flex;
  gap: 8px;
}
.doc-meta {
  display: flex;
  gap: 24px;
  padding: 12px 16px;
  margin-bottom: 16px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 13px;
  color: #606266;
}
.content-container {
  min-height: 400px;
  padding: 24px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 4px;
}
.markdown-body {
  line-height: 1.8;
}
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  margin-top: 24px;
  margin-bottom: 12px;
}
.markdown-body :deep(p) {
  margin-bottom: 8px;
}
.markdown-body :deep(pre) {
  background: #f5f7fa;
  padding: 12px 16px;
  border-radius: 4px;
  overflow-x: auto;
}
.markdown-body :deep(code) {
  font-family: 'Courier New', monospace;
  font-size: 13px;
}
.markdown-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 12px 0;
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid #dcdfe6;
  padding: 8px 12px;
  text-align: left;
}
.markdown-body :deep(th) {
  background: #f5f7fa;
}
.error-msg {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #f56c6c;
  font-size: 15px;
}
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 0;
  color: #909399;
}
.empty-state p {
  margin-top: 12px;
  font-size: 14px;
}
</style>
