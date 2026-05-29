<template>
  <div class="raw-file-page">
    <div class="raw-file-header">
      <el-button @click="goBack" text>
        <el-icon><ArrowLeft /></el-icon> 返回
      </el-button>
      <span class="raw-file-title">
        <el-icon><Document /></el-icon>
        {{ docName }}
        <el-tag v-if="isBinary" type="warning" size="small" style="margin-left:8px">二进制文件，展示清洗后文本</el-tag>
      </span>
      <el-button size="small" @click="downloadRaw" style="margin-left:auto">
        <el-icon><Download /></el-icon> 下载原文件
      </el-button>
    </div>

    <div v-loading="loading" class="raw-file-body">
      <div v-if="error" class="raw-error">
        <el-result icon="error" title="加载失败" :sub-title="error">
          <template #extra>
            <el-button type="primary" @click="load">重试</el-button>
          </template>
        </el-result>
      </div>
      <template v-else-if="content">
        <div ref="contentRef" class="raw-content-wrapper">
          <pre class="raw-text" v-html="highlightedContent"></pre>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Document, Download } from '@element-plus/icons-vue'
import { fileApi } from '@/api/modules/files'
import { getAccessToken } from '@/utils/token'

const route = useRoute()
const router = useRouter()
const docId = Number(route.params.id)
const chunkText = (route.query.chunkText as string) || ''

const loading = ref(false)
const docName = ref('')
const content = ref('')
const isBinary = ref(false)
const highlightedContent = ref('')
const error = ref('')
const contentRef = ref<HTMLElement>()

function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}

function normalizeText(s: string): string {
  return s.replace(/\s+/g, ' ').trim()
}

function mapNormalizedPos(original: string, normalized: string, normPos: number): number {
  let origIdx = 0, normIdx = 0
  while (normIdx < normPos && origIdx < original.length) {
    if (/\s/.test(original[origIdx])) {
      while (origIdx < original.length && /\s/.test(original[origIdx])) origIdx++
      if (normIdx < normPos && normalized[normIdx] === ' ') normIdx++
    } else {
      origIdx++
      normIdx++
    }
  }
  return origIdx
}

function buildHighlight(docText: string): string {
  const trimmed = chunkText.trim()
  if (!trimmed) return escapeHtml(docText)

  // 策略1：精确匹配
  const exactIdx = docText.indexOf(trimmed)
  if (exactIdx !== -1) {
    const before = escapeHtml(docText.substring(0, exactIdx))
    const match = escapeHtml(docText.substring(exactIdx, exactIdx + trimmed.length))
    const after = escapeHtml(docText.substring(exactIdx + trimmed.length))
    return before + '<mark class="raw-chunk-highlight" id="raw-anchor">' + match + '</mark>' + after
  }

  // 策略2：标准化空白匹配
  const normDoc = normalizeText(docText)
  const normChunk = normalizeText(trimmed)
  const normIdx = normDoc.indexOf(normChunk)
  if (normIdx !== -1) {
    const origPos = mapNormalizedPos(docText, normDoc, normIdx)
    const origEnd = mapNormalizedPos(docText, normDoc, normIdx + normChunk.length)
    const before = escapeHtml(docText.substring(0, origPos))
    const match = escapeHtml(docText.substring(origPos, origEnd))
    const after = escapeHtml(docText.substring(origEnd))
    return before + '<mark class="raw-chunk-highlight" id="raw-anchor">' + match + '</mark>' + after
  }

  // 策略3：截断匹配
  const strategies = [
    trimmed.substring(0, Math.min(200, trimmed.length)),
    trimmed.substring(0, Math.min(100, trimmed.length)),
    trimmed.split(/[。！？\n]/)[0]?.trim(),
  ].filter(s => s && s.length >= 10)

  for (const search of strategies) {
    const idx = docText.indexOf(search!)
    if (idx !== -1) {
      const endIdx = idx + trimmed.length
      const before = escapeHtml(docText.substring(0, idx))
      const match = escapeHtml(docText.substring(idx, Math.min(endIdx, docText.length)))
      const after = escapeHtml(docText.substring(Math.min(endIdx, docText.length)))
      return before + '<mark class="raw-chunk-highlight" id="raw-anchor">' + match + '</mark>' + after
    }
    const nIdx = normDoc.indexOf(normalizeText(search!))
    if (nIdx !== -1) {
      const origPos = mapNormalizedPos(docText, normDoc, nIdx)
      const origEnd = mapNormalizedPos(docText, normDoc, nIdx + trimmed.length)
      const before = escapeHtml(docText.substring(0, origPos))
      const match = escapeHtml(docText.substring(origPos, Math.min(origEnd, docText.length)))
      const after = escapeHtml(docText.substring(Math.min(origEnd, docText.length)))
      return before + '<mark class="raw-chunk-highlight" id="raw-anchor">' + match + '</mark>' + after
    }
  }

  return '<p style="color:#f56c6c;margin-bottom:8px;">（未能精确定位块内容在原文中的位置）</p>' + escapeHtml(docText)
}

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    window.close()
  }
}

function downloadRaw() {
  const token = getAccessToken()
  const url = `${import.meta.env.VITE_API_BASE_URL}/files/${docId}/raw?token=${encodeURIComponent(token || '')}`
  window.open(url, '_blank')
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const docInfo = await fileApi.detail(docId).catch(() => null)
    docName.value = docInfo?.fileName || `文档#${docId}`

    try {
      // 优先加载原文件内容
      const rawText = await fileApi.getRawContent(docId)
      // 检测二进制
      const sample = rawText.substring(0, 2000)
      const nonPrintable = sample.replace(/[\x20-\x7E一-鿿　-〿＀-￯\n\r\t]/g, '')
      if (sample.length > 0 && nonPrintable.length > sample.length * 0.25) {
        isBinary.value = true
        const cleaned = await fileApi.getContent(docId)
        content.value = cleaned
      } else {
        content.value = rawText
      }
    } catch {
      // 降级到清洗后内容
      const cleaned = await fileApi.getContent(docId)
      content.value = cleaned
    }

    highlightedContent.value = buildHighlight(content.value)
    await nextTick()
    setTimeout(() => {
      const el = document.getElementById('raw-anchor')
      if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }, 300)
  } catch (e: any) {
    error.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.raw-file-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 96px);
  background: #fff;
}
.raw-file-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
}
.raw-file-title {
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
}
.raw-file-body {
  flex: 1;
  overflow: hidden;
}
.raw-content-wrapper {
  height: 100%;
  overflow-y: auto;
  padding: 0;
}
.raw-text {
  margin: 0;
  padding: 20px 24px;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.9;
  font-size: 15px;
  color: #303133;
  font-family: inherit;
}
.raw-error {
  display: flex;
  justify-content: center;
  padding-top: 80px;
}
</style>

<!-- 全局：标黄样式 -->
<style>
mark.raw-chunk-highlight {
  background: #fef08a;
  color: #92400e;
  padding: 2px 4px;
  border-radius: 2px;
  scroll-margin-top: 60px;
}
</style>
