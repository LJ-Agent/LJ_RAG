import request from '@/api/request'
import type { FileVO, FileQueryDTO } from '@/api/types/file'
import type { Page } from '@/api/types/common'
import { getAccessToken } from '@/utils/token'

const BASE = '/files'

export const fileApi = {
  upload: (file: File, kbId: number, onProgress?: (pct: number) => void, chunkStrategy?: string, chunkConfig?: string): Promise<FileVO> => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('kbId', String(kbId))
    if (chunkStrategy) {
      formData.append('chunkStrategy', chunkStrategy)
    }
    if (chunkConfig) {
      formData.append('chunkConfig', chunkConfig)
    }
    return request.post(`${BASE}/upload`, formData, {
      onUploadProgress: (event) => {
        if (event.total && onProgress) {
          onProgress(Math.round((event.loaded * 100) / event.total))
        }
      },
    })
  },

  list: (params: FileQueryDTO): Promise<Page<FileVO>> =>
    request.post(`${BASE}/list`, params),

  detail: (id: number): Promise<FileVO> =>
    request.post(`${BASE}/${id}`),

  delete: (id: number): Promise<void> =>
    request.delete(`${BASE}/${id}`),

  batchDelete: (ids: number[]): Promise<void> =>
    request.post(`${BASE}/batch-delete`, ids),

  rechunk: (id: number, chunkStrategy: string, chunkConfig?: string): Promise<FileVO> =>
    request.post(`${BASE}/${id}/rechunk`, { chunkStrategy, chunkConfig }),

  download: async (id: number, fileName: string): Promise<void> => {
    const response = await request.post(`${BASE}/${id}/download`, null, {
      responseType: 'blob',
    })
    const url = window.URL.createObjectURL(new Blob([response as any]))
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    link.click()
    window.URL.revokeObjectURL(url)
  },

  getContent: (id: number): Promise<string> =>
    request.post(`${BASE}/${id}/content`, null, { responseType: 'text' }),

  getRawContent: async (id: number): Promise<string> => {
    const token = getAccessToken()
    const resp = await fetch(`${import.meta.env.VITE_API_BASE_URL}/files/${id}/raw`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
    })
    if (!resp.ok) throw new Error('Failed to fetch raw content')
    return resp.text()
  },

  getRawArrayBuffer: async (id: number): Promise<ArrayBuffer> => {
    const token = getAccessToken()
    const resp = await fetch(`${import.meta.env.VITE_API_BASE_URL}/files/${id}/raw`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
    })
    if (!resp.ok) throw new Error('Failed to fetch raw file')
    return resp.arrayBuffer()
  },

  getRawBlobUrl: async (id: number): Promise<string> => {
    const response = await request.post(`${BASE}/${id}/raw`, null, {
      responseType: 'blob',
    })
    const blob = new Blob([response as any])
    return window.URL.createObjectURL(blob)
  },
}
