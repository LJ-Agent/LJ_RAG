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

  const textOnly = html.replace(/<[^>]+>/g, '')
  const normText = fn(textOnly)
  const normChunk = fn(trimmed)

  // 纯文本起止位置（原始坐标），-1 表示未找到
  let realStart = -1, realEnd = -1

  // 策略 1：标准化全文匹配 → 映射回原始坐标
  const nFull = normText.indexOf(normChunk)
  if (nFull !== -1) {
    let nc = 0
    for (let i = 0; i < textOnly.length; i++) {
      if (fn(textOnly[i])) nc++
      if (nc > nFull && realStart === -1) realStart = i
      if (nc >= nFull + normChunk.length) { realEnd = i + 1; break }
    }
  }

  // 策略 2：标准化头部 80 字匹配 + 向后延伸
  if (realStart === -1) {
    const head = normChunk.substring(0, Math.min(80, normChunk.length))
    const nHead = normText.indexOf(head)
    if (nHead !== -1) {
      // 在标准化空间中延伸
      let bestNEnd = nHead + head.length
      const remaining = normChunk.substring(head.length)
      let pi = nHead + head.length, ci = 0
      while (pi < normText.length && ci < remaining.length) {
        if (normText[pi] === remaining[ci]) { ci++; pi++; bestNEnd = pi }
        else if (ci > 8) break
        else { pi++; ci = 0; bestNEnd = pi - ci }
      }
      // 映射回原始坐标
      let nc = 0
      for (let i = 0; i < textOnly.length; i++) {
        if (fn(textOnly[i])) nc++
        if (nc > nHead && realStart === -1) realStart = i
        if (nc >= bestNEnd) { realEnd = i + 1; break }
      }
    }
  }

  // 策略 3：原始文本首 200 字符直接匹配（使用原始坐标）
  if (realStart === -1) {
    const raw = trimmed.substring(0, Math.min(200, trimmed.length))
    const ri = textOnly.indexOf(raw)
    if (ri !== -1) {
      realStart = ri
      realEnd = ri + raw.length
    }
  }

  if (realStart === -1) return html
  if (realEnd === -1) realEnd = textOnly.length

  // 在 HTML 中定位 realStart 和 realEnd（跨标签）
  let plainPos = 0, htmlPos = 0, inTag = false
  let startHtml = -1, endHtml = -1
  while (htmlPos < html.length) {
    if (html[htmlPos] === '<') inTag = true
    else if (html[htmlPos] === '>') inTag = false
    else if (!inTag) {
      if (plainPos === realStart) startHtml = htmlPos
      if (plainPos === realEnd) { endHtml = htmlPos; break }
      plainPos++
    }
    htmlPos++
  }
  if (startHtml === -1) return html
  if (endHtml === -1) endHtml = html.length

  return (
    html.substring(0, startHtml) +
    '<mark class="raw-chunk-highlight" id="raw-anchor">' +
    html.substring(startHtml, endHtml) +
    '</mark>' +
    html.substring(endHtml)
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
