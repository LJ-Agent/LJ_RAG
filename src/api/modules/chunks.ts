import request from '@/api/request'
import type { Page } from '@/api/types/common'

const BASE = '/chunks'

export interface ChunkVO {
  id: number
  documentId: number
  chunkId: string
  chunkIndex: number
  content: string
  level: number
  parentId: string
  charCount: number
  status: string
  createdAt: string
}

export interface ChunkStats {
  totalCount: number
  activeCount: number
  deletedCount: number
  totalChars: number
}

export const chunkApi = {
  list: (documentId: number, params?: { page?: number; size?: number; keyword?: string }): Promise<Page<ChunkVO>> =>
    request.post(BASE, { documentId, ...params }),

  create: (documentId: number, content: string): Promise<ChunkVO> =>
    request.post(`${BASE}/create`, { documentId, content }),

  getByChunkId: (chunkId: string): Promise<ChunkVO> =>
    request.post(`${BASE}/by-chunk-id/${chunkId}`),

  update: (id: number, content: string): Promise<ChunkVO> =>
    request.put(`${BASE}/${id}`, { content }),

  delete: (id: number): Promise<void> =>
    request.delete(`${BASE}/${id}`),

  batchSetStatus: (ids: number[], status: string): Promise<void> =>
    request.put(`${BASE}/batch-status`, { ids, status }),

  getStats: (documentId: number): Promise<ChunkStats> =>
    request.post(`${BASE}/stats`, { documentId }),

  syncCount: (documentId: number): Promise<number> =>
    request.post(`${BASE}/sync-count`, { documentId }),

  startEmbedding: (documentId: number): Promise<void> =>
    request.post(`${BASE}/start-embedding`, { documentId }),

  submitForReview: (documentId: number): Promise<void> =>
    request.post(`${BASE}/submit-for-review`, { documentId }),
}
