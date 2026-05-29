<template>
  <div class="word-viewer">
    <div v-if="loading" class="doc-loading">
      <el-icon class="is-loading" :size="24"><Loading /></el-icon>
      <span>正在解析 Word 文档...</span>
    </div>
    <div v-else-if="error" class="doc-error">{{ error }}</div>
    <div v-else ref="contentRef" class="doc-content" v-html="highlightedHtml" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import { fileApi } from '@/api/modules/files'

const props = defineProps<{ docId: number; highlightText: string }>()

const loading = ref(true)
const error = ref('')
const highlightedHtml = ref('')
const contentRef = ref<HTMLElement>()

function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}

function buildHighlight(html: string, chunk: string): string {
  const trimmed = chunk.trim()
  if (!trimmed) return html

  // 在 HTML 文本中查找（忽略标签）
  const textOnly = html.replace(/<[^>]+>/g, '')
  const idx = textOnly.indexOf(trimmed)
  if (idx === -1) return html

  // 将文本位置映射回 HTML
  let textPos = 0, htmlPos = 0, inTag = false
  let startHtmlPos = -1, endHtmlPos = -1
  while (htmlPos < html.length && (startHtmlPos === -1 || endHtmlPos === -1)) {
    if (html[htmlPos] === '<') {
      inTag = true
    } else if (html[htmlPos] === '>') {
      inTag = false
    } else if (!inTag) {
      if (textPos === idx && startHtmlPos === -1) startHtmlPos = htmlPos
      if (textPos === idx + trimmed.length && endHtmlPos === -1) endHtmlPos = htmlPos
      textPos++
    }
    htmlPos++
  }
  if (endHtmlPos === -1) endHtmlPos = html.length

  return (
    html.substring(0, startHtmlPos) +
    '<mark class="raw-chunk-highlight" id="raw-anchor">' +
    html.substring(startHtmlPos, endHtmlPos) +
    '</mark>' +
    html.substring(endHtmlPos)
  )
}

onMounted(async () => {
  try {
    const buf = await fileApi.getRawArrayBuffer(props.docId)
    const mammoth = await import('mammoth')
    const result = await mammoth.convertToHtml({ arrayBuffer: buf })
    highlightedHtml.value = buildHighlight(result.value, props.highlightText)
    await nextTick()
    setTimeout(() => {
      const el = document.getElementById('raw-anchor')
      if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }, 300)
  } catch (e: any) {
    error.value = e?.message || 'Word 解析失败'
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.word-viewer { flex:1; overflow:hidden; display:flex; flex-direction:column; background:#fff; }
.doc-loading { flex:1; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:12px; color:#909399; }
.doc-error { flex:1; display:flex; align-items:center; justify-content:center; color:#f56c6c; padding:24px; }
.doc-content { flex:1; overflow-y:auto; padding:24px 32px; line-height:1.9; font-size:15px; color:#303133; }
.doc-content :deep(table) { border-collapse:collapse; margin:12px 0; width:100%; }
.doc-content :deep(td), .doc-content :deep(th) { border:1px solid #ddd; padding:6px 10px; }
</style>
