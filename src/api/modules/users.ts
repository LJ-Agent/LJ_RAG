import request from '@/api/request'
import type { UserVO, ChangePasswordDTO, RoleVO } from '@/api/types/user'
import type { Page } from '@/api/types/common'

const BASE = '/users'

export const userApi = {
  list: (params: { page?: number; size?: number }): Promise<Page<UserVO>> =>
    request.get(BASE, { params }),

  changePassword: (data: ChangePasswordDTO): Promise<void> =>
    request.put(`${BASE}/password`, data),

  assignRole: (userId: number, roleId: number): Promise<void> =>
    request.post(`${BASE}/${userId}/roles/${roleId}`),

  removeRole: (userId: number, roleId: number): Promise<void> =>
    request.delete(`${BASE}/${userId}/roles/${roleId}`),

  getUserRoles: (userId: number): Promise<RoleVO[]> =>
    request.get(`${BASE}/${userId}/roles`),
}
