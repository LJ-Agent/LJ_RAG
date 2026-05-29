<template>
  <div class="pdf-viewer">
    <div v-if="loading" class="pdf-loading">
      <el-icon class="is-loading" :size="24"><Loading /></el-icon>
      <span>正在加载 PDF 并搜索定位...</span>
    </div>
    <div v-else-if="error" class="pdf-error">{{ error }}</div>
    <div v-else ref="containerRef" class="pdf-container">
      <div v-for="pageNum in pageCount" :key="pageNum" class="pdf-page-wrapper">
        <canvas :ref="el => setCanvasRef(pageNum, el)" class="pdf-canvas" />
        <div :ref="el => setTextLayerRef(pageNum, el)" class="pdf-text-layer" />
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

const canvasRefs = new Map<number, any>()
const textLayerRefs = new Map<number, any>()

function setCanvasRef(pageNum: number, el: any) {
  if (el) canvasRefs.set(pageNum, el)
}
function setTextLayerRef(pageNum: number, el: any) {
  if (el) textLayerRefs.set(pageNum, el)
}

async function loadPdf() {
  loading.value = true
  error.value = ''

  try {
    const doc = await pdfjsLib.getDocument({ url: props.pdfUrl, cMapUrl: 'https://cdn.jsdelivr.net/npm/pdfjs-dist@5.4.449/cmaps/', cMapPacked: true }).promise
    pageCount.value = doc.numPages

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
    for (let i = 1; i <= doc.numPages; i++) {
      const page = await doc.getPage(i)
      const viewport = page.getViewport({ scale: 1.5 })
      const canvas = canvasRefs.get(i)
      const textLayerDiv = textLayerRefs.get(i)

      if (!canvas) continue

      canvas.height = viewport.height
      canvas.width = viewport.width
      canvas.style.width = '100%'
      canvas.style.height = 'auto'

      const ctx = canvas.getContext('2d')
      await page.render({ canvasContext: ctx, viewport }).promise

      // 渲染文本层
      if (textLayerDiv) {
        const textContent = await page.getTextContent()
        // 标记需要高亮的文本项
        const searchTerms = extractSearchTerms(props.highlightText)
        textContent.items.forEach((item: any) => {
          if (searchTerms.some(t => item.str.includes(t))) {
            ;(item as any).highlighted = true
          }
        })

        textLayerDiv.style.height = viewport.height + 'px'
        textLayerDiv.style.width = viewport.width + 'px'
        textLayerDiv.style.transform = 'scale(0.6667)'
        textLayerDiv.style.transformOrigin = 'top left'
        textLayerDiv.innerHTML = ''

        await pdfjsLib.renderTextLayer({
          textContentSource: textContent,
          container: textLayerDiv,
          viewport,
          textDivs: textContent.items.map((item: any) => {
            const div = document.createElement('span')
            div.textContent = item.str
            div.style.position = 'absolute'
            div.style.whiteSpace = 'pre'
            div.style.cursor = 'default'
            const tx = pdfjsLib.Util.transform(viewport.transform, item.transform)
            div.style.left = tx[4] + 'px'
            div.style.top = (tx[5] - item.height * 0.8) + 'px'
            div.style.fontSize = Math.sqrt(tx[2] * tx[2] + tx[3] * tx[3]) + 'px'
            div.style.fontFamily = 'sans-serif'
            if (item.highlighted) {
              div.style.backgroundColor = '#fef08a'
              div.style.color = '#92400e'
              div.style.padding = '1px 2px'
              div.style.borderRadius = '2px'
              if (matchPage.value === i) {
                div.id = 'pdf-highlight-anchor'
                div.style.scrollMarginTop = '80px'
              }
            }
            return div
          }),
        })
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
