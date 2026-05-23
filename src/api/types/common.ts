// RAG后端统一响应: { code, message, data, timestamp }
export interface Result<T = unknown> {
  code: number
  message: string
  data: T
  timestamp: number
}

// 分页响应
export interface Page<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

// 分页查询参数
export interface PageQuery {
  page?: number
  size?: number
}
