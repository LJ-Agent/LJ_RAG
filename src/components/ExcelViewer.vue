<template>
  <div class="excel-viewer">
    <div v-if="loading" class="doc-loading">
      <el-icon class="is-loading" :size="24"><Loading /></el-icon>
      <span>正在解析 Excel 表格...</span>
    </div>
    <div v-else-if="error" class="doc-error">{{ error }}</div>
    <div v-else ref="contentRef" class="excel-content" v-html="highlightedHtml" />
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

  let realStart = -1, realEnd = -1

  const nFull = normText.indexOf(normChunk)
  if (nFull !== -1) {
    let nc = 0
    for (let i = 0; i < textOnly.length; i++) {
      if (fn(textOnly[i])) nc++
      if (nc > nFull && realStart === -1) realStart = i
      if (nc >= nFull + normChunk.length) { realEnd = i + 1; break }
    }
  }
  if (realStart === -1) {
    const head = normChunk.substring(0, Math.min(80, normChunk.length))
    const nHead = normText.indexOf(head)
    if (nHead !== -1) {
      let bestNEnd = nHead + head.length
      const remaining = normChunk.substring(head.length)
      let pi = nHead + head.length, ci = 0
      while (pi < normText.length && ci < remaining.length) {
        if (normText[pi] === remaining[ci]) { ci++; pi++; bestNEnd = pi }
        else if (ci > 8) break
        else { pi++; ci = 0; bestNEnd = pi - ci }
      }
      let nc = 0
      for (let i = 0; i < textOnly.length; i++) {
        if (fn(textOnly[i])) nc++
        if (nc > nHead && realStart === -1) realStart = i
        if (nc >= bestNEnd) { realEnd = i + 1; break }
      }
    }
  }
  if (realStart === -1) {
    const raw = trimmed.substring(0, Math.min(200, trimmed.length))
    const ri = textOnly.indexOf(raw)
    if (ri !== -1) { realStart = ri; realEnd = ri + raw.length }
  }
  if (realStart === -1) return html
  if (realEnd === -1) realEnd = textOnly.length

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
    const XLSX = await import('xlsx')
    const wb = XLSX.read(buf, { type: 'array' })
    // 收集所有 sheet 的 HTML
    let html = ''
    wb.SheetNames.forEach((name: string, i: number) => {
      if (i > 0) html += '<hr style="margin:20px 0"><h3 style="margin:8px 0">' + name + '</h3>'
      html += XLSX.utils.sheet_to_html(wb.Sheets[name], { id: 'sheet-' + i })
    })
    highlightedHtml.value = buildHighlight(html, props.highlightText)
    await nextTick()
    setTimeout(() => {
      const el = document.getElementById('raw-anchor')
      if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }, 300)
  } catch (e: any) {
    error.value = e?.message || 'Excel 解析失败'
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.excel-viewer { flex:1; overflow:hidden; display:flex; flex-direction:column; background:#fff; }
.doc-loading { flex:1; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:12px; color:#909399; }
.doc-error { flex:1; display:flex; align-items:center; justify-content:center; color:#f56c6c; padding:24px; }
.excel-content { flex:1; overflow:auto; padding:16px 24px; }
.excel-content :deep(table) { border-collapse:collapse; font-size:13px; }
.excel-content :deep(td) { border:1px solid #d0d0d0; padding:4px 8px; min-width:60px; }
.excel-content :deep(th) { border:1px solid #d0d0d0; padding:4px 8px; background:#f5f5f5; }
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
