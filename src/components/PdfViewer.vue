<template>
  <div class="pdf-viewer">
    <div v-if="loading" class="pdf-loading">
      <el-icon class="is-loading" :size="24"><Loading /></el-icon>
      <span>正在加载 PDF 并搜索定位...</span>
    </div>
    <div v-else-if="error" class="pdf-error">{{ error }}</div>
    <div v-else ref="containerRef" class="pdf-container">
      <div v-for="pageNum in pageCount" :key="pageNum" class="pdf-page-wrapper">
        <canvas class="pdf-canvas" />
        <div class="pdf-text-layer" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, nextTick } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import * as pdfjsLib from 'pdfjs-dist'

// Worker 配置
pdfjsLib.GlobalWorkerOptions.workerSrc = new URL(
  'pdfjs-dist/build/pdf.worker.min.mjs',
  import.meta.url
).toString()

const props = defineProps<{
  pdfUrl: string
  highlightText: string
}>()

const loading = ref(true)
const error = ref('')
const containerRef = ref<HTMLElement>()
const pageCount = ref(0)
const matchPage = ref(0)

async function loadPdf() {
  loading.value = true
  error.value = ''

  try {
    const doc = await pdfjsLib.getDocument({ url: props.pdfUrl, cMapUrl: 'https://cdn.jsdelivr.net/npm/pdfjs-dist@5.4.449/cmaps/', cMapPacked: true }).promise
    pageCount.value = doc.numPages
    // 等待 Vue 渲染 canvas + text layer DOM
    await nextTick()
    await new Promise(r => setTimeout(r, 100))

    // 先搜索所有页找到匹配页
    let foundPage = 0
    if (props.highlightText.trim()) {
      for (let i = 1; i <= doc.numPages; i++) {
        const page = await doc.getPage(i)
        const textContent = await page.getTextContent()
        const pageText = textContent.items.map((item: any) => item.str).join(' ')
        const fn = (t: string) => t.replace(/\s+/g, '')
        if (fn(pageText).includes(fn(props.highlightText.trim().substring(0, 50)))) {
          foundPage = i
          break
        }
      }
      matchPage.value = foundPage
    }

    // 渲染所有页
    const canvasEls = containerRef.value?.querySelectorAll('.pdf-canvas')
    const textLayerEls = containerRef.value?.querySelectorAll('.pdf-text-layer')

    for (let i = 1; i <= doc.numPages; i++) {
      const page = await doc.getPage(i)
      const viewport = page.getViewport({ scale: 1.5 })
      const canvas = canvasEls?.[i - 1] as HTMLCanvasElement | undefined
      const textLayerDiv = textLayerEls?.[i - 1] as HTMLElement | undefined

      if (!canvas) continue

      canvas.height = viewport.height
      canvas.width = viewport.width
      canvas.style.width = '100%'
      canvas.style.height = 'auto'

      const ctx = canvas.getContext('2d')
      await page.render({ canvasContext: ctx, viewport }).promise

      // 手动构建文本层（PDF.js 5.x 不再导出 renderTextLayer）
      if (textLayerDiv) {
        const textContent = await page.getTextContent()

        textLayerDiv.style.height = viewport.height + 'px'
        textLayerDiv.style.width = viewport.width + 'px'
        textLayerDiv.innerHTML = ''

        // 文本层需要 CSS transform 来匹配 canvas 缩放
        // canvas scale = 1.5, 所以 scale down 到 1/1.5 ≈ 0.6667
        textLayerDiv.style.setProperty('--scale-factor', String(1.5))

        const fn = (t: string) => t.replace(/\s+/g, '').replace(/[]/g, '')
        const searchNorm = fn(props.highlightText.trim().substring(0, 200))
        const searchWords = searchNorm.replace(/(.{10})/g, '$1|').split('|').filter(s => s.length >= 5)

        let firstMatch: HTMLSpanElement | null = null
        let consecutive = 0

        textContent.items.forEach((item: any) => {
          const span = document.createElement('span')
          span.textContent = item.str
          // 使用 item.transform 计算位置（PDF.js 内部坐标系）
          const tx = item.transform
          // transform 是 [scaleX, skewY, skewX, scaleY, translateX, translateY]
          const left = tx[4]
          const top = tx[5] - item.height * 0.8
          const fontSize = Math.sqrt(tx[0] * tx[0] + tx[1] * tx[1])

          span.style.position = 'absolute'
          span.style.left = left + 'px'
          span.style.top = top + 'px'
          span.style.fontSize = fontSize + 'px'
          span.style.fontFamily = 'sans-serif'
          span.style.whiteSpace = 'pre'
          span.style.transformOrigin = 'top left'

          // 高亮匹配
          const spanNorm = fn(item.str)
          if (spanNorm && searchWords.some(w => spanNorm.includes(w)) || searchNorm.includes(spanNorm.substring(0, Math.min(20, spanNorm.length)))) {
            span.style.backgroundColor = '#fef08a'
            span.style.color = '#92400e'
            span.style.padding = '1px 2px'
            span.style.borderRadius = '2px'
            consecutive++
            if (!firstMatch && consecutive >= 3 && matchPage.value === i) {
              firstMatch = span
            }
          } else if (spanNorm) {
            consecutive = 0
          }

          textLayerDiv.appendChild(span)
        })

        if (firstMatch) {
          firstMatch.id = 'pdf-highlight-anchor'
          firstMatch.style.scrollMarginTop = '80px'
        }
      }
    }

    // 滚动到匹配页
    if (foundPage > 0) {
      await nextTick()
      setTimeout(() => {
        const anchor = document.getElementById('pdf-highlight-anchor')
        if (anchor) {
          anchor.scrollIntoView({ behavior: 'smooth', block: 'center' })
        } else {
          const pageEl = containerRef.value?.children[foundPage - 1]
          if (pageEl) pageEl.scrollIntoView({ behavior: 'smooth', block: 'start' })
        }
      }, 500)
    }
  } catch (e: any) {
    error.value = e?.message || 'PDF 加载失败'
  } finally {
    loading.value = false
  }
}

/** 从块文本中提取搜索关键词 */
function extractSearchTerms(text: string): string[] {
  if (!text) return []
  // 取前200字符，分段（按换行/句号），取长度>=5的片段
  const short = text.trim().substring(0, 200)
  const segments = short
    .split(/[\n。！？]/)
    .map(s => s.trim())
    .filter(s => s.length >= 5)
  // 也加入前80字符作为一个搜索词
  const head = short.substring(0, Math.min(80, short.length))
  return [...new Set([head, ...segments])]
}

onMounted(loadPdf)
watch(() => props.pdfUrl, loadPdf)
</script>

<style scoped>
.pdf-viewer {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: #525659;
}
.pdf-loading {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #fff;
  font-size: 14px;
}
.pdf-error {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #f56c6c;
  font-size: 14px;
  padding: 24px;
}
.pdf-container {
  flex: 1;
  overflow-y: auto;
  padding: 16px 0;
}
.pdf-page-wrapper {
  position: relative;
  margin-bottom: 16px;
  display: flex;
  justify-content: center;
}
.pdf-canvas {
  display: block;
  max-width: 95%;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
}
.pdf-text-layer {
  position: absolute;
  top: 0;
  left: 50%;
  overflow: hidden;
  opacity: 0.2;
  pointer-events: none;
}
</style>
