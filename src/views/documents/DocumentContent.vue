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
      </div>
    </div>

    <div class="doc-meta" v-if="doc">
      <span>类型: {{ doc.fileType?.toUpperCase() }}</span>
      <span>大小: {{ formatFileSize(doc.fileSize) }}</span>
      <span>分块数: {{ doc.chunkCount || 0 }}</span>
      <span>上传时间: {{ formatDate(doc.uploadAt || doc.createdAt) }}</span>
    </div>

    <div class="content-container" v-loading="loading">
      <div v-if="error" class="error-msg">
        <el-icon><WarningFilled /></el-icon> {{ error }}
      </div>
      <div v-else-if="!loading && renderedHtml" class="markdown-body" v-html="renderedHtml"></div>
      <el-empty v-else-if="!loading && !error" description="暂无内容" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft, Download, WarningFilled } from '@element-plus/icons-vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { fileApi } from '@/api/modules/files'
import { formatDate, formatFileSize } from '@/utils/format'
import { DOCUMENT_STATUS_MAP } from '@/utils/constants'
import type { FileVO } from '@/api/types/file'
import { ElMessage } from 'element-plus'

const route = useRoute()
const id = Number(route.params.id)

const doc = ref<FileVO | null>(null)
const content = ref('')
const loading = ref(false)
const error = ref('')

const renderedHtml = computed(() => {
  if (!content.value) return ''
  const raw = marked.parse(content.value, { breaks: true }) as string
  return DOMPurify.sanitize(raw)
})

onMounted(async () => {
  loading.value = true
  try {
    const [docRes, contentRes] = await Promise.all([
      fileApi.detail(id),
      fileApi.getContent(id),
    ])
    doc.value = docRes
    content.value = contentRes
  } catch (e: any) {
    error.value = e?.message || '加载文档内容失败'
  } finally {
    loading.value = false
  }
})

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
</style>
