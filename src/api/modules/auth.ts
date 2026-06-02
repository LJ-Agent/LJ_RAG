import request from '@/api/request'
import type { LoginDTO, RegisterDTO, TokenResponse, UserVO } from '@/api/types/auth'
import type { Result } from '@/api/types/common'
import axios from 'axios'

const BASE = '/auth'

export const loginApi = {
  login: (data: LoginDTO): Promise<TokenResponse> =>
    request.post(`${BASE}/login`, data),

  register: (data: RegisterDTO): Promise<void> =>
    request.post(`${BASE}/register`, data),

  refresh: (refreshToken: string): Promise<TokenResponse> => {
    // 刷新Token不能走带拦截器的request（拦截器可能触发递归）
    return axios
      .post<Result<TokenResponse>>(
        `${import.meta.env.VITE_API_BASE_URL}${BASE}/refresh`,
        null,
        { params: { refreshToken } }
      )
      .then((res) => {
        if (res.data.code === 0) return res.data.data
        throw new Error(res.data.message)
      })
  },

  logout: (): Promise<void> => request.post(`${BASE}/logout`),

  getMe: (): Promise<UserVO> => request.post(`${BASE}/me`),
}
