import request from '@/api/request'
import type { SystemConfigVO } from '@/api/types/config'
import type { Page } from '@/api/types/common'

const BASE = '/configs'

export const configApi = {
  list: (params: { page?: number; size?: number }): Promise<Page<SystemConfigVO>> =>
    request.get(BASE, { params }),

  get: (key: string): Promise<SystemConfigVO> =>
    request.get(`${BASE}/${key}`),

  save: (data: Partial<SystemConfigVO>): Promise<SystemConfigVO> =>
    request.post(BASE, data),

  delete: (id: number): Promise<void> =>
    request.delete(`${BASE}/${id}`),
}
