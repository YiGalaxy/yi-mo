import { http } from './http'

/**
 * 书库。
 *
 * 字段名必须和后端返回的 JSON 完全一致——后端的实体里叫 lastOpened，
 * 这里写成 lastOpenedAt 就会拿到 undefined。
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

// ===== 卷章树 =====
//
// 树是三层：书 → 卷 → 章。后端返回的就是这个结构，
// 下面这些 interface 是照着 dto/TreeResponse.java 一行行对过来的。

/** 章节在树上的简要信息。没有 content——树只显示目录，不传正文 */
export interface ChapterBrief {
  id: string
  title: string
  relPath: string
  order: number
  status: 'draft' | 'revising' | 'done'
  wordCount: number
  /** 待处理的批注数，用来显示红色角标 */
  pendingReviewCount: number
}

/** 一卷 */
export interface VolumeNode {
  /** 空串表示「未分卷」 */
  name: string
  chapters: ChapterBrief[]
}

/** 一本书 */
export interface BookNode {
  name: string
  relPath: string
  volumes: VolumeNode[]
  totalWords: number
  chapterCount: number
}

/** 整个书库的结构 */
export interface TreeResponse {
  books: BookNode[]
}

/** 扫描结果 */
export interface ScanResult {
  total: number
  indexed: number
  errors: string[]
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

  /** 添加书库。返回创建好的那一个（不是数组） */
  create: (data: { path: string; name?: string }) =>
    http.post<unknown, Library>('/libraries', data),

  /** 移除书库。只删数据库记录，磁盘文件不动 */
  remove: (id: string) =>
    http.delete<unknown, { id: string; removed: boolean; filesDeleted: boolean }>(
      `/libraries/${id}`,
    ),

  /** 取整个书库的卷章树 */
  tree: (id: string) => http.get<unknown, TreeResponse>(`/libraries/${id}/tree`),

  /**
   * 重新扫描书库，重建索引。
   *
   * 这个请求可能要几十秒（100 万字的书库约 30 秒），
   * 所以要单独设个长超时，不能用默认的 30 秒。
   */
  rescan: (id: string) =>
    http.post<unknown, ScanResult>(`/libraries/${id}/rescan`, {}, { timeout: 300_000 }),
}
