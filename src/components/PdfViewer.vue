<template>
  <div class="pdf-viewer">
    <div v-if="loading" class="pdf-loading-overlay">
      <el-icon class="is-loading" :size="24"><Loading /></el-icon>
      <span>正在加载 PDF 并搜索定位...</span>
    </div>
    <div ref="containerRef" class="pdf-container">
      <div v-for="n in pageCount" :key="n" :id="'pdf-page-' + n" class="pdf-page-wrap">
        <div :id="'pdf-wrapper-' + n" class="pdf-page-inner" />
      </div>
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

    // 第一遍：扫描找到匹配页
    if (searchText) {
      const needle = searchText.substring(0, Math.min(60, searchText.length))
      for (let i = 1; i <= doc.numPages && matchPage === 0; i++) {
        const page = await doc.getPage(i)
        const tc = await page.getTextContent()
        const fullText = fn(tc.items.map((it: any) => it.str).join(''))
        if (fullText.includes(needle)) matchPage = i
      }
    }

    // 渲染所有页
    for (let i = 1; i <= doc.numPages; i++) {
      const wrapper = document.getElementById('pdf-wrapper-' + i)
      if (!wrapper) continue

      const page = await doc.getPage(i)
      const vp = page.getViewport({ scale })

      // 创建 canvas
      const canvas = document.createElement('canvas')
      canvas.height = vp.height
      canvas.width = vp.width
      canvas.style.display = 'block'
      canvas.style.boxShadow = '0 2px 8px rgba(0,0,0,0.3)'
      canvas.className = 'pdf-canvas-inner'

      const ctx = canvas.getContext('2d')!
      await page.render({ canvasContext: ctx, viewport: vp }).promise

      // 设置 wrapper 尺寸和缩放
      wrapper.style.width = vp.width + 'px'
      wrapper.style.height = vp.height + 'px'
      wrapper.style.position = 'relative'
      wrapper.appendChild(canvas)

      // 动态缩放 wrapper 以适配容器宽度，保持 canvas 和高亮层比例一致
      const containerWidth = containerRef.value?.clientWidth || 900
      const fitScale = Math.min(1, (containerWidth - 32) / vp.width)
      wrapper.style.transform = `scale(${fitScale})`
      wrapper.style.marginBottom = `${vp.height * (fitScale - 1)}px`

      // 在匹配页创建高亮层
      console.warn('[PdfViewer] Page', i, 'matchPage:', matchPage, 'searchText:', !!searchText)
      if (i === matchPage && searchText) {
        console.warn('[PdfViewer] Creating highlights for page', i)
        const tc = await page.getTextContent()
        const allItems = tc.items.map((item: any, idx: number) => ({ item, idx, norm: fn(item.str) }))
        const itemsWithText = allItems.filter((x: any) => x.norm)  // 所有非空项用于文本匹配
        const pageFullText = itemsWithText.map((x: any) => x.norm).join('')
        const chunkHead = searchText.substring(0, Math.min(80, searchText.length))
        const headPos = pageFullText.indexOf(chunkHead)

        console.warn('[PdfViewer] headPos:', headPos, 'pageFullText:', pageFullText.length, 'searchText:', searchText.length)
        if (headPos !== -1) {
          const coverLen = Math.min(searchText.length, pageFullText.length - headPos)
          const coverEnd = headPos + coverLen

          let startIdx = 0, endIdx = itemsWithText.length - 1
          let charCount = 0
          let foundStart = false
          for (let j = 0; j < itemsWithText.length; j++) {
            charCount += itemsWithText[j].norm.length
            if (!foundStart && charCount > headPos) { startIdx = j; foundStart = true }
            if (charCount >= coverEnd) { endIdx = j; break }
          }

          const hlLayer = document.createElement('div')
          hlLayer.style.cssText = `position:absolute;inset:0;pointer-events:none;z-index:10;`
          wrapper.appendChild(hlLayer)

          const highlightItems = itemsWithText.slice(startIdx, endIdx + 1)
            .filter(({ item }: any) => {
              const str = (item.str || '').trim()
              return str.length >= 2  // 只过滤空字符和单字碎片
            })

          console.warn('[PdfViewer] highlightItems:', highlightItems.length)
          let firstBar: HTMLElement | null = null
          highlightItems.forEach(({ item }: any, hi: number) => {
            const tx = item.transform
            const x = tx[4]
            const y = tx[5] - item.height * 0.85
            const fz = Math.sqrt(tx[0] ** 2 + tx[1] ** 2)
            const w = Math.max(item.width || (item.str.length * fz * 0.7), 24)
            const h = Math.max(item.height || fz, 14)

            const bar = document.createElement('div')
            bar.style.cssText = [
              'position:absolute',
              `left:${x - 2}px`,
              `top:${y - 1}px`,
              `width:${w + 4}px`,
              `height:${h + 3}px`,
              'background:rgba(254,240,138,0.65)',
              'border-radius:2px',
              'pointer-events:none',
              'z-index:11',
            ].join(';')
            if (hi === 0) { bar.id = 'pdf-highlight-anchor'; firstBar = bar }
            hlLayer.appendChild(bar)
          })

          if (firstBar) {
            setTimeout(() => firstBar!.scrollIntoView({ behavior: 'smooth', block: 'center' }), 600)
          }
        }
      }
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
.pdf-viewer { flex:1; display:flex; flex-direction:column; background:#525659; position:relative; min-height:0; height:0; }
.pdf-loading-overlay { position:absolute; inset:0; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:12px; color:#fff; font-size:14px; background:rgba(82,86,89,0.85); z-index:50; }
.pdf-container { flex:1; overflow-y:auto; overflow-x:hidden; padding:16px 0; min-height:0; height:0; }
.pdf-page-wrap { display:flex; justify-content:center; margin-bottom:16px; }
.pdf-page-inner { position:relative; transform-origin:top center; }
.pdf-canvas-inner { display:block; }

</style>
