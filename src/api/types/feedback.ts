export interface FeedbackVO {
  id: number
  userId: number
  chatRecordId: number
  feedbackType: 'BUG' | 'SUGGESTION' | 'CONTENT_ERROR' | 'OTHER'
  content: string
  contact: string
  status: 'PENDING' | 'PROCESSING' | 'RESOLVED' | 'CLOSED'
  handlerNote: string
  handledBy: number
  handledAt: string
  createdAt: string
}

export interface FeedbackQueryDTO {
  page?: number
  size?: number
  status?: string
}

export interface FeedbackSubmitDTO {
  chatRecordId?: number
  feedbackType: string
  content: string
  contact?: string
}
