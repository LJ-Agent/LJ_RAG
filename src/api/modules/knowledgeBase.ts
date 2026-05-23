import request from '@/api/request'
import type { KnowledgeBaseVO, KnowledgeBaseSaveDTO, KnowledgeBaseQueryDTO } from '@/api/types/knowledgeBase'
import type { Page } from '@/api/types/common'

const BASE = '/knowledge-bases'

export const knowledgeBaseApi = {
  list: (params: KnowledgeBaseQueryDTO): Promise<Page<KnowledgeBaseVO>> =>
    request.get(BASE, { params }),

  detail: (id: number): Promise<KnowledgeBaseVO> =>
    request.get(`${BASE}/${id}`),

  create: (data: KnowledgeBaseSaveDTO): Promise<KnowledgeBaseVO> =>
    request.post(BASE, data),

  update: (id: number, data: KnowledgeBaseSaveDTO): Promise<KnowledgeBaseVO> =>
    request.put(`${BASE}/${id}`, data),

  delete: (id: number): Promise<void> =>
    request.delete(`${BASE}/${id}`),

  toggleDocument: (kbId: number, docId: number, enabled: boolean): Promise<void> =>
    request.put(`${BASE}/${kbId}/documents/${docId}`, null, { params: { enabled } }),
}
