<template>
  <div class="chunk-detail-page">
    <div class="chunk-detail-header">
      <el-button @click="$router.back()" text>
        <el-icon><ArrowLeft /></el-icon> 返回
      </el-button>
      <span class="chunk-detail-title">块详情</span>
    </div>

    <el-card v-loading="loading" class="chunk-detail-card">
      <template v-if="error">
        <el-result icon="error" title="加载失败" :sub-title="error">
          <template #extra>
            <el-button type="primary" @click="load">重新加载</el-button>
            <el-button @click="$router.back()">返回</el-button>
          </template>
        </el-result>
      </template>

      <template v-else-if="chunk">
        <div class="chunk-meta">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="数据库ID">{{ chunk.id }}</el-descriptions-item>
            <el-descriptions-item label="业务ChunkID">{{ chunk.chunkId }}</el-descriptions-item>
            <el-descriptions-item label="所属文档ID">{{ chunk.documentId }}</el-descriptions-item>
            <el-descriptions-item label="块序号">{{ chunk.chunkIndex }}</el-descriptions-item>
            <el-descriptions-item label="字符数">{{ chunk.charCount }}</el-descriptions-item>
            <el-descriptions-item label="层级">{{ chunk.level }}</el-descriptions-item>
            <el-descriptions-item label="父块ID">{{ chunk.parentId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="chunk.status === 'ACTIVE' ? 'success' : chunk.status === 'DELETED' ? 'danger' : 'info'" size="small">
                {{ chunk.status }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="创建时间" :span="2">{{ chunk.createdAt }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="chunk-content-section">
          <div class="chunk-content-header">块内容</div>
          <div class="chunk-content-body markdown-body" v-html="renderMarkdown(chunk.content || '')"></div>
        </div>
      </template>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { chunkApi, type ChunkVO } from '@/api/modules/chunks'
import { sanitizeHtml } from '@/utils/sanitize'
import { marked } from 'marked'

const route = useRoute()
const chunkId = route.params.chunkId as string

const loading = ref(false)
const chunk = ref<ChunkVO | null>(null)
const error = ref('')

function renderMarkdown(text: string): string {
  const html = marked.parse(text || '') as string
  return sanitizeHtml(html)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    chunk.value = await chunkApi.getByChunkId(chunkId)
  } catch (e: any) {
    error.value = e?.message || '加载块详情失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.chunk-detail-page {
  padding: 16px;
  max-width: 960px;
  margin: 0 auto;
}
.chunk-detail-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.chunk-detail-title {
  font-size: 18px;
  font-weight: 600;
}
.chunk-detail-card {
  margin-bottom: 16px;
}
.chunk-meta {
  margin-bottom: 16px;
}
.chunk-content-section {
  border-top: 1px solid #ebeef5;
  padding-top: 16px;
}
.chunk-content-header {
  font-weight: 600;
  margin-bottom: 12px;
  font-size: 14px;
}
.chunk-content-body {
  background: #fafafa;
  padding: 16px;
  border-radius: 6px;
  border: 1px solid #ebeef5;
  max-height: 600px;
  overflow-y: auto;
  line-height: 1.8;
  font-size: 14px;
}
.markdown-body :deep(p) { margin: 0.5em 0; }
.markdown-body :deep(pre) { background: #282c34; color: #abb2bf; padding: 12px; border-radius: 4px; overflow-x: auto; font-size: 13px; }
.markdown-body :deep(code) { background: rgba(0,0,0,0.06); padding: 2px 6px; border-radius: 3px; font-size: 13px; }
.markdown-body :deep(pre code) { background: transparent; padding: 0; }
</style>
