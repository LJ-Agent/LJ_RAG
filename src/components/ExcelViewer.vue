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

  // 标准全文匹配
  let textIdx = normText.indexOf(normChunk)
  let matchLen = normChunk.length
  if (textIdx === -1) {
    // 首80字符匹配
    const head = normChunk.substring(0, Math.min(80, normChunk.length))
    textIdx = normText.indexOf(head)
    matchLen = head.length
  }
  if (textIdx === -1) {
    // 原始文本匹配
    const raw = trimmed.substring(0, Math.min(200, trimmed.length))
    textIdx = textOnly.indexOf(raw)
    matchLen = raw.length
  }
  if (textIdx === -1) return html

  // 映射回原始文本位置
  let origStart = 0, np = 0
  for (let i = 0; i < textOnly.length && np < textIdx; i++) {
    if (fn(textOnly[i])) { np++; origStart = i + 1 }
    else { origStart = i + 1 }
  }
  let origEnd = origStart; np = 0
  for (let i = origStart; i < textOnly.length && np < matchLen; i++) {
    if (fn(textOnly[i])) np++
    origEnd = i + 1
  }
  const matched = textOnly.substring(origStart, origEnd)
  const htmlIdx = html.indexOf(matched)
  if (htmlIdx === -1) return html

  return (
    html.substring(0, htmlIdx) +
    '<mark class="raw-chunk-highlight" id="raw-anchor">' +
    html.substring(htmlIdx, htmlIdx + matched.length) +
    '</mark>' +
    html.substring(htmlIdx + matched.length)
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
