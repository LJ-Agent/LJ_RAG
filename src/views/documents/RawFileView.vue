<template>
  <div class="raw-file-page">
    <div class="raw-file-header">
      <el-button @click="goBack" text>
        <el-icon><ArrowLeft /></el-icon> 返回
      </el-button>
      <span class="raw-file-title">
        <el-icon><Document /></el-icon>
        {{ docName }}
      </span>
      <el-button size="small" @click="openInNewTab" style="margin-left:auto">
        <el-icon><CopyDocument /></el-icon> 在新标签页中查看（含标黄定位）
      </el-button>
    </div>

    <div v-loading="loading" class="raw-file-body">
      <div v-if="error" class="raw-error">
        <el-result icon="error" title="加载失败" :sub-title="error">
          <template #extra><el-button type="primary" @click="load">重试</el-button></template>
        </el-result>
      </div>

      <!-- 文本文件：直接展示原文+标黄 -->
      <template v-else-if="!isBinary && !isPdf && content">
        <div ref="contentRef" class="text-scroll">
          <pre class="raw-text" v-html="highlightedContent"></pre>
        </div>
      </template>

      <!-- PDF：PDF.js 渲染原文件 + 标黄定位 -->
      <template v-else-if="isPdf">
        <div class="section-label">
          <el-icon><Document /></el-icon> 原文件（标黄处为块对应内容）
          <el-button size="small" @click="openRawFile" style="margin-left:auto">
            <el-icon><Download /></el-icon> 在新标签页打开原文件
          </el-button>
        </div>
        <PdfViewer :pdfUrl="pdfUrl" :highlightText="chunkText" />
      </template>

      <!-- 其他二进制文件：展示文本对照和标黄 -->
      <template v-else-if="isBinary">
        <div class="binary-notice">
          <el-icon :size="20"><WarningFilled /></el-icon>
          <span>此文档为二进制格式（Word/Excel等），无法在页面内直接展示。请打开原文件查看。</span>
          <el-button type="primary" size="small" @click="openRawFile">
            <el-icon><Document /></el-icon> 打开原文件
          </el-button>
        </div>
        <div class="section-label">
          <el-icon><Collection /></el-icon> 文本对照 · 标黄处为对应块内容
        </div>
        <div ref="contentRef" class="text-scroll">
          <pre v-if="highlightedContent" class="raw-text" v-html="highlightedContent"></pre>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Document, Download, Collection, WarningFilled, CopyDocument } from '@element-plus/icons-vue'
import { fileApi } from '@/api/modules/files'
import { getAccessToken } from '@/utils/token'
import PdfViewer from '@/components/PdfViewer.vue'

const route = useRoute()
const router = useRouter()
const docId = Number(route.params.id)
const chunkText = (route.query.chunkText as string) || ''

const loading = ref(false)
const docName = ref('')
const content = ref('')
const isPdf = ref(false)
const isBinary = ref(false)
const pdfUrl = ref('')
const highlightedContent = ref('')
const error = ref('')
const contentRef = ref<HTMLElement>()

function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}

function norm(s: string): string {
  return s.replace(/\s+/g, ' ').trim()
}

function mapPos(orig: string, normed: string, nPos: number): number {
  let oi = 0, ni = 0
  while (ni < nPos && oi < orig.length) {
    if (/\s/.test(orig[oi])) {
      while (oi < orig.length && /\s/.test(orig[oi])) oi++
      if (ni < nPos && normed[ni] === ' ') ni++
    } else { oi++; ni++ }
  }
  return oi
}

function buildHighlight(docText: string): string {
  const t = chunkText.trim()
  if (!t) return escapeHtml(docText)

  // 精确匹配
  let idx = docText.indexOf(t)
  if (idx !== -1) {
    const b = escapeHtml(docText.substring(0, idx))
    const m = escapeHtml(docText.substring(idx, idx + t.length))
    const a = escapeHtml(docText.substring(idx + t.length))
    return b + '<mark class="raw-chunk-highlight" id="raw-anchor">' + m + '</mark>' + a
  }

  // 标准化空白匹配
  const nd = norm(docText)
  const nt = norm(t)
  let nIdx = nd.indexOf(nt)
  if (nIdx !== -1) {
    const op = mapPos(docText, nd, nIdx)
    const oe = mapPos(docText, nd, nIdx + nt.length)
    const b = escapeHtml(docText.substring(0, op))
    const m = escapeHtml(docText.substring(op, oe))
    const a = escapeHtml(docText.substring(oe))
    return b + '<mark class="raw-chunk-highlight" id="raw-anchor">' + m + '</mark>' + a
  }

  // 截断匹配
  const strategies = [
    t.substring(0, Math.min(200, t.length)),
    t.substring(0, Math.min(100, t.length)),
    t.split(/[。！？\n]/)[0]?.trim(),
  ].filter(s => s && s.length >= 10)

  for (const s of strategies) {
    idx = docText.indexOf(s!)
    if (idx !== -1) {
      const endIdx = idx + t.length
      const b = escapeHtml(docText.substring(0, idx))
      const m = escapeHtml(docText.substring(idx, Math.min(endIdx, docText.length)))
      const a = escapeHtml(docText.substring(Math.min(endIdx, docText.length)))
      return b + '<mark class="raw-chunk-highlight" id="raw-anchor">' + m + '</mark>' + a
    }
    nIdx = nd.indexOf(norm(s!))
    if (nIdx !== -1) {
      const op = mapPos(docText, nd, nIdx)
      const oe = mapPos(docText, nd, nIdx + t.length)
      const b = escapeHtml(docText.substring(0, op))
      const m = escapeHtml(docText.substring(op, Math.min(oe, docText.length)))
      const a = escapeHtml(docText.substring(Math.min(oe, docText.length)))
      return b + '<mark class="raw-chunk-highlight" id="raw-anchor">' + m + '</mark>' + a
    }
  }

  return '<p style="color:#f56c6c;margin-bottom:8px;">（未能精确定位块内容在原文中的位置）</p>' + escapeHtml(docText)
}

function goBack() {
  if (window.history.length > 1) router.back()
  else window.close()
}

function openInNewTab() {
  // 在新标签页中打开 RawFileView（含标黄定位），而非直接打开原始文件
  const resolved = router.resolve({
    path: `/documents/${docId}/raw-view`,
    query: { chunkText: chunkText },
  })
  window.open(resolved.href, '_blank')
}

function openRawFile() {
  // 直接打开原始文件（用于 PDF 等二进制文件）
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

    // 构建原文件 URL（含 JWT）
    const token = getAccessToken()
    pdfUrl.value = `${import.meta.env.VITE_API_BASE_URL}/files/${docId}/raw?token=${encodeURIComponent(token || '')}`

    try {
      const rawText = await fileApi.getRawContent(docId)
      const hasNull = rawText.indexOf(String.fromCharCode(0)) !== -1
      const sample = rawText.substring(0, 2000)
      const nonPrintable = sample.replace(/[\x20-\x7E一-鿿　-〿\n\r\t]/g, '')
      const isHighBin = sample.length > 0 && nonPrintable.length > sample.length * 0.15

      if (rawText.startsWith('%PDF')) {
        // PDF：用 PDF.js 渲染
        isPdf.value = true
      } else if (hasNull || isHighBin) {
        // 其他二进制文件
        isBinary.value = true
        const cleaned = await fileApi.getContent(docId)
        content.value = cleaned
      } else {
        // 文本文件：直接展示原文
        content.value = rawText
      }
    } catch {
      const cleaned = await fileApi.getContent(docId)
      content.value = cleaned
    }

    highlightedContent.value = buildHighlight(content.value)
    await nextTick()
    // 多帧延迟确保 v-html 渲染和布局完成后再滚动
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        setTimeout(() => {
          const el = document.getElementById('raw-anchor')
          if (el) {
            // 先设置父容器滚动位置
            const scrollParent = el.closest('.text-scroll') as HTMLElement | null
            if (scrollParent) {
              scrollParent.scrollTop = Math.max(0, el.offsetTop - scrollParent.clientHeight / 3)
            }
            // 再确保整个元素在视口内
            el.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
          }
        }, 300)
      })
    })
  } catch (e: any) {
    error.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.raw-file-page { display:flex; flex-direction:column; height:calc(100vh - 96px); background:#fff; }
.raw-file-header { display:flex; align-items:center; gap:12px; padding:12px 16px; border-bottom:1px solid #e4e7ed; }
.raw-file-title { font-size:16px; font-weight:600; display:flex; align-items:center; gap:6px; }
.raw-file-body { flex:1; overflow:hidden; display:flex; flex-direction:column; min-height:0; }
.text-scroll { height:100%; overflow-y:auto; }
.raw-text { margin:0; padding:20px 24px; white-space:pre-wrap; word-break:break-word; line-height:1.9; font-size:15px; color:#303133; font-family:inherit; }
.raw-error { display:flex; justify-content:center; padding-top:80px; }

.section-label { font-weight:600; font-size:13px; color:#303133; padding:8px 16px; background:#f5f7fa; border-bottom:1px solid #ebeef5; display:flex; align-items:center; gap:6px; flex-shrink:0; }
.binary-notice { display:flex; align-items:center; gap:10px; padding:16px 20px; background:#fdf6ec; border-bottom:1px solid #faecd8; color:#e6a23c; font-size:14px; }
.binary-notice .el-button { margin-left:auto; flex-shrink:0; }
</style>

<style>
mark.raw-chunk-highlight {
  background: #fef08a;
  color: #92400e;
  padding: 2px 4px;
  border-radius: 2px;
  scroll-margin-top: 60px;
}
</style>
