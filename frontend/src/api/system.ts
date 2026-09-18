import { http } from './http'

export interface PickDirectoryResult {
  picked: boolean
  path: string | null
}

export const systemApi = {
  /**
   * 弹出后端的原生文件夹选择窗口。
   *
   * 浏览器不允许网页获取用户选择的绝对路径，所以这件事只能由后端做
   * （Java 的 JFileChooser）。
   *
   * timeout 设为 0（不超时）：用户可能在窗口里翻半天文件夹，
   * 默认的 30 秒会让请求提前失败。用户点取消时后端立刻返回 picked: false，
   * 不会一直挂着。
   */
  pickDirectory: (initialPath?: string) =>
    http.post<unknown, PickDirectoryResult>(
      '/system/pick-directory',
      { initialPath: initialPath ?? null },
      { timeout: 0 },
    ),
}
