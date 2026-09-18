import { http } from './http'

export interface PingResult {
  ok: boolean
  service: string
  time: string
  sampleId: string
}

export const pingApi = {
  /** 健康检查。返回的 sampleId 可以顺便验证 ULID 生成是否正常 */
  ping: () => http.get<unknown, PingResult>('/ping'),
}
