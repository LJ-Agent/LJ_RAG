import { ref } from 'vue'
import { qaApi } from '@/api/modules/qa'
import type { QuestionDTO, SourceDoc } from '@/api/types/qa'

export interface StreamMetadata {
  chatId?: number
  tokenCount?: number
  latencyMs?: number
  thinking?: string
  sourceDocs?: SourceDoc[]
}

export function useSSE() {
  const isStreaming = ref(false)
  const streamPhase = ref<'retrieving' | 'thinking' | 'reasoning' | 'generating'>('retrieving')
  const streamContent = ref('')
  const streamThinking = ref('')
  const streamSourceDocs = ref<SourceDoc[]>([])
  const error = ref<string | null>(null)
  let abortController: AbortController | null = null

  async function startStream(
    question: QuestionDTO,
    onThinking: (text: string) => void,
    onChunk: (text: string) => void,
    onDone: (metadata: StreamMetadata) => void,
    onSourceDocs?: (docs: SourceDoc[]) => void
  ) {
    isStreaming.value = true
    streamPhase.value = 'retrieving'
    streamContent.value = ''
    streamThinking.value = ''
    streamSourceDocs.value = []
    error.value = null
    abortController = new AbortController()

    try {
      // 合并手动中止信号和120秒超时信号
      const timeoutSignal = AbortSignal.timeout(120000)
      const combinedSignal = abortController
        ? AbortSignal.any([abortController.signal, timeoutSignal])
        : timeoutSignal

      const response = await qaApi.streamChat(question, combinedSignal)

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }

      const reader = response.body!.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      const eventBuffer: string[] = []

      function dispatchEvent(eventType: string, data: string) {
        if (eventType === 'done') {
          try {
            const meta: StreamMetadata = JSON.parse(data)
            onDone(meta)
          } catch { /* ignore */ }
        } else if (eventType === 'error') {
          error.value = data
        } else if (eventType === 'ping') {
          if (data !== 'connected' && streamPhase.value === 'retrieving') {
            streamPhase.value = 'thinking'
          }
        } else if (eventType === 'reasoning') {
          streamPhase.value = 'reasoning'
          streamThinking.value += data
          onThinking(data)
        } else if (eventType === 'sourceDocs') {
          streamPhase.value = 'thinking'
          try {
            const docs: SourceDoc[] = JSON.parse(data)
            streamSourceDocs.value = docs
            onSourceDocs?.(docs)
          } catch { /* ignore */ }
        } else if (eventType === '') {
          streamPhase.value = 'generating'
          streamContent.value += data
          onChunk(data)
        }
      }

      function flushEvent(lines: string[]) {
        if (lines.length === 0) return
        let eventType = ''
        const dataLines: string[] = []
        for (const line of lines) {
          const clean = line.endsWith('\r') ? line.slice(0, -1) : line
          if (clean.startsWith('event:')) {
            eventType = clean.slice(6).trim()
          } else if (clean.startsWith('data:')) {
            dataLines.push(clean.slice(5).trimStart())
          }
        }
        if (dataLines.length > 0) {
          dispatchEvent(eventType, dataLines.join('\n'))
        }
      }

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (line === '' || line === '\r') {
            flushEvent(eventBuffer)
            eventBuffer.length = 0
          } else {
            eventBuffer.push(line)
          }
        }
      }

      // 处理最后可能未以空行结尾的事件
      if (eventBuffer.length > 0) {
        flushEvent(eventBuffer)
        eventBuffer.length = 0
      }
      // 处理 buffer 中残余的不完整行
      if (buffer) {
        const clean = buffer.endsWith('\r') ? buffer.slice(0, -1) : buffer
        if (clean) {
          eventBuffer.push(clean)
          flushEvent(eventBuffer)
        }
      }
    } catch (e: any) {
      if (e.name === 'AbortError' || e.name === 'TimeoutError') {
        if (!streamContent.value) {
          error.value = '请求超时，请重试'
        }
      } else {
        error.value = e.message
      }
    } finally {
      isStreaming.value = false
    }
  }

  function stopStream() {
    abortController?.abort()
    isStreaming.value = false
    streamPhase.value = 'retrieving'
  }

  return { isStreaming, streamPhase, streamContent, streamThinking, streamSourceDocs, error, startStream, stopStream }
}
