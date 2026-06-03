import request from '@/api/request'
import type { SystemConfigVO, ConfigHistoryVO, ConfigSaveResult, ConfigValidateResult } from '@/api/types/config'
import type { Page } from '@/api/types/common'

const BASE = '/configs'

export const configApi = {
  // 列表（支持分类筛选）
  list: (params: { page?: number; size?: number; category?: string }): Promise<Page<SystemConfigVO>> =>
    request.get(BASE, { params }),

  // 按分类获取
  listByCategory: (category: string): Promise<SystemConfigVO[]> =>
    request.get(`${BASE}/category/${category}`),

  // 获取分类列表
  categories: (): Promise<string[]> =>
    request.get(`${BASE}/categories`),

  // 获取单个
  get: (key: string): Promise<SystemConfigVO> =>
    request.get(`${BASE}/${key}`),

  // 保存或更新（含校验）
  save: (data: Partial<SystemConfigVO>): Promise<ConfigSaveResult> =>
    request.post(BASE, data),

  // 批量更新
  batchUpdate: (configs: Partial<SystemConfigVO>[]): Promise<{ success: number; errors: string[] }> =>
    request.post(`${BASE}/batch`, configs),

  // 校验值（不保存）
  validate: (data: Partial<SystemConfigVO>): Promise<ConfigValidateResult> =>
    request.post(`${BASE}/validate`, data),

  // 删除
  delete: (id: number): Promise<void> =>
    request.delete(`${BASE}/${id}`),

  // 变更历史
  getHistory: (key: string): Promise<ConfigHistoryVO[]> =>
    request.get(`${BASE}/${key}/history`),

  // 回滚
  rollback: (key: string, historyId: number): Promise<ConfigSaveResult> =>
    request.post(`${BASE}/${key}/rollback/${historyId}`),
}
