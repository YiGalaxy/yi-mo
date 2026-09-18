# 接口设计

前后端分离开发，接口是唯一的契约。**这份文档定稿后，前端和后端可以完全并行开发**——前端用 Mock，后端用 Postman 测，互不阻塞。

## 1. 通用约定

### 基础

| 项 | 约定 |
|---|---|
| 基础路径 | `/api` |
| 数据格式 | JSON，`Content-Type: application/json; charset=utf-8` |
| 流式响应 | `text/event-stream`（SSE） |
| 认证 | 无。本地服务，只监听 `127.0.0.1` |
| 端口 | 后端 18080，前端开发时 5173（Vite 代理 `/api`） |

**只监听 `127.0.0.1` 而不是 `0.0.0.0`**。亿墨没有认证，绑定到 `0.0.0.0` 意味着同一个局域网里任何人都能读写你的稿件。

```yaml
server:
  address: 127.0.0.1
  port: 18080
```

### 导入 Apifox

`openapi/yimo-api.json` 是 OpenAPI 3.0 规范，可直接导入 Apifox：**项目设置 → 导入数据 → OpenAPI/Swagger → 上传文件**。

**有个坑：不处理的话所有接口都会变成「已发布」。**

Apifox 的接口状态不是 OpenAPI 标准字段，它靠扩展字段 `x-apifox-status` 判断。**没有这个字段时，Apifox 默认给「已发布」**——而我们所有接口其实都还没实现，标成已发布是误导。

所以文件里每个 operation 都带了一行：

```json
{
  "tags": ["Library"],
  "summary": "添加书库",
  "operationId": "createLibrary",
  "x-apifox-status": "developing",
  ...
}
```

`x-apifox-status` 的取值：

| 值 | 界面显示 |
|---|---|
| `designing` | 设计中 |
| `pending` | 待确定 |
| `developing` | **开发中**（本项目统一用这个） |
| `integrating` | 联调中 |
| `testing` | 测试中 |
| `tested` | 已测完 |
| `released` | 已发布 |
| `deprecated` | 将废弃 |
| `exception` | 有异常 |
| `obsolete` | 已废弃 |

**接口做完之后记得改**：实现并通过测试的接口，把 `developing` 改成 `tested` 或 `released`，Apifox 里重新导入即可。

**手改 JSON 要注意**：这个文件用标准 2 空格缩进、一个字段一行，是为了 diff 精确——别为了省行数把多个字段挤在一行，那会让后续每次改动都产生大片无意义的 diff。

### 响应格式

成功直接返回数据体，失败返回统一错误体。**不套 `{code, message, data}` 外壳**——HTTP 状态码已经表达了成败，再包一层是冗余。

```jsonc
// 200 OK
{ "id": "ch_01H8XYZ", "title": "第一章 雪夜" }

// 404 Not Found
{
  "error": "CHAPTER_NOT_FOUND",
  "message": "章节不存在",
  "details": { "chapterId": "ch_01H8XYZ" },
  "timestamp": "2026-09-18T12:30:00+08:00"
}
```

后端用 `@RestControllerAdvice` 统一处理异常：

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiError> handle(BizException e) {
        return ResponseEntity.status(e.getCode().httpStatus())
            .body(new ApiError(e.getCode().name(), e.getMessage(), e.getDetails()));
    }
}
```

前端用 axios 拦截器统一处理：

```ts
http.interceptors.response.use(
  res => res.data,
  err => {
    const e = err.response?.data
    toast.error(e?.message ?? '请求失败')
    return Promise.reject(e)
  }
)
```

### 错误码

| 错误码 | HTTP | 说明 |
|---|---|---|
| `LIBRARY_NOT_FOUND` | 404 | 书库不存在 |
| `LIBRARY_PATH_INVALID` | 400 | 书库路径无效或不可读 |
| `LIBRARY_PATH_DUPLICATE` | 409 | 该路径已添加过 |
| `CHAPTER_NOT_FOUND` | 404 | 章节不存在 |
| `CHAPTER_TITLE_DUPLICATE` | 409 | 同目录下已有同名的章节 |
| `PATH_OUT_OF_BOUNDS` | 403 | 路径越界（试图访问书库目录之外） |
| `FILE_READ_FAILED` | 500 | 文件读取失败 |
| `FILE_WRITE_FAILED` | 500 | 文件写入失败 |
| `CONTENT_HASH_MISMATCH` | 409 | 保存时检测到文件已被外部修改 |
| `MODEL_NOT_CONFIGURED` | 400 | 尚未配置任何模型 |
| `AI_CALL_FAILED` | 502 | 模型调用失败 |
| `AI_QUOTA_EXCEEDED` | 429 | 已达每日用量上限 |
| `TASK_ALREADY_RUNNING` | 409 | 同类型任务正在执行 |
| `TASK_NOT_FOUND` | 404 | 任务不存在 |
| `EXPORT_FAILED` | 500 | 导出失败 |

### 数据类型约定

| 类型 | 格式 | 示例 |
|---|---|---|
| ID | 26 字符 ULID，带类型前缀 | `ch_01H8XYZABCDEFGHJKMNPQRST` |
| 时间 | ISO 8601，带时区偏移 | `2026-09-18T12:30:00+08:00` |
| 路径 | 相对书库根的 POSIX 风格路径 | `07-正文/第001章-雪夜.md` |
| 分页 | `page`（从 0 开始）、`size`（默认 20，最大 200） | `?page=0&size=20` |

### 分页响应

```jsonc
{
  "items": [ /* ... */ ],
  "page": 0,
  "size": 20,
  "total": 137,
  "hasNext": true
}
```

## 2. 接口总览

| 模块 | 接口数 | 说明 |
|---|---|---|
| [书库](#3-书库-library) | 8 | 添加、打开、扫描、统计 |
| [目录树](#4-目录树-tree) | 1 | 卷章结构 |
| [章节](#5-章节-chapter) | 9 | 增删改查、排序、快照 |
| [批注](#6-批注-review) | 5 | 校对结果的读写与状态流转 |
| [校对](#7-校对-proofread) | 2 | 触发校对、查询能力状态 |
| [实体](#8-实体-entity) | 9 | 知识库的实体与关系 |
| [搜索](#9-搜索-search) | 1 | 全文 / 语义检索 |
| [AI 任务](#10-ai-任务-task) | 4 | 队列、状态、取消、进度流 |
| [Agent 对话](#11-agent-对话-chat) | 6 | 会话与流式消息 |
| [设置](#12-设置-setting) | 5 | 模型配置、偏好 |
| [统计](#13-统计-stat) | 2 | 写作统计 |
| [导出](#14-导出-export) | 2 | 导出格式与下载 |
| [系统](#15-系统-system) | 1 | 调用操作系统的能力（文件夹选择窗口） |

## 3. 书库 Library

### `GET /api/libraries` — 列出所有书库

```jsonc
[
  {
    "id": "lib_01H8XYZ",
    "name": "我的小说",
    "path": "D:\\Writing\\我的小说",
    "bookCount": 3,
    "wordCount": 1284000,
    "lastOpenedAt": "2026-09-18T12:30:00+08:00",
    "createdAt": "2026-08-01T09:00:00+08:00"
  }
]
```

### `POST /api/libraries` — 添加书库

```jsonc
// 请求
{ "path": "D:\\Writing\\我的小说", "name": "我的小说" }

// 响应 201
{ "id": "lib_01H8XYZ", "name": "我的小说", "path": "D:\\Writing\\我的小说", "bookCount": 3 }
```

后端校验：路径存在、是目录、有读权限。**不要求路径为空**——作者可能指向一个已有的 Obsidian 库。若该目录下没有 `.yimo/`，视为新书库，扫描时按 `02-library-format.md` 的推断规则识别文件类型。

### `DELETE /api/libraries/{id}` — 移除书库

**只从数据库移除记录，绝不删除磁盘文件。**

```jsonc
{ "id": "lib_01H8XYZ", "removed": true, "filesDeleted": false }
```

响应体里显式返回 `filesDeleted: false`，让前端能明确告知用户"你的文件还在原处"。

### 其余

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/libraries/{id}` | 书库详情 |
| `PATCH` | `/api/libraries/{id}` | 重命名（只改显示名，不动磁盘目录名） |
| `POST` | `/api/libraries/{id}/open` | 标记为打开，触发增量扫描 |
| `POST` | `/api/libraries/{id}/rescan` | 强制全量重建索引，返回 taskId |
| `GET` | `/api/libraries/{id}/stats` | 字数、章节数、实体数 |

## 4. 目录树 Tree

### `GET /api/libraries/{id}/tree` — 卷章结构

```jsonc
{
  "books": [
    {
      "id": "bk_01H8XYZ",
      "name": "剑来",
      "relPath": "剑来",
      "volumes": [
        {
          "name": "第一卷 少年游",
          "chapters": [
            {
              "id": "ch_01H8AAA",
              "title": "第一章 雪夜",
              "relPath": "剑来/07-正文/第001章-雪夜.md",
              "order": 1,
              "status": "draft",
              "wordCount": 3204,
              "hasReviews": true,
              "pendingReviewCount": 15
            }
          ]
        }
      ],
      "totalWords": 1284000,
      "chapterCount": 312
    }
  ]
}
```

`hasReviews` 和 `pendingReviewCount` 让前端能在树上直接显示红色角标，作者一眼看到哪几章有待处理的问题。

**未分卷的章节放在 `volumes[0]` 里，`name` 为空字符串。**

## 5. 章节 Chapter

### `GET /api/chapters/{id}` — 读章节

最重要的接口之一。

```jsonc
{
  "id": "ch_01H8AAA",
  "bookId": "bk_01H8XYZ",
  "relPath": "剑来/07-正文/第001章-雪夜.md",
  "title": "第一章 雪夜",
  "volume": "第一卷 少年游",
  "order": 1,
  "status": "draft",
  "pov": "陈平安",
  "characters": ["ent_chenpingan", "ent_ningyao"],
  "locations": ["ent_nipingxiang"],
  "storyTime": "第三天 · 夜",
  "summary": "陈平安在城头遇见宁姚，得知小镇即将封闭。",
  "wordCount": 3204,
  "contentHash": "a3f9c2e1b8d74056",
  "content": "　　他站在城头，看雪落下来。\n\n　　\"你确定要走？\"身后有人问。\n\n　　他没有回头。\n",
  "createdAt": "2026-09-18T10:00:00+08:00",
  "updatedAt": "2026-09-18T12:30:00+08:00"
}
```

**`content` 是原始 Markdown 文本**（不含 frontmatter，frontmatter 已解析成上面的字段）。前端负责把 `content` 解析成 ProseMirror 文档。

**`contentHash` 必须原样回传**给保存接口，用于冲突检测。

### `PUT /api/chapters/{id}/content` — 保存正文

```jsonc
// 请求
{
  "content": "　　他站在城头，看雪落下来。\n\n　　他没有回头。\n",
  "contentHash": "a3f9c2e1b8d74056",
  "createSnapshot": false
}

// 响应 200
{
  "id": "ch_01H8AAA",
  "contentHash": "7d2e9f1a4c6b8035",
  "wordCount": 3198,
  "savedAt": "2026-09-18T12:35:12+08:00",
  "snapshotCreated": false
}
```

**冲突检测**：后端比对请求里的 `contentHash` 与磁盘文件的当前哈希。

- 一致 → 正常写入
- 不一致 → 说明文件被外部修改过（作者用 Typora 改了），返回 `409 CONTENT_HASH_MISMATCH`，响应体带磁盘上的最新内容，让前端弹窗让作者选择"用我的"还是"用文件里的"

**幂等**：若请求的 `content` 与磁盘内容完全一致，直接返回成功，不写文件、不更新 `updated_at`。

**原子写入**：写临时文件 → 原子重命名。绝不直接覆盖。

### `POST /api/chapters` — 新建章节

```jsonc
// 请求
{
  "bookId": "bk_01H8XYZ",
  "title": "第三章 泥瓶巷",
  "volume": "第一卷 少年游",
  "afterChapterId": "ch_01H8BBB",   // 插在这章之后，null 表示追加到末尾
  "content": ""
}

// 响应 201，返回与 GET /api/chapters/{id} 相同的结构
```

后端负责生成文件名（`第003章-泥瓶巷.md`）、写入 frontmatter、插入到正确位置。

### 其余

| 方法 | 路径 | 说明 |
|---|---|---|
| `PATCH` | `/api/chapters/{id}` | 改元数据（title / status / volume / pov / storyTime） |
| `DELETE` | `/api/chapters/{id}` | 删除章节（移到 `.yimo/trash/`，可恢复 30 天） |
| `POST` | `/api/chapters/{id}/move` | 移动到别的卷 / 改变顺序 |
| `POST` | `/api/chapters/batch-move` | 批量重排（拖拽后一次提交） |
| `GET` | `/api/chapters/{id}/snapshots` | 快照列表 |
| `POST` | `/api/chapters/{id}/snapshots` | 手动创建快照 |
| `POST` | `/api/chapters/{id}/restore` | 回滚到指定快照 |

`POST /api/chapters/{id}/move` 请求体：

```jsonc
{ "targetVolume": "第二卷 山水郎", "beforeChapterId": "ch_01H8CCC" }
```

## 6. 批注 Review

### `GET /api/chapters/{id}/reviews` — 章节批注

```jsonc
{
  "chapterId": "ch_01H8AAA",
  "contentHash": "7d2e9f1a4c6b8035",
  "items": [
    {
      "id": "rv_01H8DDD",
      "kind": "typo",
      "severity": "error",
      "anchor": {
        "from": 120, "to": 124,
        "quote": "在说一遍",
        "prefix": "他站在城头，看雪落下来。我说",
        "suffix": "，你听不听？"
      },
      "message": "「在」应为「再」",
      "suggestions": ["再"],
      "status": "pending",
      "source": "rule",
      "ruleId": "zai-vs-zai",
      "confidence": 0.95,
      "createdAt": "2026-09-18T12:36:00+08:00"
    }
  ],
  "summary": { "pending": 15, "accepted": 3, "rejected": 1, "orphaned": 0 }
}
```

**`contentHash` 让前端知道这批批注是基于哪个版本的正文算的**。若与当前正文的 hash 不一致，前端要先跑锚点重定位，再把新位置回写。

### `PATCH /api/reviews/{id}` — 更新批注状态

```jsonc
// 请求
{
  "status": "accepted",
  "appliedText": "再说一遍",        // 接受时，实际替换的文本
  "anchor": { "from": 118, "to": 122 }   // 重定位后的新位置
}

// 响应 200
{
  "id": "rv_01H8DDD",
  "status": "accepted",
  "chapterContentHash": "9e1b3c5a7f2d4068"   // 接受后产生的新版本
}
```

**接受批注会修改正文**，所以这个接口会触发一次文件写入和 `contentHash` 更新。前端拿返回值更新本地的 hash。

### `POST /api/reviews/batch` — 批量操作

```jsonc
// 请求
{
  "ids": ["rv_01H8DDD", "rv_01H8EEE", "rv_01H8FFF"],
  "action": "accept" | "reject" | "ignore-rule",
  "ruleId": "zai-vs-zai"     // action = ignore-rule 时必填
}

// 响应
{
  "succeeded": 2,
  "failed": [{ "id": "rv_01H8FFF", "reason": "ANCHOR_ORPHANED" }],
  "chapterContentHash": "9e1b3c5a7f2d4068"
}
```

**批量接受是一个事务**，要么全成功要么全回滚。部分失败时返回失败清单。

### 其余

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/reviews` | 手动创建批注（作者给自己留的备注） |
| `DELETE` | `/api/reviews/{id}` | 删除批注 |
| `GET` | `/api/libraries/{id}/reviews/orphaned` | 全书已失效批注 |

## 7. 校对 Proofread

### `POST /api/chapters/{id}/proofread` — 触发校对

```jsonc
// 请求
{
  "engine": "rule" | "llm" | "both",
  "scope": "chapter" | "selection",
  "selection": { "from": 0, "to": 500 },   // scope = selection 时必填
  "force": false                            // true 则忽略缓存强制重跑
}

// 响应 202
{
  "taskId": "task_01H8GGG",
  "engine": "both",
  "estimatedSeconds": 30,
  "cached": false
}
```

**立刻返回 `taskId`，不阻塞**。前端拿 `taskId` 去订阅 SSE 进度流。规则引擎的结果通常在 1 秒内返回，LLM 的结果流式返回。

### `GET /api/libraries/{id}/capabilities` — 查询能力状态

```jsonc
{
  "ruleEngine": { "available": true, "ruleCount": 12, "enabledRuleCount": 9 },
  "llm": {
    "available": true,
    "provider": "deepseek",
    "model": "deepseek-chat",
    "degraded": false
  },
  "python": { "available": false, "reason": "NOT_CONFIGURED" },
  "quota": { "dailyLimit": 2000000, "used": 340000, "resetAt": "2026-09-19T00:00:00+08:00" }
}
```

前端状态栏直接读这个接口显示当前能力状态。**降级必须可见**，不能让作者以为"检查过了没问题"。

## 8. 实体 Entity

### `GET /api/libraries/{id}/entities` — 实体列表

```jsonc
// ?type=character&q=陈&page=0&size=50

{
  "items": [
    {
      "id": "ent_chenpingan",
      "type": "character",
      "name": "陈平安",
      "aliases": ["平安", "小平安", "隐官"],
      "status": "alive",
      "filePath": "剑来/03-人物/陈平安.md",
      "firstAppear": "ch_01H8AAA",
      "mentionCount": 1284,
      "attributes": { "境界": "十四境", "本命飞剑": "十五" },
      "updatedAt": "2026-09-18T12:00:00+08:00"
    }
  ],
  "page": 0, "size": 50, "total": 12, "hasNext": false
}
```

### `GET /api/entities/{id}` — 实体详情

```jsonc
{
  "id": "ent_chenpingan",
  "type": "character",
  "name": "陈平安",
  "aliases": ["平安", "小平安", "隐官"],
  "status": "alive",
  "filePath": "剑来/03-人物/陈平安.md",
  "attributes": { "境界": "十四境" },
  "relations": [
    { "targetId": "ent_ningyao", "targetName": "宁姚", "type": "道侣", "note": "" },
    { "targetId": "ent_qijingchun", "targetName": "齐静春", "type": "师徒", "note": "代师收徒" }
  ],
  "mentions": [
    { "chapterId": "ch_01H8AAA", "chapterTitle": "第一章 雪夜", "kind": "appear", "count": 23 },
    { "chapterId": "ch_02BBBB", "chapterTitle": "第二章 泥瓶巷", "kind": "mentioned", "count": 4 }
  ],
  "body": "## 外貌\n\n## 性格\n\n..."
}
```

**`body` 是实体卡文件里 frontmatter 之后的自由文本**，前端渲染成可编辑的区域。

### `POST /api/entities/merge` — 合并实体

人工确认的实体消解。

```jsonc
// 请求
{
  "sourceId": "ent_pingan",      // 被合并掉的
  "targetId": "ent_chenpingan",  // 保留的
  "mergeAliases": true,           // 把 source 的名字加进 target 的别名
  "redirectMentions": true        // 把 source 的所有出现记录转到 target
}

// 响应
{
  "target": { /* 合并后的实体 */ },
  "mergedMentions": 47,
  "deletedFile": "剑来/03-人物/平安.md"
}
```

**这个接口只能由作者显式调用**，Agent 不能自动合并。后台会把被合并的文件移到 `.yimo/trash/`，可恢复。

### 其余

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/entities` | 新建实体（生成 md 文件） |
| `PATCH` | `/api/entities/{id}` | 修改实体（frontmatter 字段或 body） |
| `DELETE` | `/api/entities/{id}` | 删除实体 |
| `GET` | `/api/libraries/{id}/relations/graph` | 关系图数据（节点 + 边） |
| `GET` | `/api/libraries/{id}/entities/pending` | 待确认的实体提案 |
| `POST` | `/api/entities/pending/{id}/confirm` | 确认提案 |

## 9. 搜索 Search

### `GET /api/libraries/{id}/search` — 搜索

```jsonc
// ?q=陈平安 一剑&mode=keyword&scope=content&page=0&size=20
// mode: keyword | semantic

{
  "items": [
    {
      "chapterId": "ch_01H8AAA",
      "chapterTitle": "第一章 雪夜",
      "volume": "第一卷 少年游",
      "relPath": "剑来/07-正文/第001章-雪夜.md",
      "snippet": "...<em>陈平安</em>握着那柄锈剑，<em>一剑</em>劈开了山门...",
      "highlights": [{ "start": 3, "end": 6 }, { "start": 15, "end": 17 }],
      "score": 12.4
    }
  ],
  "page": 0, "size": 20, "total": 87, "hasNext": true,
  "mode": "keyword",
  "tookMs": 42
}
```

`snippet` 里的 `<em>` 是高亮标记，**后端只标记位置，不返回 HTML 字符串**——`highlights` 数组给出精确的字符区间，前端自己渲染。避免 XSS 和后端写前端代码。

`mode=semantic` 时走后端 → Python 服务。Python 未启用时该模式返回 `400 PYTHON_NOT_AVAILABLE`，前端隐藏入口。

## 10. AI 任务 Task

### `GET /api/tasks/{id}` — 任务状态

```jsonc
{
  "id": "task_01H8GGG",
  "kind": "proofread",
  "status": "running",
  "chapterId": "ch_01H8AAA",
  "model": "deepseek-chat",
  "progress": { "current": 2, "total": 3, "message": "正在分析第 2 批（共 3 批）" },
  "partialResult": { "reviewCount": 9 },
  "usage": { "inputTokens": 4200, "outputTokens": 380 },
  "createdAt": "2026-09-18T12:36:00+08:00",
  "finishedAt": null,
  "error": null
}
```

### `GET /api/tasks/{id}/stream` — 任务进度流（SSE）

```
event: progress
data: {"current":2,"total":3,"message":"正在分析第 2 批（共 3 批）"}

event: review
data: {"id":"rv_01H8DDD","kind":"typo","anchor":{...},"message":"...","suggestions":["再"]}

event: done
data: {"reviewCount":12,"usage":{"inputTokens":6400,"outputTokens":560}}
```

**边算边推**。LLM 每返回一条批注就推一个 `review` 事件，前端立即上屏波浪线，作者不用等全部算完。这是体验的关键——30 秒的等待和"3 秒后开始陆续出现结果"是两个感受。

### 其余

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/tasks` | 任务列表（`?status=queued,running`） |
| `DELETE` | `/api/tasks/{id}` | 取消任务 |

## 11. Agent 对话 Chat

### `POST /api/threads/{id}/messages` — 发送消息（SSE 流式）

```jsonc
// 请求
{
  "content": "帮我看看第 41 章和第 28 章有没有矛盾",
  "context": { "chapterId": "ch_41XXXX" }   // 可选，当前打开的章节
}
```

SSE 响应：

```
event: delta
data: {"text":"我"}

event: delta
data: {"text":"查到"}

event: tool
data: {"name":"searchText","args":{"query":"左手 持剑"},"status":"running"}

event: tool
data: {"name":"searchText","status":"done","summary":"找到 3 处"}

event: proposal
data: {"kind":"review","id":"rv_01H8HHH","title":"第 41 章与第 28 章矛盾","confidence":0.85,"evidence":["ch_28YYYY"]}

event: done
data: {"threadId":"th_01H8III","messageId":"msg_01H8JJJ","usage":{"inputTokens":18400,"outputTokens":920}}
```

事件类型：

| event | 说明 |
|---|---|
| `delta` | 文本增量，前端追加到气泡 |
| `tool` | 工具调用状态，前端显示"正在检索正文…" |
| `proposal` | 产出一个提案，前端加入待确认区 |
| `error` | 出错 |
| `done` | 结束，附带用量统计 |

**`tool` 事件必须推给前端。** 让作者看到 AI 正在干什么，比一个转圈的 loading 强得多，也能让作者判断它是不是在瞎搜。

### 其余

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/libraries/{id}/threads` | 会话列表 |
| `POST` | `/api/libraries/{id}/threads` | 新建会话 |
| `GET` | `/api/threads/{id}/messages` | 历史消息 |
| `DELETE` | `/api/threads/{id}` | 删除会话 |
| `GET` | `/api/proposals` | 当前待确认的提案（内存中） |

## 12. 设置 Setting

### `GET /api/settings` — 读取设置

```jsonc
{
  "models": [
    {
      "id": "cfg_01H8KKK",
      "provider": "openai-compat",
      "label": "DeepSeek",
      "baseUrl": "https://api.deepseek.com",
      "apiKey": "sk-****cdef",        // 永远不回传完整 key
      "models": ["deepseek-chat", "deepseek-reasoner"],
      "isDefault": true
    }
  ],
  "profile": "balanced",
  "routes": { "PROOFREAD": "deepseek-chat", "CONSISTENCY": "deepseek-reasoner" },
  "proofread": { "onSave": true, "ruleEngine": true, "llm": false },
  "autoSnapshot": { "enabled": true, "intervalMinutes": 10, "keep": 50 },
  "quota": { "dailyTokenLimit": 2000000 },
  "python": { "enabled": false, "baseUrl": "http://127.0.0.1:8000" },
  "ui": { "fontSize": 16, "lineHeight": 1.8, "theme": "light" }
}
```

**API Key 永不回传完整值**，只回传后 4 位用于辨认。前端提交新 key 时传完整值，提交时传 `null` 或省略表示"不修改"。

### `POST /api/settings/test-model` — 测试连通性

```jsonc
// 请求
{ "provider": "openai-compat", "baseUrl": "https://api.deepseek.com", "apiKey": "sk-xxx", "model": "deepseek-chat" }

// 响应
{ "ok": true, "latencyMs": 842, "modelEcho": "deepseek-chat", "message": "连接正常" }
```

失败时返回 `422` 并带具体原因（key 无效 / 额度不足 / 网络不通 / 模型名不存在）。**不做泛化的"连接失败"**——每种原因对应完全不同的处理动作。

### 其余

| 方法 | 路径 | 说明 |
|---|---|---|
| `PUT` | `/api/settings` | 更新设置 |
| `GET` | `/api/settings/models?configId=` | 拉取该配置下的可用模型列表 |
| `POST` | `/api/settings/export` | 导出设置（不含 key） |
| `POST` | `/api/settings/import` | 导入设置 |

## 13. 统计 Stat

### `GET /api/libraries/{id}/stats/writing` — 写作统计

```jsonc
// ?from=2026-08-01&to=2026-09-18&granularity=day

{
  "totalWords": 1284000,
  "todayWords": 3204,
  "avgDailyWords": 2840,
  "streakDays": 23,
  "series": [
    { "date": "2026-09-16", "words": 3100, "durationMinutes": 95, "chaptersTouched": ["ch_01H8AAA"] },
    { "date": "2026-09-17", "words": 2800, "durationMinutes": 80, "chaptersTouched": ["ch_01H8AAA"] },
    { "date": "2026-09-18", "words": 3204, "durationMinutes": 110, "chaptersTouched": ["ch_02BBBB"] }
  ]
}
```

数据来自 `writing_stat` 表。这张表的内容**不在稿件里，丢了不可恢复**，所以要提供导出。

### `GET /api/libraries/{id}/stats/progress` — 进度统计

```jsonc
{
  "byStatus": { "draft": 120, "revising": 30, "done": 162 },
  "byVolume": [
    { "volume": "第一卷 少年游", "chapters": 42, "words": 168000, "status": "done" }
  ],
  "hookStats": { "open": 8, "resolved": 23, "abandoned": 2 },
  "reviewStats": { "pending": 45, "accepted": 890, "rejected": 120, "orphaned": 6 }
}
```

## 14. 导出 Export

### `POST /api/libraries/{id}/export` — 触发导出

```jsonc
// 请求
{
  "bookId": "bk_01H8XYZ",
  "format": "txt" | "md" | "docx" | "epub",
  "scope": { "type": "all" } | { "type": "volume", "volume": "第一卷 少年游" } | { "type": "range", "fromId": "ch_01H8AAA", "toId": "ch_20ZZZZ" },
  "options": {
    "includeTitle": true,
    "chapterSeparator": "\n\n",
    "tocDepth": 2
  }
}

// 响应 202
{ "taskId": "task_01H8LLL", "estimatedSeconds": 5 }
```

### `GET /api/exports/{taskId}/download` — 下载

任务完成后返回文件流：

```
Content-Type: application/octet-stream
Content-Disposition: attachment; filename="剑来.txt"; filename*=UTF-8''%E5%89%91%E6%9D%A5.txt
```

`filename*` 用 RFC 5987 编码，否则中文书名会乱码。

## 15. 系统 System

调用操作系统能力的接口。这些只在本地运行时有意义——亿墨的定位就是本机工具。

### `POST /api/system/pick-directory` — 弹出文件夹选择窗口

**为什么需要后端做这件事**：浏览器出于安全限制，不允许网页获取用户选择的绝对路径（`<input type="file" webkitdirectory>` 只给相对路径）。后端没有这个限制，可以直接调系统的文件夹选择对话框。

```jsonc
// 请求
{ "initialPath": "D:\\我的小说" }    // 可选，窗口打开时定位到哪

// 响应 200 — 用户选了
{ "picked": true, "path": "D:\\我的小说\\剑来" }

// 响应 200 — 用户点了取消
{ "picked": false, "path": null }
```

**用户取消不是错误**，所以返回 200 而不是 4xx，用 `picked: false` 区分。

**这个请求会阻塞**，直到用户选完或取消。前端必须把超时设长或者干脆不设（axios 里 `timeout: 0` 表示不超时）：

```ts
http.post('/system/pick-directory', { initialPath }, { timeout: 0 })
```

否则用户在窗口里翻文件夹超过 30 秒，请求就会提前失败。

| 错误码 | 场景 |
|---|---|
| `PICKER_UNSUPPORTED` | 没有图形界面（Docker、无头服务器）。前端应降级为手动输入 |
| `PICKER_BUSY` | 已经有一个选择窗口开着。防止连点弹出多个 |
| `PICKER_FAILED` | 窗口创建失败 |

## 16. 前后端类型对应

后端定义 DTO，前端手写对应的 TypeScript interface。**字段名保持完全一致**（都用 camelCase），不做任何转换——转换是 bug 的温床。

```java
// 后端
public record ChapterDetail(
    String id,
    String bookId,
    String relPath,
    String title,
    String volume,
    Integer order,
    String status,
    String pov,
    List<String> characters,
    List<String> locations,
    String storyTime,
    String summary,
    Integer wordCount,
    String contentHash,
    String content,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
```

```ts
// 前端
export interface ChapterDetail {
  id: string
  bookId: string
  relPath: string
  title: string
  volume: string
  order: number
  status: 'draft' | 'revising' | 'done'
  pov: string
  characters: string[]
  locations: string[]
  storyTime: string
  summary: string
  wordCount: number
  contentHash: string
  content: string
  createdAt: string   // ISO 8601
  updatedAt: string
}
```

**DTO 用 `record`，实体类用 `@Data`。** 两类东西，两种写法：

| | DTO（请求 / 响应） | 实体（跟数据库表对应） |
|---|---|---|
| 写法 | `public record ChapterDetail(...) {}` | `@Data public class Library {}` |
| 为什么 | 不可变、天然线程安全、Java 21 原生支持，不需要 Lombok | MyBatis-Plus 需要无参构造函数和 setter，record 做不到 |
| 样板代码 | 无 | Lombok 的 `@Data` 自动生成 getter/setter |

DTO 要传值，构造后不该再改；实体要从数据库读出来填值，必须能被改。这个区别决定了两者写法不同。

## 17. 开发顺序建议

接口多，但前后端可以按这个顺序并行推进，每一步都是可验证的：

| 阶段 | 后端做 | 前端做 | 验收 |
|---|---|---|---|
| 1 | `GET /api/libraries` `POST /api/libraries` | 书库列表页、添加书库弹窗 | 能添加一个文件夹 |
| 2 | `GET /api/libraries/{id}/tree` | 左侧卷章树 | 树上能列出所有章节 |
| 3 | `GET /api/chapters/{id}` `PUT /api/chapters/{id}/content` | 编辑器读写 | 能改字并保存到磁盘 |
| 4 | `POST /api/chapters` `DELETE` | 新建 / 删除章节 | 能建新章 |
| 5 | `POST /api/chapters/{id}/proofread` + SSE | 波浪线渲染 | 能看到错字标注 |
| 6 | `GET /api/chapters/{id}/reviews` `PATCH /api/reviews/{id}` | 批注面板、接受/拒绝 | 能接受建议并改正文 |
| 7 | `GET /api/libraries/{id}/entities` | 人物面板 | 能看人物列表 |
| 8 | `POST /api/threads/{id}/messages` + SSE | 对话面板 | 能跟 AI 对话 |

**阶段 3 是整个项目的分水岭**。它跑通意味着"作者能写、能存、数据不会丢"这条主链路成立。在此之前不要碰任何 AI 功能。
