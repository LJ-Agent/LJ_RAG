export interface LoginDTO {
  username: string
  password: string
}

export interface RegisterDTO {
  username: string
  password: string
  email: string
  realName: string
}

export interface UserVO {
  id: number
  username: string
  realName: string
  email: string
  phone: string
  avatarUrl: string
  status: number
  roles: string[]
  permissions: string[]
  lastLoginAt: string
  createdAt: string
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  userInfo: UserVO
}
