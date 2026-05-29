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

function fn(s: string): string { return s.replace(/\s+/g, '').replace(/[\f]/g, '') }

function buildHighlight(html: string, chunk: string): string {
  const trimmed = chunk.trim()
  if (!trimmed) return html

  // 提取纯文本并标准化
  const textOnly = html.replace(/<[^>]+>/g, '')
  const normText = fn(textOnly)
  const normChunk = fn(trimmed)

  // 多级匹配策略
  let textIdx = -1, matchLen = 0

  // 1) 标准化全文匹配
  const ni = normText.indexOf(normChunk)
  if (ni !== -1) { textIdx = ni; matchLen = normChunk.length }
  else {
    // 2) 首 80 字符标准化匹配
    const head = normChunk.substring(0, Math.min(80, normChunk.length))
    const hi = normText.indexOf(head)
    if (hi !== -1) { textIdx = hi; matchLen = head.length }
    else {
      // 3) 首 200 字符原始文本匹配
      const raw = trimmed.substring(0, Math.min(200, trimmed.length))
      const ri = textOnly.indexOf(raw)
      if (ri !== -1) { textIdx = ri; matchLen = raw.length }
    }
  }

  if (textIdx === -1) return html

  // 将标准化位置映射回原始文本位置
  let origStart = 0, normPos = 0
  for (let i = 0; i < textOnly.length && normPos < textIdx; i++) {
    if (fn(textOnly[i])) { normPos++; origStart = i + 1 }
    else { origStart = i + 1 }
  }
  let origEnd = origStart
  normPos = 0
  for (let i = origStart; i < textOnly.length && normPos < matchLen; i++) {
    if (fn(textOnly[i])) normPos++
    origEnd = i + 1
  }
  const matchedText = textOnly.substring(origStart, origEnd)

  // 在原始 HTML 中找到这段纯文本的位置
  const htmlIdx = html.indexOf(matchedText)
  if (htmlIdx === -1) return html

  return (
    html.substring(0, htmlIdx) +
    '<mark class="raw-chunk-highlight" id="raw-anchor">' +
    html.substring(htmlIdx, htmlIdx + matchedText.length) +
    '</mark>' +
    html.substring(htmlIdx + matchedText.length)
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

<style>
mark.raw-chunk-highlight {
  background: #fef08a;
  color: #92400e;
  padding: 2px 4px;
  border-radius: 2px;
  scroll-margin-top: 80px;
}
</style>
