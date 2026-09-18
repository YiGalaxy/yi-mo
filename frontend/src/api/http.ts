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

/**
 * 把 catch 到的未知错误转成 ApiError。
 *
 * 为什么需要这个函数：
 * - TypeScript 4.4+ 在 strict 模式下，catch 变量的类型是 `unknown`，不能直接当 ApiError 用
 * - 写 `catch (e: any)` 会被 ESLint 的 @typescript-eslint/no-explicit-any 拦下
 *
 * 所以统一在这里做一次安全的类型收窄，业务代码里 `catch (e)` 之后调 toApiError(e) 即可。
 */
export function toApiError(e: unknown): ApiError {
  if (e && typeof e === 'object' && 'error' in e && 'message' in e) {
    return e as ApiError
  }
  return {
    error: 'UNKNOWN_ERROR',
    message: e instanceof Error ? e.message : String(e),
  }
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
