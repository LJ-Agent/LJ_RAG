<template>
  <div class="pdf-viewer">
    <div ref="containerRef" class="pdf-container">
      <div v-for="n in pageCount" :key="n" :id="'pdf-page-' + n" class="pdf-page-wrap">
        <canvas :id="'pdf-canvas-' + n" class="pdf-canvas" />
      </div>
    </div>
    <div v-if="loading" class="pdf-loading-overlay">
      <el-icon class="is-loading" :size="24"><Loading /></el-icon>
      <span>正在加载 PDF 并搜索定位...</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import * as pdfjsLib from 'pdfjs-dist'

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
    const doc = await pdfjsLib.getDocument({
      url: props.pdfUrl,
      cMapUrl: 'https://cdn.jsdelivr.net/npm/pdfjs-dist@5.4.449/cmaps/',
      cMapPacked: true,
    }).promise

    pageCount.value = doc.numPages
    await nextTick()
    await new Promise(r => setTimeout(r, 300))

    const searchText = fn(props.highlightText.trim())
    let matchPage = 0

    // 第一遍：扫描文本找到匹配页
    if (searchText) {
      const needle = searchText.substring(0, Math.min(60, searchText.length))
      for (let i = 1; i <= doc.numPages && matchPage === 0; i++) {
        const page = await doc.getPage(i)
        const tc = await page.getTextContent()
        const fullText = fn(tc.items.map((it: any) => it.str).join(''))
        if (fullText.includes(needle)) matchPage = i
      }
    }

    // 第二遍：渲染所有页 canvas
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
    }

    // 在匹配页上覆盖黄色标记
    if (matchPage > 0 && searchText) {
      const page = await doc.getPage(matchPage)
      const viewport = page.getViewport({ scale })
      const tc = await page.getTextContent()
      const pageEl = document.getElementById('pdf-page-' + matchPage)
      if (!pageEl || tc.items.length === 0) return

      const hlLayer = document.createElement('div')
      hlLayer.style.cssText = `position:absolute;top:0;left:0;width:${viewport.width}px;height:${viewport.height}px;pointer-events:none;z-index:10;`
      pageEl.style.position = 'relative'
      pageEl.appendChild(hlLayer)

      // 精确匹配：在页面全文中定位块文本的起止字符位置
      const itemsWithText = tc.items.map((item: any, idx: number) => ({ item, idx, norm: fn(item.str) })).filter((x: any) => x.norm)
      const pageFullText = itemsWithText.map((x: any) => x.norm).join('')

      // 用块文本头部在页面定位起始位置
      const chunkHead = searchText.substring(0, Math.min(80, searchText.length))
      const headPos = pageFullText.indexOf(chunkHead)

      console.warn('[PdfViewer] headPos:', headPos, 'pageFullText len:', pageFullText.length, 'searchText len:', searchText.length)
      console.warn('[PdfViewer] page head:', pageFullText.substring(0, 80))
      console.warn('[PdfViewer] chunk head:', chunkHead.substring(0, 80))
      if (headPos !== -1) {
        // 从 headPos 开始，计算块文本与页面文本的最长连续匹配长度
        let matchLen = chunkHead.length
        const remainingPage = pageFullText.substring(headPos)
        const remainingChunk = searchText.substring(chunkHead.length)
        // 逐字符比较，找到分叉点
        for (let i = 0; i < Math.min(remainingPage.length, remainingChunk.length); i++) {
          if (remainingPage[i] === remainingChunk[i]) matchLen++
          else break
        }
        const endPos = headPos + matchLen

        // 将精确字符位置映射回文本项索引
        let startIdx = 0, endIdx = itemsWithText.length - 1
        let charCount = 0
        for (let i = 0; i < itemsWithText.length; i++) {
          charCount += itemsWithText[i].norm.length
          if (charCount > headPos && startIdx === 0) startIdx = i
          if (charCount >= endPos) { endIdx = i; break }
        }

        const highlightItems = itemsWithText.slice(startIdx, endIdx + 1)
        let firstBar: HTMLElement | null = null

        // 逐项精确标黄，每个文本项独立高亮条
        highlightItems.forEach(({ item }: any, hi: number) => {
          const tx = item.transform
          const x = tx[4]
          const y = tx[5] - item.height * 0.8
          const w = item.width || (item.str.length * Math.sqrt(tx[0] * tx[0] + tx[1] * tx[1]) * 0.6)
          const h = item.height || Math.sqrt(tx[0] * tx[0] + tx[1] * tx[1])
          const bar = document.createElement('div')
          bar.style.cssText = [
            'position:absolute',
            `left:${x - 1}px`,
            `top:${y - 1}px`,
            `width:${Math.max(w + 4, 20)}px`,
            `height:${h + 2}px`,
            'background:rgba(254,240,138,0.7)',
            'border-radius:1px',
            'pointer-events:none',
            'z-index:11',
          ].join(';')
          if (hi === 0) {
            bar.id = 'pdf-highlight-anchor'
            bar.style.scrollMarginTop = '80px'
            firstBar = bar
          }
          hlLayer.appendChild(bar)
        })

        setTimeout(() => {
          if (firstBar) firstBar.scrollIntoView({ behavior: 'smooth', block: 'center' })
        }, 500)
      }
    } else if (matchPage > 0) {
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

onMounted(() => setTimeout(render, 100))
</script>

<style scoped>
.pdf-viewer { flex:1; overflow:hidden; display:flex; flex-direction:column; background:#525659; position:relative; }
.pdf-loading-overlay { position:absolute; inset:0; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:12px; color:#fff; font-size:14px; background:rgba(82,86,89,0.85); z-index:50; }
.pdf-container { flex:1; overflow-y:auto; padding:16px 0; }
.pdf-page-wrap { display:flex; justify-content:center; margin-bottom:16px; }
.pdf-canvas { display:block; max-width:95%; }
</style>
