export interface KnowledgeBaseVO {
  id: number
  kbName: string
  description: string
  coverUrl: string
  status: number
  ownerId: number
  ownerName: string
  documentCount: number
  createdAt: string
  updatedAt: string
}

export interface KnowledgeBaseSaveDTO {
  kbName: string
  description: string
  coverUrl?: string
  status: number
}

export interface KnowledgeBaseQueryDTO {
  kbName?: string
  status?: number
  page?: number
  size?: number
}
