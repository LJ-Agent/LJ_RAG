import request from '@/api/request'
import type { QuestionDTO, AnswerVO, ChatHistoryVO, ChatSessionVO } from '@/api/types/qa'
import type { Page } from '@/api/types/common'
import { getAccessToken } from '@/utils/token'

const BASE = '/qa'

export const qaApi = {
  chat: (data: QuestionDTO): Promise<AnswerVO> =>
    request.post(`${BASE}/chat`, data),

  // SSE流式问答 - 使用fetch + ReadableStream，直连后端绕过Vite代理避免缓冲
  streamChat: (data: QuestionDTO, signal?: AbortSignal): Promise<Response> => {
    const token = getAccessToken()
    const apiBase = import.meta.env.DEV ? 'http://localhost:8080' : import.meta.env.VITE_API_BASE_URL
    return fetch(`${apiBase}${BASE}/chat/stream`, {
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

  // 会话管理
  getSessions: (params?: { page?: number; size?: number }): Promise<Page<ChatSessionVO>> =>
    request.get(`${BASE}/sessions`, { params }),

  createSession: (data: { title?: string; kbIds?: string }): Promise<ChatSessionVO> =>
    request.post(`${BASE}/sessions`, data),

  updateSession: (id: number, data: { title: string }): Promise<void> =>
    request.put(`${BASE}/sessions/${id}`, data),

  deleteSession: (id: number): Promise<void> =>
    request.delete(`${BASE}/sessions/${id}`),

  batchDeleteSessions: (ids: number[]): Promise<void> =>
    request.post(`${BASE}/sessions/batch-delete`, ids),

  getSessionRecords: (sessionId: number, params?: { page?: number; size?: number }): Promise<Page<ChatHistoryVO>> =>
    request.get(`${BASE}/sessions/${sessionId}/records`, { params }),
}
