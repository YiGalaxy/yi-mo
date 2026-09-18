import axios from 'axios'

/**
 * 统一的 HTTP 客户端。
 *
 * baseURL 是相对路径 '/api'，开发时由 Vite 代理转发到后端，
 * 生产时前后端同源。不要在这里写死完整域名。
 */
export const http = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

/** 后端返回的统一错误体，格式见 docs/06-api.md */
export interface ApiError {
  error: string
  message: string
  details?: Record<string, unknown>
  timestamp?: string
}

http.interceptors.response.use(
  // 成功时直接返回数据体，调用方不用每次 .data.data
  (res) => res.data,
  (err) => {
    const body = err.response?.data as ApiError | undefined
    const apiError: ApiError = body ?? {
      error: 'NETWORK_ERROR',
      message: '无法连接后端服务，请确认它正在运行',
    }
    console.error('[API]', apiError.error, apiError.message)
    return Promise.reject(apiError)
  },
)
