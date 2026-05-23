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

export interface UserQueryDTO {
  username?: string
  status?: number
  page?: number
  size?: number
}

export interface ChangePasswordDTO {
  oldPassword: string
  newPassword: string
}

export interface RoleVO {
  id: number
  roleName: string
  roleCode: string
  description: string
  status: number
}
