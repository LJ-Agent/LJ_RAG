import { ref } from 'vue'
import { qaApi } from '@/api/modules/qa'
import type { QuestionDTO } from '@/api/types/qa'

export function useSSE() {
  const isStreaming = ref(false)
  const streamContent = ref('')
  const error = ref<string | null>(null)
  let abortController: AbortController | null = null

  async function startStream(
    question: QuestionDTO,
    onChunk: (text: string) => void,
    onDone: (metadata: { chatId?: number; tokenCount?: number; latencyMs?: number }) => void
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

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (line.startsWith('data: ')) {
            const chunk = line.slice(6)
            streamContent.value += chunk
            onChunk(chunk)
          } else if (line.startsWith('event: done')) {
            // 最后一条data在下一行
          } else if (line.startsWith('data: {')) {
            try {
              const meta = JSON.parse(line.slice(6))
              if (meta.chatId) {
                onDone(meta)
              }
            } catch { /* ignore parse errors */ }
          }
        }
      }

      // 处理buffer残余
      if (buffer.startsWith('data: ') && buffer.length > 6) {
        const remaining = buffer.slice(6)
        if (remaining.startsWith('{')) {
          try {
            const meta = JSON.parse(remaining)
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
