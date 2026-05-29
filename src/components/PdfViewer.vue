<template>
  <div class="pdf-viewer">
    <div v-if="loading" class="pdf-loading">
      <el-icon class="is-loading" :size="24"><Loading /></el-icon>
      <span>正在加载 PDF 并搜索定位...</span>
    </div>
    <div v-else ref="containerRef" class="pdf-container">
      <div v-for="n in pageCount" :key="n" :id="'pdf-page-' + n" class="pdf-page-wrap">
        <canvas :id="'pdf-canvas-' + n" class="pdf-canvas" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import * as pdfjsLib from 'pdfjs-dist'
import { getAccessToken } from '@/utils/token'

pdfjsLib.GlobalWorkerOptions.workerSrc = '/pdfjs-worker.mjs'

const props = defineProps<{
  pdfUrl: string
  highlightText: string
}>()

const loading = ref(true)
const containerRef = ref<HTMLElement>()
const pageCount = ref(0)
const scale = 1.5

const fn = (t: string) => t.replace(/\s+/g, '').replace(/[\f]/g, '')

async function render() {
  try {
    const loadingTask = pdfjsLib.getDocument({
      url: props.pdfUrl,
      cMapUrl: 'https://cdn.jsdelivr.net/npm/pdfjs-dist@5.4.449/cmaps/',
      cMapPacked: true,
    })
    const doc = await loadingTask.promise
    pageCount.value = doc.numPages
    await nextTick()
    await new Promise(r => setTimeout(r, 200))

    const searchText = fn(props.highlightText.trim())
    let matchPage = 0

    // 渲染所有页 canvas
    for (let i = 1; i <= doc.numPages; i++) {
      const page = await doc.getPage(i)
      const viewport = page.getViewport({ scale })
      const canvas = document.getElementById('pdf-canvas-' + i) as HTMLCanvasElement | null
      if (!canvas) continue

      canvas.height = viewport.height
      canvas.width = viewport.width
      canvas.style.width = '100%'
      canvas.style.height = 'auto'
      canvas.style.display = 'block'
      canvas.style.boxShadow = '0 2px 8px rgba(0,0,0,0.3)'

      const ctx = canvas.getContext('2d')!
      await page.render({ canvasContext: ctx, viewport }).promise

      if (searchText && matchPage === 0) {
        const tc = await page.getTextContent()
        const fullText = fn(tc.items.map((it: any) => it.str).join(''))
        // 搜索块文本的前40字符
        const needle = searchText.substring(0, Math.min(40, searchText.length))
        if (fullText.includes(needle)) {
          matchPage = i
        }
      }
    }

    // 在匹配页上覆盖黄色标记层
    if (matchPage > 0 && searchText) {
      const page = await doc.getPage(matchPage)
      const viewport = page.getViewport({ scale })
      const tc = await page.getTextContent()
      const pageEl = document.getElementById('pdf-page-' + matchPage)
      if (!pageEl || tc.items.length === 0) return

      // 创建高亮覆盖层
      const hlLayer = document.createElement('div')
      hlLayer.style.cssText = [
        'position:absolute', 'top:0', 'left:0',
        'width:' + viewport.width + 'px',
        'height:' + viewport.height + 'px',
        'pointer-events:none', 'z-index:10',
      ].join(';')
      pageEl.style.position = 'relative'
      pageEl.appendChild(hlLayer)

      const searchWords = searchText.replace(/(.{10})/g, '$1|').split('|').filter((s: string) => s.length >= 5)
      const matchedItems: any[] = []

      tc.items.forEach((item: any) => {
        const spanNorm = fn(item.str)
        if (spanNorm && searchWords.some((w: string) => spanNorm.includes(w))) {
          matchedItems.push(item)
        }
      })

      // 合并相邻匹配项并创建标黄矩形
      if (matchedItems.length > 0) {
        const highlightDiv = document.createElement('div')
        highlightDiv.style.cssText = [
          'position:absolute',
          'background:rgba(254,240,138,0.7)',
          'border-radius:2px',
          'pointer-events:none',
          'z-index:11',
        ].join(';')

        // 用第一个匹配项的坐标
        const first = matchedItems[0]
        const tx = first.transform
        const left = tx[4]
        const top = tx[5] - first.height * 0.8
        const fontSize = Math.sqrt(tx[0] * tx[0] + tx[1] * tx[1])

        highlightDiv.style.left = left + 'px'
        highlightDiv.style.top = top + 'px'
        highlightDiv.style.height = Math.max(fontSize, 16) + 'px'
        highlightDiv.style.width = '100%'
        highlightDiv.id = 'pdf-highlight-anchor'
        highlightDiv.style.scrollMarginTop = '80px'

        hlLayer.appendChild(highlightDiv)

        setTimeout(() => {
          highlightDiv.scrollIntoView({ behavior: 'smooth', block: 'center' })
        }, 500)
      }
    } else if (matchPage > 0) {
      // 无搜索文本但有匹配页：直接滚动
      setTimeout(() => {
        const el = document.getElementById('pdf-page-' + matchPage)
        if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
      }, 500)
    }
  } catch (e) {
    console.error('[PdfViewer] Error:', e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  setTimeout(render, 100)
})
</script>

<style scoped>
.pdf-viewer { flex:1; overflow:hidden; display:flex; flex-direction:column; background:#525659; }
.pdf-loading { flex:1; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:12px; color:#fff; font-size:14px; }
.pdf-container { flex:1; overflow-y:auto; padding:16px 0; }
.pdf-page-wrap { display:flex; justify-content:center; margin-bottom:16px; }
.pdf-canvas { display:block; max-width:95%; }
</style>
