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
  const streamContent = ref('')
  const streamThinking = ref('')
  const error = ref<string | null>(null)
  let abortController: AbortController | null = null

  async function startStream(
    question: QuestionDTO,
    onThinking: (text: string) => void,
    onChunk: (text: string) => void,
    onDone: (metadata: StreamMetadata) => void
  ) {
    isStreaming.value = true
    streamContent.value = ''
    streamThinking.value = ''
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

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (line.startsWith('event:')) {
            currentEvent = line.slice(6).trim()
          } else if (line.startsWith('data: ')) {
            const data = line.slice(6)
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
              // 心跳事件，忽略
              currentEvent = ''
            } else if (currentEvent === 'reasoning') {
              streamThinking.value += data
              onThinking(data)
            } else {
              streamContent.value += data
              onChunk(data)
            }
          } else if (line === '') {
            // SSE 协议空行分隔事件，重置事件类型
            currentEvent = ''
          }
        }
      }

      // 处理 buffer 残余
      if (buffer.startsWith('data: ')) {
        const remaining = buffer.slice(6)
        if (currentEvent === 'done' && remaining.startsWith('{')) {
          try {
            const meta: StreamMetadata = JSON.parse(remaining)
            onDone(meta)
          } catch { /* ignore */ }
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
  }

  return { isStreaming, streamContent, streamThinking, error, startStream, stopStream }
}
