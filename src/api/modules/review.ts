import request from '@/api/request'
import type { ReviewVO, ReviewSubmitDTO } from '@/api/types/review'
import type { Page } from '@/api/types/common'

const BASE = '/review'

export const reviewApi = {
  pending: (params: { page?: number; size?: number }): Promise<Page<ReviewVO>> =>
    request.get(`${BASE}/pending`, { params }),

  submit: (data: ReviewSubmitDTO): Promise<void> =>
    request.post(`${BASE}/submit`, data),

  batchApprove: (ids: number[]): Promise<void> =>
    request.post(`${BASE}/batch-approve`, ids),
}
