export interface ReviewVO {
  id: number
  documentId: number
  documentName: string
  reviewerId: number
  reviewerName: string
  result: string
  comment: string
  autoApproved: number
  reviewedAt: string
  createdAt: string
}

export interface ReviewSubmitDTO {
  documentId: number
  result: 'APPROVED' | 'REJECTED'
  comment?: string
}
