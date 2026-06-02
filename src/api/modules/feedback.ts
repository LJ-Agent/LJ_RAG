import request from '@/api/request'
import type { FeedbackVO, FeedbackSubmitDTO, FeedbackQueryDTO } from '@/api/types/feedback'
import type { Page } from '@/api/types/common'

const BASE = '/feedback'

export const feedbackApi = {
  submit: (data: FeedbackSubmitDTO): Promise<void> =>
    request.post(BASE, data),

  list: (params: FeedbackQueryDTO): Promise<Page<FeedbackVO>> =>
    request.post(`${BASE}/list`, params),

  detail: (id: number): Promise<FeedbackVO> =>
    request.post(`${BASE}/${id}`),

  handle: (id: number, handlerNote: string): Promise<void> =>
    request.put(`${BASE}/${id}/handle`, { handlerNote }),
}
