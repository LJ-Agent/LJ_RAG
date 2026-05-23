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
  topK?: number
  scoreThreshold?: number
}

export interface ChatHistoryVO {
  id: number
  question: string
  answer: string
  rating: number
  latencyMs: number
  isStream: number
  createdAt: string
}
