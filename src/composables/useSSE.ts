import { ref } from 'vue'
import { qaApi } from '@/api/modules/qa'
import type { QuestionDTO, SourceDoc } from '@/api/types/qa'

export interface StreamMetadata {
  chatId?: number
  tokenCount?: number
  latencyMs?: number
  sourceDocs?: SourceDoc[]
}

export function useSSE() {
  const isStreaming = ref(false)
  const streamContent = ref('')
  const error = ref<string | null>(null)
  let abortController: AbortController | null = null

  async function startStream(
    question: QuestionDTO,
    onChunk: (text: string) => void,
    onDone: (metadata: StreamMetadata) => void
  ) {
    isStreaming.value = true
    streamContent.value = ''
    error.value = null
    abortController = new AbortController()

    try {
      const response = await qaApi.streamChat(question, abortController.signal)

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
          if (line.startsWith('event: ')) {
            currentEvent = line.slice(7).trim()
          } else if (line.startsWith('data: ')) {
            const data = line.slice(6)
            if (currentEvent === 'done') {
              // 流式结束，解析元数据 JSON
              try {
                const meta: StreamMetadata = JSON.parse(data)
                onDone(meta)
              } catch { /* ignore parse errors */ }
              currentEvent = ''
            } else if (currentEvent === 'error') {
              error.value = data
              currentEvent = ''
            } else {
              // 普通 token 数据
              streamContent.value += data
              onChunk(data)
            }
          }
          // 空行表示事件分隔，重置 currentEvent
          // （SSE 协议中用空行分隔不同事件）
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
      if (e.name !== 'AbortError') {
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

  return { isStreaming, streamContent, error, startStream, stopStream }
}
