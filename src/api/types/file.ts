export interface FileVO {
  id: number
  kbId: number
  fileName: string
  fileType: string
  fileSize: number
  fileMd5: string
  status: string
  errorMessage: string
  chunkCount: number
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
