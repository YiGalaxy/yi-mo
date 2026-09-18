# 技术选型

## 一览

### 前端

| 技术 | 版本 | 用途 |
|---|---|---|
| Vue | 3.5+ | 界面框架 |
| TypeScript | 5.x，`strict: true` | 语言 |
| Vite | 7.x | 构建工具 |
| Tiptap | 3.x | 富文本编辑器（Vue 3 绑定 `@tiptap/vue-3`） |
| Pinia | 3.x | 状态管理 |
| Vue Router | 4.x | 路由 |
| Naive UI | 2.x | 组件库 |
| Tailwind CSS | 4.x | 样式 |
| Axios | 1.x | HTTP 客户端 |

### 后端

| 技术 | 版本 | 用途 |
|---|---|---|
| Java | 21 (LTS) | 语言 |
| Spring Boot | 3.5+ | 应用框架 |
| Spring Web | — | REST 接口 |
| Spring AI | 1.x | 大模型调用与工具编排 |
| MyBatis-Plus | 3.5+ | 数据库访问 |
| MySQL | 8.0+ | 索引、批注、统计、任务 |
| H2 | 2.x | 内嵌兜底（免装 MySQL） |
| HanLP | portable 版 | 中文分词、人名识别 |
| Hutool | 5.x | 工具集（文件、字符串、ID） |
| SSE (`SseEmitter`) | — | 流式推送 |

### Python（后置，先留接口）

| 技术 | 用途 |
|---|---|
| FastAPI | HTTP 接口 |
| jieba / HanLP | 中文分词与 NER |
| sentence-transformers | 语义向量 |
| transformers / llama.cpp | 本地模型推理 |

## 关键选择论证

### 编辑器：Tiptap（Vue 3 绑定）

编辑器就是产品本身，这是最重要的一个选择。

| 候选 | 否决原因 |
|---|---|
| 纯 `<textarea>` + 自绘覆盖层 | 波浪线标注要自己算字符坐标。中文换行、全角字符、输入法组合态下的坐标计算极易出错 |
| CodeMirror 6 | 为代码设计。做 Markdown 源码编辑极好，但用户要的是所见即所得 |
| Lexical（Meta） | 性能优秀，但生态年轻，富文本插件基本要自己写，中文资料少 |
| Quill | 成熟但对自定义文档 schema 不友好，需要精确控制允许哪些节点 |
| **Tiptap** | ProseMirror 之上，插件生态最成熟，中文输入法经过大量生产验证，官方支持 Vue 3 |

**决定性理由**：ProseMirror 的 Decoration 系统天生就是为「不改动文档内容、只在上面叠标记」设计的。亿墨的批注系统正需要这个——错字标注、波浪线、建议高亮全部是 Decoration，不污染文档数据模型。

**许可证风险**：Tiptap 核心 MIT，但官方商业扩展 `@tiptap-pro/*`（协作、评论、AI、版本历史）**是付费的**。

- **批注系统必须自研**，基于 ProseMirror Decoration + 自定义 Anchor 结构（见 `01-architecture.md` §8）
- **严禁引入任何 `@tiptap-pro/*` 依赖**，CI 加检查

开源项目一旦混入商业依赖，后患无穷。

### 后端：Spring Boot 3 + Java 21

用户指定，也是国内后端岗位的事实标准。

**Java 21 而非 17**：虚拟线程（Virtual Threads）已转正。AI 调用大量是阻塞式 IO，虚拟线程让"一个请求一个线程"的简单模型能撑住高并发，不需要引入 WebFlux 的响应式复杂度。

**Spring AI 而非手写 HTTP 客户端**：Spring AI 是 Spring 官方 2024 年推出的 AI 框架，提供统一的 `ChatModel` 抽象，一套代码切换 OpenAI / Anthropic / Ollama。还支持工具调用（`@Tool` 注解）、结构化输出、流式响应、对话记忆。手写这些要几百行，且要处理各家 API 差异。

### 数据库：MySQL 8

**中文全文检索必须用 `WITH PARSER ngram`**：

```sql
FULLTEXT KEY ft_body (body) WITH PARSER ngram
```

MySQL 默认全文分词器按空格切词，对中文完全失效——整段中文会被当成一个词，搜什么都搜不到。ngram 分词器按 N 字符滑窗切分，是 MySQL 支持中文检索的唯一方式。这是本项目最容易踩的坑。

**H2 兜底**：为了让别人不必先装 MySQL 就能试用亿墨，提供 H2 内嵌模式作为配置项。学习环境用 MySQL，演示环境用 H2。MyBatis-Plus 让两者共用同一套 SQL。

### 前端组件库：Naive UI

Vue 3 生态里对 TypeScript 支持最好的组件库，全量 TS 编写，主题系统灵活，没有 Element Plus 的历史包袱。

（原设计的 shadcn/ui 是 React 专用的"复制代码到你项目里"模式，Vue 生态没有完全对等的方案。Naive UI 是当前最接近的选择。）

### 中文分词：HanLP（Java 版）

**这一条决定了 Python 服务不是必需的。**

HanLP 有完整的 Java 实现，分词、词性标注、命名实体识别、依存句法分析全部支持。规则引擎里需要分词配合的部分（比如"他/她混用"要判断指代对象）在 Java 侧就能完成。

Python 服务只留给 Java 确实做不了的事：语义向量检索、本地大模型推理、NLP 实验。

## 仓库组织

**单仓库（monorepo）。** 一个 GitHub 仓库 `yi-mo`，包含全部组件。

```
yi-mo/
├── backend/              Spring Boot 后端
├── frontend/             Vue 3 前端
├── python/               Python 服务（预留，暂空）
├── docs/                 设计文档
├── openapi/              API 契约
├── scripts/              构建与启动脚本
├── .github/workflows/    CI
├── VERSION               版本号（三端共用）
└── README.md
```

**为什么不拆仓库**：三部分是同一个产品的三个组件，版本必须同步——前端 v1.2 需要的接口，后端必须提供。拆开后每次改动都要在仓库之间对齐版本号，一个人开发纯粹是自找麻烦。

真正需要拆仓库的只有四种情况：有独立发布周期、有独立团队负责、仓库大到 CI 跑不动、有对外开源/闭源的区别。亿墨一条都不满足。

**「现在不拆」没有风险**——将来真要拆，`git filter-repo` 能带着完整历史把子目录拆出去。拆容易，合回去难。

**版本号**：三端共用一个版本，写在根目录 `VERSION` 文件里。发布时打 Git tag `v1.0.0`，Maven 和 npm 的版本从 tag 同步，避免三处对不上。

**分支策略**：`main` 保持可运行，功能开发走 `feat/xxx` 分支，做完合回 `main`。单人项目不需要更复杂的流程。

## 学习路径建议

这是学习项目，技术选型要服务于学习效率。建议顺序：

**第 1-2 周：Vue 3 基础**
- 组合式 API（`ref` `computed` `watch`）
- 单文件组件、`<script setup>`
- 先把 Tiptap 的官方 Vue 示例跑起来，改一改

**第 3-4 周：TypeScript + Pinia**
- 不要专门啃 TS 语法书，在写组件的过程中遇到再查
- Pinia 半天就能学会，重点理解"状态放哪一层"

**第 5-6 周：Spring Boot 基础**
- 从 `@RestController` 开始，先做几个不连数据库的接口
- 前端调通这几个接口，理解前后端怎么对上

**第 7-8 周：MyBatis-Plus + MySQL**
- 建表、写 Mapper、做查询
- 特别注意 ngram 全文索引，这是本项目唯一的 MySQL 难点

**第 9-10 周：Spring AI**
- 先做最简单的对话接口
- 再做工具调用（让模型能主动查章节）
- 最后做流式输出（SSE）

**之后：HanLP 和规则引擎**

不要按"学完一个再学下一个"的方式推进。**每个阶段都要做出一个能跑的东西**，哪怕很粗糙。看着浏览器里显示出自己写的数据，是唯一能撑下去的动力。

## 明确不用的技术

| 不用 | 原因 |
|---|---|
| Vuex | Vue 3 官方已推荐 Pinia，Vuex 处于维护模式 |
| Next.js / Nuxt | 不需要服务端渲染，前后端分离更清晰 |
| WebFlux | 虚拟线程已解决并发问题，响应式编程的学习成本不值得 |
| LangChain4j | Agent 循环自己写约 200 行，引入框架换来调试困难 |
| Electron / Tauri | 后端已经是 Java 服务，不需要再套一层壳 |
| 任何 `@tiptap-pro/*` | 商业许可，与 AGPL 开源定位冲突 |
| 向量数据库 | 人名是专有名词，MySQL 全文检索比向量更准，且少一个依赖 |
| Redis | 见下方专节论证 |
| 任何埋点 / 分析 SDK | 无遥测是产品承诺 |

### 为什么不用 Redis

**缓存不等于 Redis。** Redis 是"分布式缓存"这一种实现，它的全部价值集中在**多进程、多实例、高并发**这三个场景，亿墨一个都不沾。

| 亿墨需要什么 | 用 Redis 的问题 | 实际方案 |
|---|---|---|
| 缓存 AI 结果 | Redis 重启后缓存丢失（除非额外配 RDB/AOF），重新调用要花钱 | MySQL 表。**缓存需要持久化** |
| 章节内容缓存 | 单用户，数据在本地磁盘，读文件本来就是毫秒级 | 不需要 |
| 任务队列 | 单用户串行任务，且需重启后仍能看到失败任务 | `ThreadPoolExecutor` + MySQL 任务表 |
| 分布式锁 | 只有一个进程，没有锁竞争 | 不需要 |
| 会话 | 无账号体系 | 不需要 |

**最直接的冲突**：产品定位是「双击即用」，引入 Redis 意味着用户要装 MySQL + Redis 两个服务。每多一个服务，就多一层安装失败的可能。

**学习角度也不建议加。** 面试时「我用了 Redis」没有意义；「我评估过 Redis，因为单进程单用户、且缓存需要持久化，所以选了 MySQL 表」才是能讲两分钟的答案——它证明你在做技术判断，而不是在堆技术栈。

Redis 在亿墨里唯一有意义的时机是支持多人协作或云端部署，但那和「无账号、本地优先」的定位正面冲突，是另一个产品。

## 风险清单

| 风险 | 影响 | 应对 |
|---|---|---|
| MySQL ngram 全文检索配置复杂 | 中文搜索完全失效 | 建表 SQL 固化 ngram parser，写集成测试验证中文能搜到 |
| Tiptap Pro 扩展付费 | 批注系统需自研 | 已按自研设计，CI 检查依赖 |
| Markdown ↔ ProseMirror 往返丢信息 | 稿件被静默破坏 | 收窄语法、往返测试语料库、空闲自检 |
| 用户不想装 MySQL | 无法试用 | H2 内嵌兜底 |
| Spring AI 版本较新，API 可能变动 | 升级踩坑 | 锁定版本号，AI 调用封装在自己的一层 Service 里 |
| 提示词效果不稳定 | 校对质量波动 | 提示词独立存放、可被作者覆盖、建立评测语料 |
| 大书库性能 | 长篇作者体验崩塌 | M1 起就用 50 万字压测数据 |
| 中文输入法组合态 | 输入时批注错位 | Decoration 在 `compositionend` 后再计算 |
| 前后端字段对不上 | 低级但高频的联调问题 | 后端定义 DTO，前端手写对应的 `interface`，两端字段名保持一致 |

## 开发环境

```bash
# 依赖
Java 21 (Temurin 或 Oracle)
Maven 3.9+
Node.js 20+
MySQL 8.0+

# 后端
cd backend
mvn spring-boot:run              # http://localhost:8080

# 前端
cd frontend
npm install
npm run dev                      # http://localhost:5173，/api 代理到 8080
```

前后端分离开发时用 5173 端口，Vite 配置里把 `/api` 代理到后端。发布时前端构建产物拷进 `backend/src/main/resources/static/`，打成一个 jar。

## 部署

**一个 jar 文件。**

```bash
mvn clean package
java -jar target/yimo.jar
```

启动后自动打开浏览器。用户拿到 `yimo.jar` + 启动脚本（`.bat` / `.sh`），双击即用。

**不提供在线托管版本**。因为亿墨的设计是读写用户本机磁盘，托管版本无法做到这一点，且会立刻引出账号和数据存储问题，与产品的根本定位冲突。
