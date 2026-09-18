# Agent 设计

## 0. 一条铁律

**Agent 产出的一切都是提案，落盘前必须由作者确认。**

- 校对问题 → 行内波浪线 + 快速修复菜单，不自动改字
- 人物卡补全 → 侧栏 diff 视图，逐条接受 / 拒绝
- 润色改写 → 原文与改写并列对照
- 大纲生成 → 新建文件，不覆盖已有
- 续写 → 插入到光标处时标为"待定稿"，作者确认后才去掉标记

批量接受必须显式点击，且可 Ctrl+Z 整体撤销。

## 1. 模型接入

**基于 Spring AI 的 `ChatModel` 抽象，不手写 HTTP 客户端。**

Spring AI 已经为各家模型提供了统一的 `ChatModel` 实现，一套业务代码切换服务商。

```java
public interface ModelProvider {
    String id();
    String label();
    List<ModelInfo> listModels();
    ChatModel chatModel(ModelConfig config);
}
```

三个实现，全部是 Spring AI 内置支持的：

| 实现 | Spring AI 依赖 | 覆盖 |
|---|---|---|
| `OpenAiCompatProvider` | `spring-ai-openai` | DeepSeek、通义千问、Kimi、智谱、OpenAI、自建 vLLM / SGLang |
| `AnthropicProvider` | `spring-ai-anthropic` | Claude 系列 |
| `OllamaProvider` | `spring-ai-ollama` | 本地模型 |

配置项从数据库 `setting` 表读取，用户在设置页填写 `baseUrl` / `apiKey` / `model`。

**OpenAI 兼容是最推荐的选项**——一套协议通吃国内主流模型，便宜且中文语感好。

**注意 @Transactional 与 AI 调用的冲突**：AI 调用是秒级到分钟级的阻塞操作，绝不能放在数据库事务里，否则一个事务会占着连接几十秒，连接池很快耗尽。所有 AI 调用必须在事务外执行。

```java
// 错误示范
@Transactional
public void proofread(Long chapterId) {
    String text = repo.findContent(chapterId);
    String result = chatClient.prompt(text).call().content();  // 连接被占用几十秒
    repo.saveReviews(result);
}

// 正确做法：先查、再调、最后单独开事务写
public void proofread(Long chapterId) {
    String text = repo.findContent(chapterId);      // 事务已结束
    String result = chatClient.prompt(text).call().content();
    reviewService.saveAll(result);                  // 独立事务
}
```

**Key 存储**：明文存在 MySQL 的 `setting` 表。UI 上必须明说"密钥以明文保存在本机数据库中"。不假装加密——本地单用户场景下，前端加密是掩耳盗铃。

## 2. 模型路由

不同任务对模型能力的要求差两个数量级，硬编码一个模型是浪费。

```java
// 下面两个类各自独立成文件（Java 一个 .java 只能有一个 public 类）
// TaskKind.java
public enum TaskKind {
    PROOFREAD,      // 错字语病
    EXTRACT,        // 实体抽取
    SUMMARY,        // 章节摘要
    CONSISTENCY,    // 跨章一致性
    REWRITE,        // 润色改写
    GENERATE,       // 生成内容
    CHAT            // 对话
}

// ModelProfile.java
public record ModelProfile(String name, Map<TaskKind, String> routes) {}
```

默认三档：

| 档位 | PROOFREAD / EXTRACT / SUMMARY | CONSISTENCY / REWRITE / GENERATE / CHAT |
|---|---|---|
| `budget` | 最便宜的小模型 | 中档模型 |
| `balanced`（默认） | 中档模型 | 强模型 |
| `quality` | 强模型 | 强模型 + 长上下文 |

作者可自定义档位，也可逐任务指定。设置页显示每个任务的历史 token 消耗，让成本可见。

## 3. Agent 循环

**优先用 Spring AI 的自动工具执行**——`ChatClient` 会自动处理"模型请求调用工具 → 执行 → 把结果回传 → 继续推理"这个循环，直到模型不再请求工具。

```java
@Service
public class AgentService {

    public AgentResult run(AgentTask task, ProposalCollector collector) {
        return ChatClient.builder(chatModel)
            .defaultSystem(promptLoader.load(task.kind()))
            .defaultTools(new LibraryTools(collector))   // 工具执行时把提案写进 collector
            .build()
            .prompt()
            .user(task.instruction())
            .call()
            .entity(AgentResult.class);                   // 结构化输出
    }
}
```

**需要精细控制时**（比如强制限制步数、流式推送中间状态、工具执行失败要重试），用底层的 `ChatModel.call()` 自己写循环：

```java
public AgentResult runWithLoop(AgentTask task, ProposalCollector collector) {
    List<Message> messages = new ArrayList<>();
    messages.add(new SystemMessage(promptLoader.load(task.kind())));
    messages.add(new UserMessage(task.instruction()));

    for (int step = 0; step < task.maxSteps(); step++) {
        ChatResponse response = chatModel.call(new Prompt(messages, toolCallingOptions));
        AssistantMessage assistant = response.getResult().getOutput();
        messages.add(assistant);

        List<ToolCall> calls = assistant.getToolCalls();
        if (calls.isEmpty()) break;

        for (ToolCall call : calls) {
            ToolResponse result = toolRegistry.execute(call, collector);
            messages.add(new ToolResponseMessage(List.of(result)));
        }
    }
    return collector.toResult();
}
```

步数上限默认 12。达到上限时返回已有提案并提示"分析未完成，可继续"，不静默截断。

### 工具集

用 Spring AI 的 `@Tool` 注解声明。工具是**只读优先**的——绝大多数只是查询，产生副作用的极少数，且产出是提案而非写入。

```java
@Component
public class LibraryTools {

    private final ProposalCollector collector;

    @Tool(description = "列出全书卷章结构")
    public List<ChapterBrief> listChapters() { ... }

    @Tool(description = "读取指定章节的完整正文")
    public String readChapter(@ToolParam(description = "章节 id") String chapterId) { ... }

    @Tool(description = "按关键词全文检索，返回带上下文的片段")
    public List<SearchHit> searchText(@ToolParam String query) { ... }

    @Tool(description = "按名称或别名查找实体（人物、地点、物品等）")
    public List<EntityBrief> findEntity(@ToolParam String name) { ... }

    @Tool(description = "获取指定范围的章节摘要")
    public List<ChapterSummary> getSummaries(@ToolParam int from, @ToolParam int to) { ... }

    @Tool(description = "提交一条批注提案，不直接修改正文")
    public String proposeReview(@ToolParam ReviewProposal proposal) {
        collector.addReview(proposal);
        return "已记录";
    }
}
```

完整工具清单：

| 工具 | 副作用 | 说明 |
|---|---|---|
| `listChapters()` | 无 | 卷章树 |
| `readChapter(id)` | 无 | 读单章全文 |
| `readChapters(ids)` | 无 | 批量读，有总量上限 |
| `searchText(query)` | 无 | MySQL ngram 全文检索 |
| `findEntity(name)` | 无 | 查实体，走别名匹配 |
| `listEntities(type)` | 无 | 实体列表 |
| `getSummaries(from, to)` | 无 | 章节摘要链 |
| `getTimeline()` | 无 | 时间线 |
| `getHooks(status)` | 无 | 伏笔列表 |
| `proposeReview(...)` | 提案 | 提交批注 |
| `proposeEntityUpdate(...)` | 提案 | 提交实体卡修改 |
| `proposeTextEdit(...)` | 提案 | 提交正文修改 |

**上下文预算**：`readChapter` 返回前先估算 token 数，超过单次预算（默认 32k）时截断并告知模型"已截断，请用 searchText 精确定位"。

**文件读写不在工具列表里**。Agent 不能直接写文件，只能提交提案——这是铁律的物理保障。

## 4. 五项核心能力

### 4.1 错字查询

```
system: 你是中文小说校对员。只报告确凿的文字错误。
        不要报告风格偏好、不要建议改写、不要评价内容。
        对每个问题输出：原文片段、问题类型、修改建议、理由（一句话）。
        没有把握的不要报。返回 JSON。
```

约束：

- 要求返回**原文精确子串**。返回的片段若在原文中找不到，该条直接丢弃。
- 分批 1500 字，带前后各 300 字上下文。
- 每批结果去重（重叠区域）。

### 4.2 语病识别

同 4.1 的通道，但 `kind = GRAMMAR`，提示词不同——关注成分残缺、搭配不当、指代不明、语序混乱、虚词误用。

**与错字分开的理由**：作者对这两类问题的容忍度不同。错字必须改，语病常常是风格。分开可以让作者只开错字、关掉语病，不被"建议"淹没。

### 4.3 知识库生成

从正文自动抽取实体和关系。

```
章节保存完成
    │
    ▼
[数据库任务队列] 摘要 + 实体抽取（新章 / 大改后）
    │
    ▼
┌──────────────────────────────────────────────┐
│ 1. 生成章节摘要 → summary 表                  │
│ 2. 抽取本章实体提及                            │
│ 3. 实体消解：新提及 ↔ 已有实体                 │
│ 4. 写入 review 表（作为提案）                  │
└──────────────────────────────────────────────┘
    │
    ▼
[前端知识库面板] 待确认区：新增 3 个实体 / 更新 5 个
    │
    ▼ 作者确认
写入 03-人物/*.md + entity 表
```

**实体消解**是难点。中文小说里「陈平安」「平安」「小平安」「隐官」是同一个人。流程：

1. **精确匹配**：候选名与已有实体的 `name` 或 `aliases` 完全相等 → 同一实体。
2. **别名判定**：不精确时，把候选名 + 上下文句子 + 已有实体列表交给 LLM，问"是否指代已有实体？还是新实体？"
3. **不确定时归入"待确认"**，标注候选关联，让作者一句话解决。

**绝不自动合并**——错误的合并比不合并危害大得多。把两个角色揉成一个，全书逻辑就废了。

**抽取的提示词要点**：

- 明确区分"提及"和"出场"（提及是别人提到，出场是本人出现）
- 要求给出判定依据的原文片段
- 忽略路人和一次性名字（默认忽略出现 1 次且无对话的名字）

### 4.4 人物 / 背景补全

与 4.3 反向：不是从正文抽取，而是用正文内容完善人物卡。

```java
public record EntityUpdateProposal(
    String entityId,
    String field,          // "## 性格"
    String mode,           // append | replace
    String content,
    List<String> evidence  // 依据的章节 id，必需
) {}
```

**`evidence` 是强制字段**。任何写入人物卡的内容必须附出处章节，UI 上渲染成可点击的章节链接。没有出处的补全不予展示。

这条约束同时解决两个问题：作者可以快速核对，模型也因为没有出处可引而减少臆造。

### 4.5 故事逻辑前后矫正

最强的一环，也是上下文消耗最大的一环。三种检查模式：

**模式 A：单章内**（快，便宜）
被校章节全文 + 本章摘要 → 找章内矛盾（前后描述冲突、人物状态突变、时间跳跃无交代）。

**模式 B：跨章一致性**（中）
章节摘要链 + 相关实体卡 + 本章全文 → 找跨章矛盾。

```
system: 你是小说连续性编辑。已知以下全书设定与章节梗概，
        检查当前章节是否存在与之矛盾的描述。
        只报告客观矛盾，不评价剧情好坏。
        对每条报告：矛盾点、与哪一章冲突、原文依据。

【实体卡】
陈平安：status = alive，本命飞剑"十五"，境界 = 十四境
...

【章节摘要链（第 1-40 章）】
1. 雪夜 | 陈平安遇见宁姚，得知小镇将封闭 | 伏笔：离别暗示[open]
...

【当前检查：第 41 章】
```

**模式 C：伏笔追踪**（中）
遍历 `hook` 表，找 `status = open` 且埋设章节距今超过 N 章（默认 30）的伏笔，提示作者。这条不调用模型也能量化，模型只负责判断"是否实际已回收但未标记"。

**"加入忽略列表"很重要**。作者可能是刻意安排的（假死、伏笔、叙述性诡计）。忽略记录存在 `setting` 表，同一个矛盾不再重复报告。

## 5. 提案机制

提案**不落盘**，存在内存中（`ProposalCollector`），通过 SSE 推给前端。会话结束即消失。

理由：提案是临时的中间态。持久化只会让数据库膨胀且语义混乱。作者确实想留存的（比如一段生成的好文字），应当先接受进正文，由正文的快照机制保护。

```java
public class ProposalCollector {
    private final List<Proposal> proposals = new CopyOnWriteArrayList<>();

    public void addReview(ReviewProposal p)      { proposals.add(...); }
    public void addEntityUpdate(EntityUpdateProposal p) { ... }
    public void addTextEdit(TextEditProposal p)  { ... }

    public List<Proposal> all() { return List.copyOf(proposals); }
}
```

**每个提案有 `confidence`**（0-1）。低于 0.6 的在 UI 上弱化显示。

**前端按来源分组，不按时间**。作者关心的是"这次校对发现了什么"，不是"12:03 生成了什么"。

**批量接受是一个事务**，一次 Ctrl+Z 全部回滚。

## 6. 任务队列

```java
@Service
public class AiTaskQueue {

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(),
        new NamedThreadFactory("ai-task")
    );

    public String submit(AiTask task) {
        // 1. 查 input_hash 缓存，命中直接返回
        // 2. 写入 ai_task 表，status = queued
        // 3. 提交到线程池
        // 4. 返回任务 id
    }
}
```

**串行执行，并发 1。** 理由：多数模型服务商有并发限流，且并发请求让成本失控。

**去重**：`ai_task` 表的 `idx_cache (kind, input_hash)` 索引。同一章节、同一任务类型且内容哈希未变 → 复用上次结果，不发请求。

**增量校对**：只发变化的段落 + 上下文，不发全章。这是省钱的第一杠杆。

**预算护栏**：设置里可配每日 token 上限。达到后停止所有后台任务（手动触发的仍可运行），前端提示。防止作者挂机一晚发现账单爆了。

**任务可取消**：`Future.cancel(true)` + 在 AI 调用的 `WebClient` 上绑定中断信号。

## 7. 流式输出

用 Spring 的 `SseEmitter`，不用 WebSocket。

```java
@PostMapping(value = "/api/agent/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter chat(@RequestBody ChatRequest request) {
    SseEmitter emitter = new SseEmitter(0L);   // 不超时，由前端主动断开

    executor.submit(() -> {
        try {
            chatClient.prompt()
                .user(request.message())
                .stream()
                .content()
                .subscribe(
                    chunk -> send(emitter, "delta", chunk),
                    error -> { sendError(emitter, error); emitter.complete(); },
                    () -> { send(emitter, "done", null); emitter.complete(); }
                );
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    });

    emitter.onCompletion(() -> log.debug("SSE 连接关闭"));
    emitter.onTimeout(emitter::complete);
    return emitter;
}
```

**为什么不用 WebSocket**：AI 输出是单向流，SSE 更简单、能自动重连、走普通 HTTP 不用协议升级。双向能力在这里是负担。

**线程池隔离**：SSE 的推送线程和 AI 调用线程要分开，否则长连接会占满 AI 任务队列。

## 8. 提示词管理

提示词存在 `src/main/resources/prompts/` 下，每个 `TaskKind` 一个 `.txt` 或 `.md` 文件：

```
src/main/resources/prompts/
├── proofread.md
├── grammar.md
├── extract-entities.md
├── summary.md
├── consistency.md
└── chat.md
```

理由：提示词是这个项目里最需要迭代的东西，必须好找、好改、好 diff。不塞进 Java 字符串常量里。

**作者可在设置里覆盖内置提示词**，覆盖内容存 `setting` 表。高级用户能自己调优。

加载用 Spring 的 `ResourceLoader`，配合 Spring Boot 的配置刷新能力，改完不用重启。

## 9. 隐私

- **不向任何第三方服务器发送数据**，除了作者自己配置的模型服务商。
- **首次使用必须明确同意**：弹出对话框说明"你的正文将发送至你配置的 <服务商名>"。不勾选不能用 LLM 功能（规则引擎不受影响）。
- **脱敏模式**（可选）：发送前把已在知识库登记的人名 / 地名替换为占位符 `【人物1】`，返回后再还原。对长文分析会降低质量，默认关闭，设置页标注这个权衡。
- **本地模型路径**：选 Ollama 时全程不出本机，设置页明确标出这个状态。

## 10. 失败的降级

每一项 LLM 能力都必须有降级路径：

| 能力 | 降级 |
|---|---|
| 错字 | 规则引擎继续工作，覆盖约七成低级错误 |
| 语病 | 停用，UI 隐藏入口并说明原因 |
| 知识库抽取 | 回退到 HanLP 的人名识别（纯 Java，不调模型） |
| 章节摘要 | 回退到取章节首段 |
| 一致性检查 | 仅运行模式 C 的伏笔时效量化（不需要模型） |

**所有降级必须在 UI 上可见**。前端状态栏显示当前能力状态，而不是让作者以为"检查过了没问题"。

## 11. Python 服务的接口预留

Python 服务先不做，但要留好接口。约定：

**协议**：HTTP + JSON，跑在 `localhost:8000`。

**调用点**：封装在 `SemanticSearchClient` 接口后面，Java 侧有一个 `NoopSemanticSearchClient` 默认实现（返回空结果，前端隐藏对应入口）。

```java
public interface SemanticSearchClient {
    /** 语义检索：按意思找段落，而不是按字面 */
    List<SearchHit> search(String libraryId, String query, int topK);

    /** 判断两个名字是否可能指代同一实体 */
    double nameSimilarity(String a, String b);

    /** 生成文本向量 */
    float[] embed(String text);
}
```

配置项 `yimo.python.enabled = false`。为 true 时才启用真实实现。

**预留的三个具体用途**：

1. **语义搜索**——搜"陈平安第一次杀人"能搜到「他手起刀落，血溅三尺」这种没有关键词的段落
2. **自造人名识别**——用作者已确认的人物微调小模型，识别这本书里的人名变体
3. **本地模型推理**——不联网也能用 AI

**接口定义必须在动手写 Python 之前定死**，否则两边会反复改。定义好后，Java 侧写 Mock 联调，Python 侧照接口实现即可。

## 12. 关于 RAG 与编排框架

### 先说清楚：亿墨用了 RAG

RAG 的核心是三步——**检索 → 增强 → 生成**。先找到相关内容，塞进提示词，再让模型回答。亿墨到处是这个模式：

| 功能 | 检索什么 | 用什么检索 |
|---|---|---|
| 跨章一致性检查 | 相关章节梗概 + 人物卡 | 章节摘要链（`summary` 表）、`entity` 表 |
| Agent 回答"第41章和第28章矛盾吗" | 那两章全文 | 按 id 直取 |
| 实体消解"平安是不是陈平安" | 已有实体 + 别名 | `entity.aliases` 匹配 |
| Agent 工具 `searchText` | 含关键词的段落 | MySQL ngram 全文索引 |
| 伏笔追踪 | 未回收的钩子 | `hook` 表按 status 查询 |

**这就是 RAG。** 区别只在于检索器是关键词和结构化数据，不是向量相似度。

### 为什么不用向量检索做主力

**1. 中文小说的查询有很强的字面性**
作者搜"陈平安"，就是要找写着这三个字的地方。关键词匹配精确、快、零成本。向量检索反而会把"陈平""陈安""平安"混进来。

**2. 自造词向量化没有意义**
"骊珠洞天""本命飞剑""十四境"——玄幻/科幻小说的独创名词在通用 embedding 模型里没有语义，向量化后和普通词没区别。关键词匹配反而准。

**3. 可解释性**
作者问"为什么找到这段？"关键词匹配能答"因为包含'陈平安'"。向量检索只能答"因为它们语义相似"。写作场景需要精确，不可解释的召回会让作者不信任工具。

**4. 成本**
100 万字做 embedding 要花钱、要时间，且每改一章要重算。关键词索引按需建，几乎零成本。

### 什么时候真的需要向量检索

两个场景关键词做不到：

1. **"找陈平安第一次杀人那段"**——作者不记得当时写的是"血溅三尺"还是"手起刀落"，一个字都对不上
2. **"全书里哪些地方写了孤独感"**——纯语义查询，没有具体关键词

这两个场景已预留给 Python 服务（§11 的 `SemanticSearchClient`）。

### LangChain / LangGraph

**语言不对。** 两者都是 Python / JavaScript 的库，后端是 Java，用不了。

Java 生态的对应物是 LangChain4j，但与 Spring AI 功能重叠。Spring AI 是 Spring 官方项目，与 Spring Boot 的依赖注入、配置、事务、Web 层天然集成；LangChain4j 是第三方社区项目。没有理由选后者。

Spring AI 覆盖了 LangChain 的核心能力：

| LangChain | Spring AI |
|---|---|
| 统一模型抽象 | `ChatModel` |
| 工具调用 | `@Tool` 注解 |
| 结构化输出 | `.entity(Class)` |
| 对话记忆 | `ChatMemory` |
| 向量存储 | `VectorStore` |
| RAG 编排 | `QuestionAnswerAdvisor` |

选 Spring AI 不是"不用框架"，是"用 Java 的框架"。

**LangGraph 用在哪**：它的强项是多 Agent 协作、循环分支、中途暂停等人工输入。亿墨目前的 Agent 是单 Agent + 工具调用，流程固定，200 行 Java 写清楚，硬套图编排只增加调试难度。

但有一个真实场景适合它——**"全文体检"**：先用 Java 跑规则引擎抓低级错误，再把跨章逻辑分析交给 LangGraph 编排的多步流程（检索相关章节 → 分析矛盾 → 交叉验证 → 生成报告 → 人工确认）。这是有价值的编排，不是硬凑。**如果要做，它属于 Python 服务，不属于 Java 主线。**
