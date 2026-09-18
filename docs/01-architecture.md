# 架构设计

## 1. 总体形态

亿墨是**本地服务 + 浏览器界面**。

```
启动脚本  →  Spring Boot 服务启动（localhost:18080）
          →  自动打开浏览器
          →  开始写作
```

用户视角：双击一个脚本（或 exe），浏览器弹出来，开始写。没有账号，没有云，没有安装向导。

**稿件永远在磁盘上，永远不在服务器上。** 这里的"服务器"指的是亿墨自己启动的本地进程——它只读写你指定的那个文件夹，不往任何远端发送数据。

## 2. 分层

```
┌──────────────────────────────────────────────────────┐
│  浏览器 · Vue 3 + TypeScript                          │
│  ┌──────────┬──────────┬──────────┬───────────────┐  │
│  │ 视图层    │ 状态层    │ 编辑器    │ API 客户端     │  │
│  │ Vue 组件  │ Pinia    │ Tiptap   │ HTTP + SSE    │  │
│  └──────────┴──────────┴──────────┴───────────────┘  │
└───────────────────────┬──────────────────────────────┘
                        │ HTTP REST + SSE
┌───────────────────────▼──────────────────────────────┐
│  Spring Boot · Java 21                                │
│  ┌──────────┬──────────┬──────────┬───────────────┐  │
│  │Controller│ Service  │ Domain   │ AI 编排        │  │
│  │ REST 接口 │ 业务逻辑  │ 领域模型  │ Spring AI     │  │
│  └──────────┴──────────┴──────────┴───────────────┘  │
│  ┌────────────────────┬─────────────────────────┐    │
│  │ Repository         │ Storage                 │    │
│  │ MyBatis-Plus       │ Java NIO（书库文件）     │    │
│  └────────────────────┴─────────────────────────┘    │
└──────┬────────────────────────────┬──────────────────┘
       │ JDBC                       │ HTTP（预留）
┌──────▼─────────┐        ┌─────────▼─────────────────┐
│  MySQL 8       │        │  Python 服务（后置）        │
│  索引·批注·统计 │        │  FastAPI · 语义检索 · 本地模型│
└────────────────┘        └───────────────────────────┘
       │
       │ Java NIO
┌──────▼──────────────────────────────────────────────┐
│  书库文件夹（稿件本体）                                │
│  明文 Markdown + YAML frontmatter                    │
└─────────────────────────────────────────────────────┘
```

## 3. 职责边界（最重要的一节）

**前端负责**：显示、编辑、交互、编辑器内的位置计算。

**后端负责**：一切持久化、一切文件读写、一切 AI 调用、一切索引与检索。

**传输格式：Markdown 纯文本。**

这是最关键的一条约定。前后端之间传的不是 ProseMirror 的 JSON 结构，而是 Markdown 文本本身。

```
前端                                    后端
ProseMirror Doc                         磁盘 .md 文件
     │                                       │
     │ serialize ────────────────▶ HTTP ────▶│ write
     │                                       │
     │ parse     ◀──────────────── HTTP ─────│ read
```

好处：

- 后端完全不需要懂 ProseMirror，它只处理文本和文件。职责清晰。
- 后端可以独立测试——给一段 Markdown，断言输出的检查结果，不需要跑浏览器。
- 将来若换编辑器（比如加 Markdown 源码模式），后端一行不用改。
- Markdown 是唯一真相源这条原则，在传输层得到贯彻。

代价：每次加载章节要解析一次 Markdown。3000 字的章节解析是毫秒级，忽略不计。

## 4. 书库存储

**Java NIO 直接读写磁盘**，没有浏览器沙箱，没有权限对话框，没有兼容性问题。

```java
@Service
public class LibraryStorage {
    private final Path libraryRoot;

    public String readChapter(Path relPath) throws IOException {
        return Files.readString(libraryRoot.resolve(relPath), StandardCharsets.UTF_8);
    }

    public void writeChapter(Path relPath, String markdown) throws IOException {
        Path target = libraryRoot.resolve(relPath);
        Files.createDirectories(target.getParent());
        // 先写临时文件再原子重命名，避免写入过程中崩溃导致稿件损坏
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, markdown, StandardCharsets.UTF_8);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING,
                   StandardCopyOption.ATOMIC_MOVE);
    }
}
```

**原子写入是硬要求**。稿件是作者几个月的心血，绝不能因为一次意外断电变成半截文件。所有写操作统一走「写临时文件 → 原子重命名」这条路径。

**路径安全**：所有涉及路径的接口必须校验解析后的绝对路径是否仍在书库根目录内，防止 `../../` 越权访问。

```java
Path resolved = libraryRoot.resolve(input).normalize();
if (!resolved.startsWith(libraryRoot)) {
    throw new ForbiddenException("路径越界");
}
```

**为什么不用数据库存正文**：正文存进 MySQL，作者就没法用 Git 管理、没法用别的编辑器打开、没法直接看到自己的稿子。存文件是产品承诺，不是技术选择。

## 5. 数据库设计

MySQL 存**所有派生数据和服务级数据**。原则：**数据库里的任何东西都可以从书库文件夹重建。**

### 表清单

| 表 | 用途 | 丢失后果 |
|---|---|---|
| `library` | 书库注册表（路径、最近打开） | 重新添加书库 |
| `setting` | 全局设置、模型配置 | 重新填写 |
| `chapter` | 章节索引（标题、卷、序号、字数、内容哈希） | 重新扫描重建 |
| `chapter_content` | 正文副本 + 全文索引 | 重新扫描重建 |
| `entity` | 实体索引（人物、地点、物品、组织、术语） | 重新抽取 |
| `relation` | 实体关系 | 重新抽取 |
| `mention` | 实体在哪些章节出现 | 重新扫描 |
| `summary` | 章节摘要链 | 重新生成（需要调模型） |
| `hook` | 伏笔状态 | 重新生成 |
| `review` | 批注 | 重新校对 |
| `ai_task` | AI 任务队列与结果缓存 | 重新调用（花钱） |
| `chat_thread` / `chat_message` | 对话记录 | **不可恢复** |
| `writing_stat` | 写作统计（每日字数） | **不可恢复** |

最后两行是例外——它们是用户行为数据，不在稿件里，丢了就没了。**这两张表要提供导出功能**，且随书库一起备份。

### 核心表结构

```sql
-- 章节索引
CREATE TABLE chapter (
  id            VARCHAR(32)     NOT NULL PRIMARY KEY COMMENT 'ULID',
  library_id    BIGINT       NOT NULL,
  rel_path      VARCHAR(500) NOT NULL COMMENT '相对书库根的路径',
  title         VARCHAR(200),
  volume        VARCHAR(200),
  sort_order    INT,
  status        VARCHAR(20)  DEFAULT 'draft',
  word_count    INT          DEFAULT 0,
  content_hash  CHAR(16)     COMMENT '内容哈希，用于判断是否需要重新校对',
  pov           VARCHAR(200),
  story_time    VARCHAR(200),
  updated_at    DATETIME,
  UNIQUE KEY uk_path (library_id, rel_path),
  KEY idx_order (library_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 正文副本 + 全文索引
CREATE TABLE chapter_content (
  chapter_id  VARCHAR(32)   NOT NULL PRIMARY KEY,
  body        MEDIUMTEXT NOT NULL,
  FULLTEXT KEY ft_body (body) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**`WITH PARSER ngram` 是必须的。** MySQL 默认的全文分词器按空格切词，对中文完全无效——一整段中文会被当成一个词。ngram 分词器按 N 字符滑窗切分，是 MySQL 支持中文检索的唯一方式。

`ngram_token_size` 默认是 2，即按双字切分。对中文小说检索合适，不需要改。

```sql
-- 实体
CREATE TABLE entity (
  id           VARCHAR(32)     NOT NULL PRIMARY KEY,
  library_id   BIGINT       NOT NULL,
  type         VARCHAR(20)  NOT NULL COMMENT 'character/location/item/org/term',
  name         VARCHAR(200) NOT NULL,
  aliases      JSON         COMMENT '别名数组',
  file_path    VARCHAR(500) COMMENT '实体卡文件的相对路径',
  status       VARCHAR(20)  COMMENT 'alive/dead/missing/unknown',
  first_appear VARCHAR(32),
  attributes   JSON         COMMENT '自由键值对',
  KEY idx_library_type (library_id, type),
  KEY idx_name (library_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 批注
CREATE TABLE review (
  id            VARCHAR(32)     NOT NULL PRIMARY KEY,
  chapter_id    VARCHAR(32)     NOT NULL,
  kind          VARCHAR(20)  COMMENT 'typo/grammar/style/consistency/suggestion',
  severity      VARCHAR(20),
  anchor_from   INT,
  anchor_to     INT,
  anchor_quote  VARCHAR(500),
  anchor_prefix VARCHAR(100),
  anchor_suffix VARCHAR(100),
  message       TEXT,
  suggestions   JSON,
  status        VARCHAR(20)  DEFAULT 'pending',
  source        VARCHAR(20)  COMMENT 'rule/llm',
  rule_id       VARCHAR(50),
  created_at    DATETIME,
  KEY idx_chapter_status (chapter_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- AI 任务与缓存
CREATE TABLE ai_task (
  id            VARCHAR(32)    NOT NULL PRIMARY KEY,
  library_id    BIGINT,
  chapter_id    VARCHAR(32),
  kind          VARCHAR(30) COMMENT 'proofread/extract/summary/consistency/rewrite/chat',
  status        VARCHAR(20) COMMENT 'queued/running/done/failed/cancelled',
  model         VARCHAR(100),
  input_hash    CHAR(32)    COMMENT '输入哈希，命中则直接返回缓存结果',
  payload       JSON,
  result        JSON,
  input_tokens  INT DEFAULT 0,
  output_tokens INT DEFAULT 0,
  error         TEXT,
  created_at    DATETIME,
  finished_at   DATETIME,
  KEY idx_status (status),
  KEY idx_cache (kind, input_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**`input_hash` 是省钱的关键**。同一章节、同一任务类型、内容没变 → 直接返回上次结果，不发请求。AI 调用是唯一有边际成本的东西，缓存必须做在最前面。

### 索引重建

数据库是缓存。以下情况触发重建：

- 首次添加书库 → 全量扫描
- 检测到文件 mtime 变化 → 增量重建该文件
- 用户手动点"重建索引"
- 数据库里的 `library` 记录还在但表数据丢失 → 全量重建

**重建必须在后台线程执行，带进度条，可中断。** 100 万字书库的全量扫描约 10-30 秒，不能让用户对着白屏等。

## 6. 文稿模型

**Markdown 是唯一真相源，ProseMirror JSON 是前端的运行时表示。**

```
磁盘 .md  ──HTTP──▶  parse  ──▶  ProseMirror Doc
                                      │ 编辑
                                      ▼
磁盘 .md  ◀──HTTP──  serialize ◀── ProseMirror Doc
```

不采用"前端存 JSON、后端存 MD"的双写方案——两份数据必然漂移，且作者能编辑的只有 MD。

**往返保真度问题**：Markdown 表达能力弱于 ProseMirror，序列化会丢失格式信息。约束：

- 正文只允许 Markdown 可表达的节点：段落、加粗、斜体、引用、分隔线、列表。不引入表格、脚注、自定义节点。
- 因此**编辑器工具栏刻意做窄**。小说不需要表格。
- 空闲时执行往返自检（`serialize(parse(md)) === md`），失败则记录警告并在状态栏提示。这是前端测试的第一优先级。

**批注不写进正文**。错字、语病、Agent 建议全部存在 MySQL 的 `review` 表，用文本锚点定位。

**中文排版**：段首缩进用全角空格 `　　`（两个 U+3000）显式写出，不依赖 CSS。这样导出的 TXT 在任意阅读器里都是正确的。编辑器里以样式呈现，落盘时写全角空格。

## 7. 前端状态管理

Pinia（Vue 官方方案）：

| Store | 职责 |
|---|---|
| `libraryStore` | 书库列表、当前书库 |
| `projectStore` | 当前项目、卷章树、当前章节 |
| `docStore` | Tiptap 实例、脏标记、保存状态 |
| `kbStore` | 实体、关系、提及位置 |
| `reviewStore` | 批注列表、筛选状态 |
| `agentStore` | 对话历史、运行中任务、提案队列 |
| `settingsStore` | 模型配置、偏好、快捷键 |

**不引入 Vuex**。Pinia 是 Vue 3 的官方推荐，组合式 API 风格，TypeScript 支持更好。

**所有 store 不持久化到浏览器**。localStorage 只存"当前打开的书库 id"这一个值——其他一切数据以后端为准。避免浏览器和服务端两份状态打架。

## 8. 批注锚定

批注指向正文的一段文字，但正文会不断被修改。

```ts
interface Anchor {
  from: number          // 文档偏移量（快速路径）
  to: number
  quote: string         // 原文
  prefix: string        // 前 32 字符
  suffix: string        // 后 32 字符
}
```

**重定位在前端做**，因为只有前端持有 ProseMirror 文档。算法按顺序尝试，命中即停：

1. **精确命中** — `doc.slice(from, to).text === quote`，直接用。
2. **邻域搜索** — 在 `[from-200, to+200]` 窗口内用 `indexOf` 找 `quote`。
3. **指纹匹配** — 全局搜 `prefix + quote + suffix`。
4. **局部指纹** — 全局搜 `quote`，取编辑距离最小者，且相似度 > 0.85。
5. **失效** — 以上皆失败，标记为 `orphaned`，收进"已失效批注"折叠区，保留原文供人工判断。**不静默删除。**

每次成功重定位后，把新的 `from/to` 通过接口回写后端。

**中文输入法处理**：输入法组合态（composition）期间不要重算锚点。在 `compositionend` 事件之后再统一计算，否则正在拼的字会把所有位置算错。

## 9. 校对引擎：两级架构

```
前端 debounce 1.5s ──▶ 保存章节
                          │
                          ├─▶ 后端规则引擎（Java，~20ms）
                          │     └─▶ 立即返回批注，前端上屏波浪线
                          │
                          └─▶ LLM 校对（按需 / 保存时 / 手动）
                                └─▶ SSE 流式返回，逐条上屏
```

**规则引擎放在后端 Java**，理由：

- 需要中文分词配合的规则（如"他/她混用"需要判断指代对象），Java 侧有 HanLP 可用
- 规则可以存数据库，方便扩展和统计命中率
- 前端保持轻量，只负责渲染
- 本地服务往返延迟 < 5ms，实时性没有损失

**规则引擎的设计要求**：**误报率必须低于 2%**。宁可漏，不可扰。规则一旦有争议就默认关闭，作者可在设置里手动开启。

规则覆盖：

| 规则 | 例 |
|---|---|
| 的/地/得 | 「快速的跑」→「快速地跑」 |
| 在/再 | 「我在说一遍」→「我再说一遍」 |
| 做/作 | 「做为」→「作为」 |
| 那/哪 | 「那怎么办」→「哪怎么办」 |
| 他/她/它 混用 | 同段内指代同一人却换用 |
| 标点配对 | 引号、括号不成对 |
| 中英标点混用 | 中文语境里出现 `,` `.` `?` |
| 省略号 | `...` / `。。。` → `……` |
| 叠字误写 | 「的的」「了了」 |
| 全角空格混入 | 段中出现孤立全角空格 |
| 数字规范 | 同段落阿拉伯数字与汉字数字混用 |
| 重复词 | 相邻 4 字窗口内重复 2 字以上 |

**LLM 校对**只处理规则引擎抓不到的语义级问题——搭配不当、成分残缺、指代不明、语序混乱、逻辑跳跃。分批提交（每批约 1500 字）并带前后文，要求返回严格 JSON。

## 10. 接口设计

REST 风格，全部挂在 `/api` 下。

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/libraries` | 书库列表 |
| `POST` | `/api/libraries` | 添加书库（传路径） |
| `GET` | `/api/libraries/{id}/tree` | 卷章树 |
| `GET` | `/api/chapters/{id}` | 读章节（返回元数据 + Markdown） |
| `PUT` | `/api/chapters/{id}/content` | 保存正文 |
| `POST` | `/api/chapters` | 新建章节 |
| `DELETE` | `/api/chapters/{id}` | 删除章节 |
| `GET` | `/api/chapters/{id}/reviews` | 该章批注 |
| `POST` | `/api/chapters/{id}/proofread` | 触发校对 |
| `GET` | `/api/search?q=` | 全文检索 |
| `GET` | `/api/entities?type=` | 实体列表 |
| `POST` | `/api/agent/chat` | 对话（SSE 流式） |
| `GET` | `/api/tasks/{id}/stream` | 任务进度（SSE） |

**流式响应用 SSE 不用 WebSocket**。AI 输出是单向的，SSE 更简单、能自动重连、走普通 HTTP 不用协议升级。WebSocket 的双向能力在这里没有用武之地。

**保存接口要幂等**。前端因为网络抖动重试时，不能产生两份数据。用 `content_hash` 判断：内容没变则直接返回成功，不重复写文件。

## 11. 启动与打包

```bash
# 开发时
mvn spring-boot:run          # 后端 :18080
npm run dev                  # 前端 :5173，代理 /api 到 18080

# 发布时
mvn clean package            # 产出 yimo.jar（内嵌前端静态资源）
java -jar yimo.jar           # 启动，自动打开浏览器
```

**前端构建产物打进 jar 的 `static/` 目录**，发布时只有一个文件。用户拿到 `yimo.jar` + 一个启动脚本（`.bat` / `.sh`），双击即可。

**首次启动引导**：检测 MySQL 连接 → 若失败则弹出配置界面（主机、端口、库名、用户名、密码）→ 测通后自动建表。不能要求用户手写 SQL。

**数据库的替代方案**：为了让用户不必先装 MySQL 才能试用，提供 H2 内嵌模式作为兜底（配置项切换）。学习用 MySQL，演示用 H2。

## 12. 性能预算

| 操作 | 目标 |
|---|---|
| 服务启动 → 可编辑 | < 3s（含 MySQL 连接） |
| 打开书库 → 可编辑 | < 1.5s（100 万字书库） |
| 章节切换 | < 150ms（含 HTTP 往返） |
| 输入延迟 | < 16ms（单帧） |
| 自动保存 | 输入停顿 1.5s 后落盘 |
| 规则校对上屏 | < 200ms（含 HTTP 往返） |
| 全文搜索 | < 500ms（100 万字，MySQL ngram 索引） |
| LLM 校对首字 | 取决于网络，本地模型 < 1s |

本地 HTTP 往返在 1-5ms 量级，可以忽略。真正需要盯的是 MySQL 查询和文件 IO。

## 13. 关键取舍

**为什么不用纯前端（浏览器直接读写文件夹）**：File System Access API 只有 Chromium 系支持，Firefox 和 Safari 用户拿不到核心承诺。走后端服务反而让所有浏览器都能用。加上后端有 MySQL 和中文 NLP 的支持，能力上限高得多。代价是"打开网页就能用"变成"启动服务再用"。

**为什么用 Spring Boot 而不是更轻的框架**：这是学习项目，Spring Boot 是国内后端岗位的事实标准。技术选型服务于学习目标。

**为什么规则引擎放后端而不是前端**：需要 HanLP 分词能力，且规则可入库、可统计。本地服务延迟可忽略。

**为什么稿件不存数据库**：存进数据库，作者就没法用 Git、没法用别的编辑器打开、没法直接看到自己的稿子。文件优先是产品承诺。

**为什么传输 Markdown 而不是 ProseMirror JSON**：后端不需要懂编辑器内部结构，职责清晰，可独立测试，换编辑器不返工。

**为什么用 SSE 不用 WebSocket**：AI 输出是单向流，SSE 更简单、自动重连、无协议升级。双向能力在这里是负担。

**为什么所有 Agent 产出都是提案**：作者把几十万字交给一个软件，软件必须可预测。一次静默改写造成的信任崩塌，一百个好功能都补不回来。
