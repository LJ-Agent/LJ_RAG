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
      let currentEvent = ''
      let initialPingReceived = false

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          // 去掉 \r（兼容 Windows 风格 CRLF 换行）
          const cleanLine = line.endsWith('\r') ? line.slice(0, -1) : line
          if (cleanLine.startsWith('event:')) {
            currentEvent = cleanLine.slice(6).trim()
          } else if (cleanLine.startsWith('data:')) {
            // 兼容 "data:" 和 "data: " 两种格式
            const data = cleanLine.slice(5).trimStart()
            if (currentEvent === 'done') {
              try {
                const meta: StreamMetadata = JSON.parse(data)
                onDone(meta)
              } catch { /* ignore parse errors */ }
              currentEvent = ''
            } else if (currentEvent === 'error') {
              error.value = data
              currentEvent = ''
            } else if (currentEvent === 'ping') {
              if (!initialPingReceived && data === 'connected') {
                initialPingReceived = true
              } else if (initialPingReceived && streamPhase.value === 'retrieving') {
                streamPhase.value = 'thinking'
              }
              currentEvent = ''
            } else if (currentEvent === 'reasoning') {
              streamPhase.value = 'reasoning'
              streamThinking.value += data
              onThinking(data)
            } else if (currentEvent === 'sourceDocs') {
              try {
                const docs: SourceDoc[] = JSON.parse(data)
                streamSourceDocs.value = docs
                onSourceDocs?.(docs)
              } catch { /* ignore parse errors */ }
              currentEvent = ''
            } else {
              streamPhase.value = 'generating'
              streamContent.value += data
              onChunk(data)
            }
          } else if (cleanLine === '') {
            // SSE 协议空行分隔事件，重置事件类型
            currentEvent = ''
          }
          // 非 event/data/空行 的行（如注释行）忽略
        }
      }

      // 处理 buffer 残余（连接提前关闭或最后一行不完整时）
      if (buffer && currentEvent !== 'ping') {
        const cleanBuffer = buffer.endsWith('\r') ? buffer.slice(0, -1) : buffer
        if (cleanBuffer.startsWith('data:')) {
          const remaining = cleanBuffer.slice(5).trimStart()
          if (currentEvent === 'done' && remaining.startsWith('{')) {
            try {
              const meta: StreamMetadata = JSON.parse(remaining)
              onDone(meta)
            } catch { /* ignore */ }
          } else if (currentEvent === 'reasoning') {
            streamThinking.value += remaining
            onThinking(remaining)
          } else if (currentEvent === '' && remaining) {
            streamContent.value += remaining
            onChunk(remaining)
          }
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
