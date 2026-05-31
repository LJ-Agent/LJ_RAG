export interface FileVO {
  id: number
  kbId: number
  kbName?: string
  fileName: string
  fileType: string
  fileSize: number
  fileMd5: string
  status: string
  errorMessage: string
  chunkCount: number
  chunkStrategy?: string
  chunkConfig?: string
  uploadUserId: number
  uploadAt: string
  completedAt: string
  createdAt: string
}

export interface FileQueryDTO {
  kbId?: number
  status?: string
  fileName?: string
  page?: number
  size?: number
}

export interface FileUploadDTO {
  kbId: number
}

/** Chunk strategy parameter presets */
export const CHUNK_STRATEGY_CONFIGS: Record<string, { label: string; fields: StrategyField[] }> = {
  fixed: {
    label: '固定长度分块',
    fields: [
      { key: 'chunk_size', label: '块大小(字符)', type: 'number', default: 500, min: 100, max: 30000 },
      { key: 'chunk_overlap', label: '重叠长度(字符)', type: 'number', default: 50, min: 0, max: 5000 },
    ],
  },
  hierarchical: {
    label: '标题层级分块',
    fields: [
      { key: 'chunk_size', label: '块大小(字符)', type: 'number', default: 500, min: 100, max: 60000 },
      { key: 'min_heading_level', label: '最小标题层级(1~6)', type: 'number', default: 1, min: 1, max: 6 },
    ],
  },
  recursive: {
    label: '递归字符分块',
    fields: [
      { key: 'chunk_size', label: '块大小(字符)', type: 'number', default: 500, min: 100, max: 30000 },
      { key: 'chunk_overlap', label: '重叠长度(字符)', type: 'number', default: 50, min: 0, max: 5000 },
      { key: 'separators', label: '分隔符列表(逗号分隔)', type: 'text', default: '\\n\\n,\\n,。,.' },
    ],
  },
  semantic: {
    label: '语义分块',
    fields: [
      { key: 'similarity_threshold', label: '语义相似度阈值', type: 'number', default: 0.5, min: 0, max: 1, step: 0.05 },
      { key: 'min_chunk_size', label: '最小块大小(字符)', type: 'number', default: 100, min: 50, max: 10000 },
      { key: 'max_chunk_size', label: '最大块大小(字符)', type: 'number', default: 60000, min: 500, max: 60000 },
    ],
  },
  topic: {
    label: '主题分块',
    fields: [
      { key: 'topic_sensitivity', label: '主题切换敏感度', type: 'number', default: 0.7, min: 0.1, max: 1, step: 0.05 },
      { key: 'max_chunk_length', label: '最大块长度(字符)', type: 'number', default: 3000, min: 500, max: 60000 },
    ],
  },
  hybrid: {
    label: '混合分块（结构化+递归）',
    fields: [
      { key: 'coarse_chunk_threshold', label: '粗粒度块阈值(字符)', type: 'number', default: 2000, min: 500, max: 60000 },
      { key: 'fine_chunk_size', label: '精细分块大小(字符)', type: 'number', default: 500, min: 100, max: 30000 },
      { key: 'fine_chunk_overlap', label: '精细分块重叠长度', type: 'number', default: 50, min: 0, max: 5000 },
    ],
  },
}

export interface StrategyField {
  key: string
  label: string
  type: 'number' | 'text'
  default: number | string
  min?: number
  max?: number
  step?: number
}
