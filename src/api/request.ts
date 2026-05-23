import axios, { type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { getAccessToken, getRefreshToken, setTokens, removeTokens } from '@/utils/token'
import type { Result } from '@/api/types/common'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 30000,
})

// 并发刷新Token的排队机制
let isRefreshing = false
let pendingRequests: Array<(token: string) => void> = []

async function refreshAccessToken(): Promise<string> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) throw new Error('No refresh token')
  const res = await axios.post<Result<{ accessToken: string; refreshToken: string; expiresIn: number }>>(
    `${import.meta.env.VITE_API_BASE_URL}/auth/refresh`,
    null,
    { params: { refreshToken } }
  )
  if (res.data.code !== 0) throw new Error(res.data.message)
  const { accessToken, refreshToken: newRefresh } = res.data.data
  setTokens(accessToken, newRefresh)
  return accessToken
}

// 请求拦截器：自动附加Bearer Token
request.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getAccessToken()
  if (token && config.headers) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  return config
})

// 响应拦截器：解包Result + 401静默刷新
request.interceptors.response.use(
  (response: AxiosResponse<Result>) => {
    const body = response.data
    if (body.code === 0) {
      return body.data as any
    }
    ElMessage.error(body.message || '请求失败')
    return Promise.reject(new Error(body.message))
  },
  async (error) => {
    const { config, response } = error
    if (!response) {
      ElMessage.error('网络连接失败')
      return Promise.reject(error)
    }

    // 401处理
    if (response.status === 401 && config && !config._retry) {
      // refresh/login自身401不重试
      if (config.url?.includes('/auth/refresh') || config.url?.includes('/auth/login')) {
        removeTokens()
        window.location.href = '/login'
        return Promise.reject(error)
      }

      if (!isRefreshing) {
        isRefreshing = true
        try {
          const newToken = await refreshAccessToken()
          pendingRequests.forEach((cb) => cb(newToken))
          pendingRequests = []
          config._retry = true
          config.headers['Authorization'] = `Bearer ${newToken}`
          return request(config)
        } catch {
          pendingRequests = []
          removeTokens()
          ElMessage.error('登录已过期，请重新登录')
          window.location.href = `/login?redirect=${encodeURIComponent(window.location.pathname)}`
          return Promise.reject(error)
        } finally {
          isRefreshing = false
        }
      } else {
        // 正在刷新中，排队等待
        return new Promise((resolve) => {
          pendingRequests.push((token: string) => {
            config._retry = true
            config.headers['Authorization'] = `Bearer ${token}`
            resolve(request(config))
          })
        })
      }
    }

    // 403
    if (response.status === 403) {
      ElMessage.error('无权限执行此操作')
      return Promise.reject(error)
    }

    // 429
    if (response.status === 429) {
      ElMessage.error('请求过于频繁，请稍后再试')
      return Promise.reject(error)
    }

    const msg = response.data?.message || `请求失败 (${response.status})`
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default request
