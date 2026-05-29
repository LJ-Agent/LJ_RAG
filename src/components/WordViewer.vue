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

  // 提取纯文本
  const textOnly = html.replace(/<[^>]+>/g, '')
  const normText = fn(textOnly)
  const normChunk = fn(trimmed)

  // 在标准化文本中定位：全文匹配 → 头80字 → 头200字原文本
  let normStart = normText.indexOf(normChunk)
  let normEnd = normStart !== -1 ? normStart + normChunk.length : -1

  if (normStart === -1) {
    const head = normChunk.substring(0, Math.min(80, normChunk.length))
    normStart = normText.indexOf(head)
    if (normStart !== -1) {
      // 从头匹配位置向后尽可能延伸
      let bestEnd = normStart + head.length
      const remaining = normChunk.substring(head.length)
      let pi = normStart + head.length
      let ci = 0
      while (pi < normText.length && ci < remaining.length) {
        if (normText[pi] === remaining[ci]) { ci++; pi++; bestEnd = pi }
        else if (ci > 10) break  // 连续匹配超过10字后断开
        else { pi++; ci = 0; bestEnd = pi - ci }
      }
      normEnd = bestEnd
    } else {
      const raw = trimmed.substring(0, Math.min(200, trimmed.length))
      const ri = textOnly.indexOf(raw)
      if (ri !== -1) {
        normStart = ri
        normEnd = ri + raw.length
      }
    }
  }

  if (normStart === -1) return html

  // 将标准化位置映射回纯文本位置
  let realStart = 0, nc = 0
  for (let i = 0; i < textOnly.length; i++) {
    if (fn(textOnly[i])) nc++
    if (nc > normStart) { realStart = i; break }
  }
  let realEnd = textOnly.length
  nc = 0
  for (let i = realStart; i < textOnly.length; i++) {
    if (fn(textOnly[i])) nc++
    if (nc >= normEnd - normStart) { realEnd = i + 1; break }
  }

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
