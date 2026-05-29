<template>
  <div class="pdf-viewer">
    <div v-if="loading" class="pdf-loading">
      <el-icon class="is-loading" :size="24"><Loading /></el-icon>
      <span>正在加载 PDF 并搜索定位...</span>
    </div>
    <div v-else ref="containerRef" class="pdf-container">
      <div v-for="n in pageCount" :key="n" :id="'pdf-page-' + n" class="pdf-page-wrap">
        <canvas :id="'pdf-canvas-' + n" class="pdf-canvas" />
        <div :id="'pdf-text-' + n" class="pdf-text-layer" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import * as pdfjsLib from 'pdfjs-dist'

// Worker 文件放在 public/ 目录，直接通过 URL 访问
pdfjsLib.GlobalWorkerOptions.workerSrc = '/pdfjs-worker.mjs'

const props = defineProps<{
  pdfUrl: string
  highlightText: string
}>()

console.warn('[PdfViewer] COMPONENT LOADED', new Date().toISOString())

const loading = ref(true)
const containerRef = ref<HTMLElement>()
const pageCount = ref(0)

const fn = (t: string) => t.replace(/\s+/g, '').replace(/[\f]/g, '')
const scale = 1.5

async function searchAndRender() {
  console.warn('[PdfViewer] searchAndRender START, pdfUrl:', props.pdfUrl?.substring(0, 80))
  try {
    console.warn('[PdfViewer] calling getDocument...')
    const loadingTask = pdfjsLib.getDocument({
      url: props.pdfUrl,
      cMapUrl: 'https://cdn.jsdelivr.net/npm/pdfjs-dist@5.4.449/cmaps/',
      cMapPacked: true,
    })
    const doc = await loadingTask.promise
    console.warn('[PdfViewer] getDocument DONE, pages:', doc.numPages)

    pageCount.value = doc.numPages
    await nextTick()
    await new Promise(r => setTimeout(r, 150))

    const searchText = fn(props.highlightText.trim())
    let matchPage = 0

    // 测试：在第一页文本层加一个可见 span 验证代码可达
    const testDiv = document.getElementById('pdf-text-1')
    if (testDiv) {
      const ts = document.createElement('span')
      ts.textContent = '✓ PDF文本层已激活'
      ts.style.cssText = 'position:absolute;left:50px;top:50px;font-size:20px;color:red;background:yellow;z-index:999;padding:4px;'
      testDiv.appendChild(ts)
    }

    // 第一遍：搜索匹配页 + 渲染 canvas
    for (let i = 1; i <= doc.numPages; i++) {
      const page = await doc.getPage(i)
      const viewport = page.getViewport({ scale })
      const canvas = document.getElementById('pdf-canvas-' + i) as HTMLCanvasElement | null

      if (canvas) {
        canvas.height = viewport.height
        canvas.width = viewport.width
        canvas.style.width = '100%'
        canvas.style.height = 'auto'
        canvas.style.display = 'block'
        canvas.style.boxShadow = '0 2px 8px rgba(0,0,0,0.3)'
        const ctx = canvas.getContext('2d')!
        await page.render({ canvasContext: ctx, viewport }).promise
      }

      // 搜索匹配文本
      if (searchText && matchPage === 0) {
        const tc = await page.getTextContent()
        const pageText = fn(tc.items.map((it: any) => it.str).join(''))
        if (pageText.includes(searchText.substring(0, Math.min(40, searchText.length)))) {
          matchPage = i
        }
      }
    }

    // 第二遍：在匹配页构建文本层并标黄
    if (matchPage > 0 && searchText) {
      const page = await doc.getPage(matchPage)
      const viewport = page.getViewport({ scale })
      const tc = await page.getTextContent()
      const textDiv = document.getElementById('pdf-text-' + matchPage)

      if (textDiv && tc.items.length > 0) {
        textDiv.style.height = viewport.height + 'px'
        textDiv.style.width = viewport.width + 'px'
        textDiv.style.position = 'absolute'
        textDiv.style.top = '0'

        const searchWords = searchText.replace(/(.{10})/g, '$1|').split('|').filter((s: string) => s.length >= 5)
        let firstSpan: HTMLSpanElement | null = null
        let consecutive = 0

        tc.items.forEach((item: any) => {
          const span = document.createElement('span')
          span.textContent = item.str
          const tx = item.transform
          span.style.cssText = [
            'position:absolute',
            'left:' + tx[4] + 'px',
            'top:' + (tx[5] - item.height * 0.8) + 'px',
            'font-size:' + Math.sqrt(tx[0] * tx[0] + tx[1] * tx[1]) + 'px',
            'font-family:sans-serif',
            'white-space:pre',
            'color:transparent',
          ].join(';')

          const spanNorm = fn(item.str)
          const matched = spanNorm && searchWords.some((w: string) => spanNorm.includes(w))
          if (matched) {
            span.style.backgroundColor = '#fef08a'
            span.style.color = '#92400e'
            span.style.padding = '1px 2px'
            span.style.borderRadius = '2px'
            consecutive++
            if (!firstSpan && consecutive >= 3) firstSpan = span
          } else if (spanNorm) {
            consecutive = 0
          }
          textDiv.appendChild(span)
        })

        if (firstSpan) {
          firstSpan.id = 'pdf-highlight-anchor'
          firstSpan.style.scrollMarginTop = '80px'
          setTimeout(() => {
            firstSpan!.scrollIntoView({ behavior: 'smooth', block: 'center' })
          }, 300)
        }
      }
    }

    // 滚动到匹配页（如果没有标黄 anchor）
    if (matchPage > 0 && !document.getElementById('pdf-highlight-anchor')) {
      const pageEl = document.getElementById('pdf-page-' + matchPage)
      if (pageEl) {
        setTimeout(() => pageEl.scrollIntoView({ behavior: 'smooth', block: 'start' }), 300)
      }
    }
  } catch (e) {
    console.error('[PdfViewer] Error:', e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  console.warn('[PdfViewer] onMounted')
  setTimeout(searchAndRender, 100)
})
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
.pdf-container {
  flex: 1;
  overflow-y: auto;
  padding: 16px 0;
}
.pdf-page-wrap {
  position: relative;
  display: flex;
  justify-content: center;
  margin-bottom: 16px;
}
.pdf-canvas {
  display: block;
  max-width: 95%;
}
.pdf-text-layer {
  pointer-events: none;
}
</style>
