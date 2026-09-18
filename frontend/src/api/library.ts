import { http } from './http'

/**
 * 书库。
 *
 * 字段名必须和后端返回的 JSON 完全一致——这里的 `lastOpened`
 * 对应后端 Library 实体的 `lastOpened`，写成 `lastOpenedAt` 会拿到 undefined。
 */
export interface Library {
  id: string
  name: string
  path: string
  bookCount?: number
  wordCount?: number
  lastOpened?: string | null
  createdAt?: string | null
}

export const libraryApi = {
  /**
   * 列出所有书库。
   *
   * 注意路径写 '/libraries' 而不是 '/api/libraries'：
   * http 的 baseURL 已经是 '/api'，再写一遍会变成 /api/api/libraries。
   *
   * 泛型为什么是两个：axios 的签名是 get<T, R>()，T 是响应体类型，
   * R 才是返回值类型。因为响应拦截器已经把 res.data 拆出来了，
   * 所以这里第二个泛型才是我们真正拿到的东西。
   */
  list: () => http.get<unknown, Library[]>('/libraries'),
}
