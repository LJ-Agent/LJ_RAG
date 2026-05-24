export interface SourceDoc {
  documentId: number
  documentName: string
  chunkId: string
  chunkIndex: number
  content: string
  score: number
}

export interface AnswerVO {
  chatId: number
  answer: string
  sourceDocs: SourceDoc[]
  tokenCount: number
  latencyMs: number
}

export interface QuestionDTO {
  question: string
  kbIds: number[]
  sessionId?: number
  topK?: number
  scoreThreshold?: number
}

export interface ChatHistoryVO {
  id: number
  sessionId?: number
  question: string
  answer: string
  sourceDocs?: string
  rating: number
  latencyMs: number
  isStream: number
  createdAt: string
}

export interface ChatSessionVO {
  id: number
  userId: number
  title: string
  kbIds: string
  messageCount: number
  createdAt: string
  updatedAt: string
  pinned?: boolean
}
