import request from '@/api/request'
import type { QuestionDTO, AnswerVO, ChatHistoryVO } from '@/api/types/qa'
import type { Page } from '@/api/types/common'
import { getAccessToken } from '@/utils/token'

const BASE = '/qa'

export const qaApi = {
  chat: (data: QuestionDTO): Promise<AnswerVO> =>
    request.post(`${BASE}/chat`, data),

  // SSE流式问答 - 使用fetch + ReadableStream
  streamChat: (data: QuestionDTO, signal?: AbortSignal): Promise<Response> => {
    const token = getAccessToken()
    return fetch(`${import.meta.env.VITE_API_BASE_URL}${BASE}/chat/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
      },
      body: JSON.stringify(data),
      signal,
    })
  },

  history: (params: { page?: number; size?: number }): Promise<Page<ChatHistoryVO>> =>
    request.get(`${BASE}/history`, { params }),
}
