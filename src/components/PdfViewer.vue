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

      // 在页面全文（拼接标准化）中定位块文本的起止位置
      const itemsWithText = tc.items.map((item: any, idx: number) => ({ item, idx, norm: fn(item.str) })).filter((x: any) => x.norm)
      const pageFullText = itemsWithText.map((x: any) => x.norm).join('')

      // 用块文本的前后各 80 字符在页面全文中定位
      const chunkHead = searchText.substring(0, Math.min(80, searchText.length))
      const chunkTail = searchText.substring(Math.max(0, searchText.length - 80))
      const headPos = pageFullText.indexOf(chunkHead)
      const tailPos = pageFullText.lastIndexOf(chunkTail)

      if (headPos !== -1) {
        // 找到起始和结束的文本项索引
        let startIdx = 0, endIdx = itemsWithText.length - 1
        let charCount = 0
        for (let i = 0; i < itemsWithText.length; i++) {
          charCount += itemsWithText[i].norm.length
          if (charCount > headPos) { startIdx = i; break }
        }
        if (tailPos !== -1) {
          charCount = 0
          for (let i = 0; i < itemsWithText.length; i++) {
            charCount += itemsWithText[i].norm.length
            if (charCount > tailPos + chunkTail.length) { endIdx = i; break }
          }
        }

        const highlightItems = itemsWithText.slice(startIdx, endIdx + 1)

        let ml = Infinity, mt = Infinity, mr = -Infinity, mb = -Infinity
        highlightItems.forEach(({ item }: any) => {
          const tx = item.transform
          const x = tx[4]
          const y = tx[5] - item.height * 0.8
          const w = item.width || (item.str.length * Math.sqrt(tx[0] * tx[0] + tx[1] * tx[1]) * 0.6)
          const h = item.height || Math.sqrt(tx[0] * tx[0] + tx[1] * tx[1])
          if (x < ml) ml = x
          if (y < mt) mt = y
          if (x + w > mr) mr = x + w
          if (y + h > mb) mb = y + h
        })

        const highlight = document.createElement('div')
        highlight.style.cssText = [
          'position:absolute',
          `left:${ml - 6}px`,
          `top:${mt - 4}px`,
          `width:${Math.max(mr - ml + 12, 300)}px`,
          `height:${Math.max(mb - mt + 8, 24)}px`,
          'background:rgba(254,240,138,0.55)',
          'border:1px solid rgba(230,180,30,0.6)',
          'border-radius:3px',
          'pointer-events:none',
          'z-index:11',
        ].join(';')
        highlight.id = 'pdf-highlight-anchor'
        highlight.style.scrollMarginTop = '80px'

        hlLayer.appendChild(highlight)

        setTimeout(() => {
          highlight.scrollIntoView({ behavior: 'smooth', block: 'center' })
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
