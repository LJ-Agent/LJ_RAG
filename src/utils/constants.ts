export const DOCUMENT_STATUS_MAP: Record<string, { label: string; type: 'info' | 'warning' | 'success' | 'danger' | '' }> = {
  UPLOADED: { label: '已上传', type: 'info' },
  PARSING: { label: '解析中', type: 'warning' },
  PARSING_FAILED: { label: '解析失败', type: 'danger' },
  CLEANING: { label: '清洗中', type: 'warning' },
  CLEANING_FAILED: { label: '清洗失败', type: 'danger' },
  PENDING_REVIEW: { label: '待审核', type: 'warning' },
  REVIEW_APPROVED: { label: '已通过', type: 'success' },
  REVIEW_REJECTED: { label: '已驳回', type: 'danger' },
  CHUNKING: { label: '分块中', type: 'warning' },
  CHUNKING_FAILED: { label: '分块失败', type: 'danger' },
  EMBEDDING: { label: '向量化中', type: 'warning' },
  EMBEDDING_FAILED: { label: '向量化失败', type: 'danger' },
  COMPLETED: { label: '已完成', type: 'success' },
}

export const REVIEW_RESULT_MAP: Record<string, { label: string; type: 'info' | 'warning' | 'success' | 'danger' | '' }> = {
  PENDING: { label: '待审核', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
}

export const FEEDBACK_TYPE_MAP: Record<string, string> = {
  BUG: '缺陷',
  SUGGESTION: '建议',
  CONTENT_ERROR: '内容错误',
  OTHER: '其他',
}

export const FEEDBACK_STATUS_MAP: Record<string, { label: string; type: 'info' | 'warning' | 'success' | 'danger' | '' }> = {
  PENDING: { label: '待处理', type: 'warning' },
  PROCESSING: { label: '处理中', type: 'info' },
  RESOLVED: { label: '已解决', type: 'success' },
  CLOSED: { label: '已关闭', type: '' },
}

export const CONFIG_TYPE_MAP: Record<string, string> = {
  STRING: '字符串',
  NUMBER: '数字',
  BOOLEAN: '布尔',
  JSON: 'JSON',
}

export const USER_STATUS_MAP: Record<number, { label: string; type: '' | 'success' | 'danger' }> = {
  0: { label: '禁用', type: 'danger' },
  1: { label: '启用', type: 'success' },
}
