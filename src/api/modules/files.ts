import request from '@/api/request'
import type { FileVO, FileQueryDTO } from '@/api/types/file'
import type { Page } from '@/api/types/common'

const BASE = '/files'

export const fileApi = {
  upload: (file: File, kbId: number, onProgress?: (pct: number) => void, chunkStrategy?: string): Promise<FileVO> => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('kbId', String(kbId))
    if (chunkStrategy) {
      formData.append('chunkStrategy', chunkStrategy)
    }
    return request.post(`${BASE}/upload`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (event) => {
        if (event.total && onProgress) {
          onProgress(Math.round((event.loaded * 100) / event.total))
        }
      },
    })
  },

  list: (params: FileQueryDTO): Promise<Page<FileVO>> =>
    request.get(BASE, { params }),

  detail: (id: number): Promise<FileVO> =>
    request.get(`${BASE}/${id}`),

  delete: (id: number): Promise<void> =>
    request.delete(`${BASE}/${id}`),

  download: async (id: number, fileName: string): Promise<void> => {
    const response = await request.get(`${BASE}/${id}/download`, {
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
    request.get(`${BASE}/${id}/content`, { responseType: 'text' }),

  getRawUrl: (id: number): string =>
    `${import.meta.env.VITE_API_BASE_URL}${BASE}/${id}/raw`,
}
