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

      const headNeedle = searchText.substring(0, Math.min(60, searchText.length))
      const shortWords = headNeedle.replace(/(.{2,8})/g, '$1|').split('|').filter((s: string) => s.length >= 2)
      const matchedItems: any[] = []

      tc.items.forEach((item: any) => {
        const spanNorm = fn(item.str)
        if (!spanNorm) return
        if (headNeedle.includes(spanNorm) || shortWords.some((w: string) => w.length >= 3 && spanNorm.includes(w))) {
          matchedItems.push(item)
        }
      })

      if (matchedItems.length > 0) {
        // 计算所有匹配项的总包围盒
        let ml = Infinity, mt = Infinity, mr = -Infinity, mb = -Infinity
        matchedItems.forEach((item: any) => {
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
        const padding = 4
        const hw = mr - ml + padding * 2
        const hh = mb - mt + padding * 2

        // 多个高亮条（按行分组，视觉上更准确）
        const rows: any[][] = []
        matchedItems.forEach((item: any) => {
          const top = item.transform[5] - item.height * 0.8
          const fontSize = Math.sqrt(item.transform[0] * item.transform[0] + item.transform[1] * item.transform[1])
          let placed = false
          for (const row of rows) {
            const rowTop = row[0].transform[5] - row[0].height * 0.8
            if (Math.abs(top - rowTop) < fontSize * 1.2) {
              row.push(item)
              placed = true
              break
            }
          }
          if (!placed) rows.push([item])
        })

        rows.forEach((row, ri) => {
          let rl = Infinity, rr = -Infinity, rt = Infinity, rb = -Infinity
          row.forEach((item: any) => {
            const tx = item.transform
            const x = tx[4]
            const y = tx[5] - item.height * 0.8
            const w = item.width || (item.str.length * Math.sqrt(tx[0] * tx[0] + tx[1] * tx[1]) * 0.6)
            const h = item.height || Math.sqrt(tx[0] * tx[0] + tx[1] * tx[1])
            if (x < rl) rl = x
            if (y < rt) rt = y
            if (x + w > rr) rr = x + w
            if (y + h > rb) rb = y + h
          })
          const bar = document.createElement('div')
          bar.style.cssText = [
            'position:absolute',
            `left:${rl - 2}px`,
            `top:${rt - 1}px`,
            `width:${rr - rl + 8}px`,
            `height:${rb - rt + 4}px`,
            'background:rgba(254,240,138,0.5)',
            'border-radius:2px',
            'pointer-events:none',
            'z-index:11',
          ].join(';')
          if (ri === 0) {
            bar.id = 'pdf-highlight-anchor'
            bar.style.scrollMarginTop = '80px'
          }
          hlLayer.appendChild(bar)
        })

        setTimeout(() => {
          const anchor = document.getElementById('pdf-highlight-anchor')
          if (anchor) anchor.scrollIntoView({ behavior: 'smooth', block: 'center' })
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
