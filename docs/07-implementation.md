# 功能实现文档

每个模块写清楚：**接口 → 表 → 核心类 → 实现步骤 → 坑**。

按 `06-api.md` §16 的开发顺序推进，每一步都是可验证的。

## 目录

1. [书库管理](#1-书库管理)
2. [文件扫描与类型推断](#2-文件扫描与类型推断)
3. [Frontmatter 解析与写回](#3-frontmatter-解析与写回)
4. [章节 CRUD](#4-章节-crud)
5. [Markdown ↔ ProseMirror 往返](#5-markdown--prosemirror-往返)
6. [自动保存与冲突处理](#6-自动保存与冲突处理)
7. [版本快照](#7-版本快照)
8. [批注锚定与重定位](#8-批注锚定与重定位)
9. [规则引擎](#9-规则引擎)
10. [LLM 校对](#10-llm-校对)
11. [知识库](#11-知识库)
12. [一致性检查](#12-一致性检查)
13. [全文检索](#13-全文检索)
14. [导出](#14-导出)
15. [模型配置与热切换](#15-模型配置与热切换)

---

## 1. 书库管理

**接口** `GET/POST/PATCH/DELETE /api/libraries`
**表** `library`
**类** `LibraryController` · `LibraryService` · `LibraryRepository` · `LibraryStorage`

### 实现步骤

```java
public Library create(LibraryCreateRequest req) {
    Path path = Path.of(req.path()).toAbsolutePath().normalize();

    if (!Files.isDirectory(path))          throw new BizException(LIBRARY_PATH_INVALID);
    if (!Files.isReadable(path))           throw new BizException(LIBRARY_PATH_INVALID);
    if (libraryRepo.existsByPath(path.toString()))
        throw new BizException(LIBRARY_PATH_DUPLICATE);

    Library lib = new Library();
    lib.setId(Ulid.generate("lib_"));
    lib.setPath(path.toString());
    lib.setName(req.name() != null ? req.name() : path.getFileName().toString());
    libraryRepo.insert(lib);

    scanService.scanAsync(lib.getId());   // 异步，不阻塞响应
    return lib;
}
```

### 坑

**路径统一用绝对路径 + 规范化存储。** Windows 上 `D:\Writing\小说` 和 `d:/writing/小说` 是同一个目录，但字符串不等。`toAbsolutePath().normalize()` 之后统一大小写策略（Windows 上比较时不区分大小写）。

**删除书库绝不删文件。** 只 `DELETE FROM library WHERE id = ?`，加级联删除索引表。响应体显式返回 `filesDeleted: false`。

---

## 2. 文件扫描与类型推断

**接口** `POST /api/libraries/{id}/rescan`
**表** `chapter` · `entity`
**类** `ScanService` · `MarkdownParser` · `TypeInferrer`

### 类型推断规则

优先级从高到低：

```java
public DocType infer(Path file, Map<String, Object> frontmatter, Path bookRoot) {
    // 1. frontmatter 的 yimo 字段最权威
    Object yimo = frontmatter.get("yimo");
    if (yimo != null) return DocType.from(yimo.toString());

    // 2. 看所在目录名
    Path dir = bookRoot.relativize(file).getName(0);
    String dirName = dir.toString();
    if (contains(dirName, "正文", "章节", "chapters"))  return CHAPTER;
    if (contains(dirName, "人物", "角色", "characters")) return ENTITY_CHARACTER;
    if (contains(dirName, "地点", "locations"))          return ENTITY_LOCATION;
    if (contains(dirName, "物品", "道具", "items"))      return ENTITY_ITEM;
    if (contains(dirName, "组织", "势力", "orgs"))       return ENTITY_ORG;

    // 3. 看文件名
    if (file.getFileName().toString().contains("大纲")) return OUTLINE;

    // 4. 兜底
    return NOTE;
}
```

**目录名匹配要忽略编号前缀。** `07-正文` 要先剥掉 `07-` 再匹配。

### 扫描流程

```java
public ScanResult scan(String libraryId) {
    Path root = libraryStorage.rootOf(libraryId);
    List<Path> files;
    try (Stream<Path> s = Files.walk(root, 8)) {   // 限制深度
        files = s.filter(p -> p.toString().endsWith(".md"))
                 .filter(Files::isRegularFile)
                 .toList();
    }

    for (Path f : files) {
        try {
            Map<String, Object> fm = markdownParser.readFrontmatter(f);
            DocType type = inferrer.infer(f, fm, root);
            // 按类型分发到 chapter / entity 表
            upsertIndex(libraryId, root.relativize(f), fm, type);
        } catch (Exception e) {
            // 单个文件失败不能中断整个扫描
            log.warn("跳过无法解析的文件: {}", f, e);
            errors.add(f.toString());
        }
    }
    return new ScanResult(files.size(), errors);
}
```

### 坑

**单个文件解析失败不能中断全库扫描。** 作者目录里可能混着乱七八糟的 md，或者某个文件正被 Typora 独占打开（Windows 会抛 `FileSystemException`）。收集错误清单，扫描完成时展示给作者。

**编码必须先处理 BOM。** Windows 记事本保存的 UTF-8 文件带 BOM（`\uFEFF`），不剥掉的话 frontmatter 的 `---` 匹配会失败，整份文件被当成无元数据。

```java
String text = Files.readString(path, StandardCharsets.UTF_8);
if (text.startsWith("\uFEFF")) text = text.substring(1);
```

**`Files.walk` 要限制深度。** 默认无限深度，指向一个有深层 node_modules 的目录会卡死。

---

## 3. Frontmatter 解析与写回

**类** `FrontmatterCodec`
**依赖** SnakeYAML + Jackson

### 解析

```java
private static final Pattern FM = Pattern.compile(
    "^---\\s*\\r?\\n(.*?)\\r?\\n---\\s*\\r?\\n?", Pattern.DOTALL);

public ParsedMarkdown parse(String raw) {
    String text = stripBom(raw);
    Matcher m = FM.matcher(text);
    if (!m.find()) return new ParsedMarkdown(Map.of(), text);

    String yaml = m.group(1);
    String body = text.substring(m.end());
    Map<String, Object> map = new Yaml().load(yaml);
    return new ParsedMarkdown(map, body);
}
```

### 写回（关键）

**必须保留未知字段。** 作者的 frontmatter 里可能有亿墨不认识的自定义字段，或者别的工具写入的字段。全部丢弃是不可接受的。

```java
public String serialize(Map<String, Object> frontmatter, String body) {
    // LinkedHashMap 保证字段顺序稳定，避免每次保存都产生无意义的 diff
    Map<String, Object> ordered = new LinkedHashMap<>();

    // 1. 已知字段按固定顺序排前面
    for (String key : KNOWN_ORDER) {
        if (frontmatter.containsKey(key)) ordered.put(key, frontmatter.get(key));
    }
    // 2. 未知字段原样保留，排后面
    frontmatter.forEach((k, v) -> ordered.putIfAbsent(k, v));

    DumperOptions opt = new DumperOptions();
    opt.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    opt.setPrettyFlow(true);
    opt.setSplitLines(false);              // 长字符串不折行

    String yaml = new Yaml(opt).dump(ordered);
    return "---\n" + yaml + "---\n\n" + body;
}
```

### 坑

**字段顺序必须稳定。** 用 `HashMap` 的话每次保存顺序都可能变，作者的 Git 里会看到一堆无意义的 diff。必须用 `LinkedHashMap` 且固定已知字段的顺序。

**中文字符串不要被 YAML 加引号。** SnakeYAML 默认会给含特殊字符的字符串加引号，把 `title: 第一章 雪夜` 变成 `title: '第一章 雪夜'`。配置 `DumperOptions` 的 `setDefaultScalarStyle(PLAIN)` 并加自定义 Representer 处理。

**多行字符串用 `|` 字面块。** `summary` 之类的字段如果有换行，要输出成 YAML 的块标量而不是带 `\n` 的引号字符串。

**`relations` 是嵌套数组对象。** 别用正则手搓，直接交给 SnakeYAML 和 Jackson。

---

## 4. 章节 CRUD

**接口** `POST/PATCH/DELETE /api/chapters` · `POST /api/chapters/{id}/move`
**表** `chapter`
**类** `ChapterController` · `ChapterService` · `ChapterFileWriter`

### 文件命名

```java
public String buildFileName(int order, String title) {
    String safe = title.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    return String.format("第%03d章-%s.md", order, safe);
}
```

**Windows 非法字符必须替换**，否则创建文件直接抛异常。同时要处理：
- 标题末尾的点和空格（Windows 不允许）
- 保留名（CON、PRN、AUX、NUL、COM1-9、LPT1-9）
- 超长文件名（Windows 路径上限 260 字符）

### 新建章节

```java
@Transactional
public ChapterDetail create(ChapterCreateRequest req) {
    Book book = resolveBook(req.bookId());
    int order = req.afterChapterId() != null
        ? computeOrderAfter(req.afterChapterId())      // 插入并重排后续
        : maxOrder(book) + 1;

    Path file = book.root().resolve("07-正文").resolve(buildFileName(order, req.title()));
    if (Files.exists(file)) throw new BizException(CHAPTER_TITLE_DUPLICATE);

    Map<String, Object> fm = new LinkedHashMap<>();
    fm.put("yimo", "chapter");
    fm.put("id", Ulid.generate("ch_"));
    fm.put("title", req.title());
    fm.put("volume", req.volume());
    fm.put("order", order);
    fm.put("status", "draft");
    fm.put("created", OffsetDateTime.now());
    fm.put("updated", OffsetDateTime.now());

    chapterFileWriter.write(file, fm, req.content());
    return indexAndReturn(file, fm);
}
```

### 坑

**插入到中间时要重排后续章节的 `order`。** 并且磁盘上的文件名带编号，重排意味着要么改文件名（影响外部引用），要么只改 frontmatter 的 `order`。

**决策：只改 frontmatter 的 `order`，不动文件名。** 文件名一旦创建就不再变（除非作者手动改），避免破坏 Git 历史和外部链接。排序时 `order` 优先于文件名里的数字。

**删除是移到 `.yimo/trash/`，不是真删。** 保留 30 天。路径要带上时间戳避免同名覆盖。

---

## 5. Markdown ↔ ProseMirror 往返

**类** 前端 `useTiptapEditor` · `markdownCodec.ts`

### 允许的节点白名单

```ts
const ALLOWED = [
  'doc', 'paragraph', 'text',
  'bold', 'italic', 'strike',
  'blockquote', 'bulletList', 'orderedList', 'listItem',
  'horizontalRule', 'hardBreak',
]
```

**表格、脚注、HTML、图片一律不支持。** 小说不需要，而每多支持一种节点，往返丢失的风险就多一分。

### 往返自检

```ts
export function selfCheck(md: string): RoundTripIssue[] {
  const doc = parseMarkdown(md)
  const back = serializeMarkdown(doc)
  if (back === md) return []

  // 逐行对比找差异位置，方便定位
  return diffLines(md, back)
}
```

空闲时（`requestIdleCallback`）对当前章节跑一次，失败在状态栏警告。**这是回归测试的第一优先级**——每发现一个失败 case，就往测试语料库加一条。

### 中文段落缩进

作者写的是全角空格缩进，ProseMirror 里应该以样式呈现，但内容必须保留。

**方案：不做任何转换，全角空格就是普通文本。**

理由：任何"显示时去掉、保存时加回"的方案，都会在粘贴、复制、部分选中时出错。让全角空格老老实实当字符存在，是最不容易出 bug 的做法。

CSS 只负责首行缩进的视觉呈现：

```css
.ProseMirror p { text-indent: 2em; }
```

而全角空格本身在视觉上占据的空间，通过 `text-indent` 的负值或不设缩进来平衡。**具体做法在 M1 阶段用真实稿件调试确定**，这是需要目测调整的地方。

### 坑

**中文输入法组合态期间不要触发序列化。** 用 `compositionstart` / `compositionend` 包住，组合结束后再算。

**粘贴要过滤。** 从网页粘贴会带来一堆 span/style，必须走 `transformPastedHTML` 清洗成纯文本或允许的节点。

---

## 6. 自动保存与冲突处理

**接口** `PUT /api/chapters/{id}/content`
**类** `ChapterController` · `ChapterService` · `LibraryStorage`

### 前端

```ts
const save = useDebounce(async (md: string) => {
  if (isComposing.value) return          // 输入法组合中，跳过
  try {
    const res = await api.saveContent(chapterId.value, {
      content: md,
      contentHash: currentHash.value,
    })
    currentHash.value = res.contentHash
    saveState.value = 'saved'
  } catch (e) {
    if (e.error === 'CONTENT_HASH_MISMATCH') {
      conflictDialog.open(e.details)      // 弹窗让作者选
    } else {
      saveState.value = 'error'
    }
  }
}, 1500)
```

### 后端

```java
public ChapterContentUpdateResponse save(String chapterId, ChapterContentUpdateRequest req) {
    Chapter ch = chapterRepo.findById(chapterId).orElseThrow(...);
    Path file = libraryStorage.resolve(ch.getRelPath());

    String currentRaw = libraryStorage.read(file);
    String currentHash = hasher.hash(currentRaw);

    // 幂等：内容没变直接返回，不写文件
    if (currentHash.equals(req.contentHash())
        && extractBody(currentRaw).equals(req.content())) {
        return ChapterContentUpdateResponse.unchanged(ch);
    }

    // 冲突：磁盘已被外部修改
    if (!currentHash.equals(req.contentHash())) {
        throw new ContentMismatchException(currentRaw, currentHash);
    }

    Map<String, Object> fm = fmCodec.parse(currentRaw).frontmatter();
    fm.put("updated", OffsetDateTime.now());
    libraryStorage.writeAtomic(file, fmCodec.serialize(fm, req.content()));

    chapterRepo.updateHash(chapterId, hasher.hash(...));
    return ...;
}
```

### 坑

**`read → compare → write` 之间有竞态。** 本地单用户场景下概率极低，但正确的做法是：读时记录文件的 `lastModified` 和大小，写之前再检查一次，变了就重新走冲突分支。

**原子写入必须是真的原子。** `Files.move` 带 `ATOMIC_MOVE` 在 Windows 和 Linux 上都支持同分区重命名。临时文件必须和目标文件在**同一个目录**（同一分区），否则退化成交叉分区拷贝，就不是原子的了。

```java
Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
Files.writeString(tmp, content, UTF_8);
Files.move(tmp, target, REPLACE_EXISTING, ATOMIC_MOVE);
```

**哈希算法选 xxHash 或 SHA-256 截断。** 内容可能几十 KB，每次保存算一次，用 SHA-256 完全够快。不用 MD5（有碰撞攻击，虽然不是安全场景但没必要）。

---

## 7. 版本快照

**接口** `GET/POST /api/chapters/{id}/snapshots` · `POST /api/chapters/{id}/restore`
**类** `SnapshotService`
**位置** `.yimo/snapshots/{chapterId}/{ISO8601}.md`

### 存储格式

**整章 Markdown 副本，不存 diff。**

理由：diff 需要基准，基准会被清理。当基准快照被淘汰后，后续 diff 全部失效。整章副本在 3000 字规模下压缩后只有几 KB，这个成本换来的是永不失效。

### 触发时机

| 触发 | 说明 |
|---|---|
| 定时 | 章节打开状态下每 10 分钟 |
| 手动 | 作者点按钮 |
| 回滚前 | 自动创建 `pre-restore` 快照 |
| 大批量操作前 | 批量接受批注、章节重排 |

### 淘汰

默认保留最近 50 个，按时间倒序。淘汰时直接删文件。

**例外：`pre-restore` 类型的快照不参与淘汰，且不计入 50 的额度。** 它是回滚操作的安全网，不能被新的快照挤掉。

### 坑

**快照目录不进 Git 的默认 `.gitignore` 已经有它**（见 `02-library-format.md`）。但要确保它确实被排除——几千个小文件进 Git 会拖垮仓库。

---

## 8. 批注锚定与重定位

**接口** `GET /api/chapters/{id}/reviews` · `PATCH /api/reviews/{id}`
**表** `review`
**类** 前端 `useReviewAnchoring` · `reviewDecoration.ts`

### 前端渲染

批注用 ProseMirror 的 Decoration 渲染，**不修改文档内容**。

```ts
function buildDecorations(doc: Node, reviews: Review[], orphaned: Set<string>) {
  const decos: Decoration[] = []
  for (const r of reviews) {
    if (orphaned.has(r.id)) continue
    decos.push(Decoration.inline(r.anchor.from, r.anchor.to, {
      class: `review review-${r.severity}`,
      'data-review-id': r.id,
    }))
  }
  return DecorationSet.create(doc, decos)
}
```

### 重定位算法

按顺序尝试，命中即停：

```ts
function relocate(doc: Node, anchor: Anchor): { from: number, to: number } | null {
  const text = doc.textBetween(0, doc.content.size, '\n')

  // 1. 精确命中
  if (text.slice(anchor.from, anchor.to) === anchor.quote) {
    return { from: anchor.from, to: anchor.to }
  }

  // 2. 邻域搜索
  const winStart = Math.max(0, anchor.from - 200)
  const winEnd   = Math.min(text.length, anchor.to + 200)
  const win = text.slice(winStart, winEnd)
  const i = win.indexOf(anchor.quote)
  if (i >= 0) return { from: winStart + i, to: winStart + i + anchor.quote.length }

  // 3. 指纹匹配
  const fp = anchor.prefix + anchor.quote + anchor.suffix
  const fi = text.indexOf(fp)
  if (fi >= 0) {
    const from = fi + anchor.prefix.length
    return { from, to: from + anchor.quote.length }
  }

  // 4. 局部指纹：全局找 quote，取编辑距离最小且相似度 > 0.85 的
  let best = null, bestSim = 0.85
  let pos = 0
  while ((pos = text.indexOf(anchor.quote.slice(0, 4), pos)) >= 0) {
    const cand = text.slice(pos, pos + anchor.quote.length + 8)
    const sim = similarity(cand, anchor.quote)
    if (sim > bestSim) { bestSim = sim; best = { pos, len: cand.length } }
    pos += 1
  }
  if (best) return { from: best.pos, to: best.pos + anchor.quote.length }

  // 5. 失效
  return null
}
```

### 触发时机

| 时机 | 动作 |
|---|---|
| 章节加载完成 | 对所有 pending 批注跑一次重定位 |
| 输入停顿 800ms | 只对"受影响区间"内的批注重定位 |
| `compositionend` | 统一重算 |

**受影响区间**：只重定位锚点落在 `[改动位置 - 500, 改动位置 + 500]` 内的批注。全文重定位在长章节里是几毫秒，但每次输入都跑没必要。

### 回写

重定位成功且位置变化超过 3 个字符时，`PATCH /api/reviews/{id}` 回写新锚点。**加阈值避免抖动**——每次输入都回写会产生大量无意义的数据库写入。

### 坑

**失效批注不能静默删除。** 收进"已失效批注"折叠区，显示原文，让作者判断。作者可能刚写完就改了，批注看起来失效其实只是位移太大。

**`quote` 长度上限 500。** 超过的部分截断，只保留前 500 字符做锚定。超长批注（整段）的锚定精度本来就低。

---

## 9. 规则引擎

**接口** `POST /api/chapters/{id}/proofread`（`engine=rule`）
**类** `src/rules/` 下的规则实现，纯函数，零依赖

### 设计约束

```java
@FunctionalInterface
public interface Rule {
    String id();
    String description();
    Severity defaultSeverity();
    List<RuleIssue> check(String text, RuleContext ctx);
}

public record RuleIssue(
    int start, int end,
    String message,
    List<String> suggestions,
    double confidence
) {}
```

**纯函数、零依赖、不碰数据库、不碰文件。** 这保证它能独立单元测试，将来也能抽成独立包。

### 规则清单

| id | 规则 | 例 | 默认 |
|---|---|---|---|
| `de-di-de` | 的/地/得 | 「快速的跑」→「快速地跑」 | 开 |
| `zai-vs-zai` | 在/再 | 「我在说一遍」→「我再说一遍」 | 开 |
| `zuo-vs-zuo` | 做/作 | 「做为」→「作为」 | 开 |
| `na-vs-na` | 那/哪 | 「那怎么办」→「哪怎么办」 | 开 |
| `pronoun-mix` | 他/她/它 混用 | 同段内指代同一人却换用 | 关 |
| `punct-pair` | 标点配对 | 引号、括号不成对 | 开 |
| `punct-mix` | 中英标点混用 | 中文语境里出现 `,` `.` `?` | 开 |
| `ellipsis` | 省略号 | `...` / `。。。` → `……` | 开 |
| `dup-char` | 叠字误写 | 「的的」「了了」 | 开 |
| `fullwidth-space` | 孤立全角空格 | 段中出现不在段首的全角空格 | 开 |
| `number-mix` | 数字规范 | 同段落阿拉伯数字与汉字数字混用 | 关 |
| `dup-word` | 重复词 | 相邻 4 字窗口内重复 2 字以上 | 关 |

**默认关闭的三条（`pronoun-mix`、`number-mix`、`dup-word`）误报率偏高。** 规则一旦有争议就默认关闭，作者可在设置里手动开启。

### 实现要点

**「的/地/得」需要看后面的词性。** 简单规则会误报。正确做法：

```java
// 得：后面接补语（动词/形容词作补语）
// 地：后面接动词作状语
// 的：其他

// 用 HanLP 词性标注判断后一个词
List<Term> terms = HanLP.segment(text);
```

**没有 HanLP 时的降级**：用词表匹配（常见的「XX地+动词」组合），准确率下降但能用。

**「他/她/它混用」必须用分词。** 要判断同段内有没有指代同一个人。实现：先找出段内所有代词，再看它们修饰的名词是否相同。这条规则依赖 HanLP，没有就整条跳过。

### 坑

**误报率必须低于 2%。** 这是硬指标。宁可漏，不可扰。测试方法：拿 100 万字真实小说跑一遍，人工抽查 200 条报告。

**规则必须可关闭且记住选择。** 作者关掉「的/地/得」后，`ignore-rule` 记录进 `setting` 表，之后不再报告。

---

## 10. LLM 校对

**接口** `POST /api/chapters/{id}/proofread`（`engine=llm`）+ `GET /api/tasks/{id}/stream`
**表** `ai_task` · `review`
**类** `ProofreadService` · `AgentService`

### 分批策略

```java
private static final int BATCH_SIZE = 1500;     // 每批字数
private static final int CONTEXT_SIZE = 300;    // 前后各带 300 字

public List<Batch> split(String text) {
    List<Batch> batches = new ArrayList<>();
    int pos = 0;
    while (pos < text.length()) {
        // 在句子边界切分，不要切在词中间
        int end = findSentenceBoundary(text, pos, pos + BATCH_SIZE);
        String content = text.substring(pos, end);
        String contextBefore = text.substring(Math.max(0, pos - CONTEXT_SIZE), pos);
        String contextAfter  = text.substring(end, Math.min(text.length(), end + CONTEXT_SIZE));
        batches.add(new Batch(pos, end, content, contextBefore, contextAfter));
        pos = end;
    }
    return batches;
}
```

**必须按句子边界切分。** 切在词中间会让模型看到半个句子，产生大量误报。

### 片段校验（关键）

模型返回的 `quote` 必须在原文里能找到，否则丢弃：

```java
List<Review> validate(String batchText, int batchOffset, List<LlmIssue> issues) {
    List<Review> valid = new ArrayList<>();
    for (LlmIssue issue : issues) {
        int idx = batchText.indexOf(issue.quote());
        if (idx < 0) {
            log.debug("丢弃无法定位的片段: {}", issue.quote());
            continue;
        }
        // 转成全局偏移
        int from = batchOffset + idx;
        int to = from + issue.quote().length();
        valid.add(buildReview(from, to, issue, ...));
    }
    return valid;
}
```

这条约束挡住模型绝大部分的幻觉。**返回的片段定位不到，说明模型在编。**

### JSON 解析容错

模型经常返回带 markdown 代码块围栏的 JSON：

````
```json
{ "issues": [...] }
```
````

```java
private String extractJson(String raw) {
    String s = raw.trim();
    if (s.startsWith("```")) {
        s = s.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
    }
    int start = s.indexOf('{');
    int end = s.lastIndexOf('}');
    if (start >= 0 && end > start) s = s.substring(start, end + 1);
    return s;
}
```

同时用 Jackson 的 `FAIL_ON_UNKNOWN_PROPERTIES = false`，模型多返回字段不至于整体失败。

### SSE 推送

```java
@PostMapping("/api/chapters/{chapterId}/proofread")
public ProofreadResponse start(@PathVariable String chapterId, @RequestBody ProofreadRequest req) {
    String taskId = taskQueue.submit(...);   // 立刻返回
    return new ProofreadResponse(taskId, req.engine(), estimate, cached);
}
```

任务线程里边算边推：

```java
for (Batch batch : batches) {
    emitter.send(event("progress", Map.of("current", i, "total", batches.size())));
    List<Review> issues = callModel(batch);
    for (Review r : issues) {
        reviewRepo.insert(r);
        emitter.send(event("review", r));      // 立即上屏
    }
}
emitter.send(event("done", Map.of("reviewCount", total)));
```

**边算边推是体验的关键。** 30 秒的等待和"3 秒后开始陆续出现结果"是两个完全不同的感受。

### 坑

**AI 调用绝不能放在 `@Transactional` 里。** 一次调用几十秒，会占着数据库连接不放，连接池很快耗尽。见 `03-agent-design.md` §1 的对照代码。

**`response_format: json_schema` 不是所有服务商都支持。** 降级链：`json_schema` → `json_object` → 纯 prompt 约束 + 客户端容错解析。

---

## 11. 知识库

**接口** `GET /api/libraries/{id}/entities` · `POST /api/entities/merge` 等
**表** `entity` · `relation` · `mention` · `summary` · `hook`
**类** `KbService` · `EntityExtractor` · `EntityResolver` · `SummaryService`

### 章节摘要生成

章节保存后触发（debounce，且内容哈希变化时才触发）。

```java
public Summary generate(String chapterId) {
    String text = chapterService.readBody(chapterId);
    String prompt = promptLoader.load("summary", Map.of("text", text));
    return chatClient.prompt(prompt).call().entity(Summary.class);
}
```

返回结构见 `02-library-format.md` 的摘要链定义。

### 实体抽取

```java
public List<EntityMentionCandidate> extract(String chapterId) {
    return chatClient.prompt()
        .system(promptLoader.load("extract-entities"))
        .user(text)
        .call()
        .entity(new ParameterizedTypeReference<List<EntityMentionCandidate>>() {});
}
```

**提示词要点**：

- 明确区分"提及"和"出场"
- 要求给出判定依据的原文片段
- 忽略出现 1 次且无对话的名字（阈值可配）

### 实体消解（三级）

```java
public Resolution resolve(EntityMentionCandidate candidate) {
    // 1. 精确匹配：名字或别名完全相等
    Optional<Entity> exact = entityRepo.findByNameOrAlias(libId, candidate.name());
    if (exact.isPresent()) return Resolution.merge(exact.get(), 1.0);

    // 2. 别名判定：交给模型
    List<Entity> candidates = entityRepo.findByLibrary(libId);
    AliasJudgement j = judgeAlias(candidate, candidates);   // LLM 调用

    if (j.isSameEntity() && j.confidence() > 0.8) {
        return Resolution.merge(j.entityId(), j.confidence());
    }
    if (j.confidence() < 0.4) {
        return Resolution.createNew();
    }

    // 3. 不确定 → 待确认
    return Resolution.pending(j.possibleMatches());
}
```

### 坑

**绝不自动合并。** 这是整个知识库设计的底线。错误的合并（把两个角色揉成一个）比不合并的危害大一个数量级，而且极难发现——作者可能几十章之后才发现人物关系乱了。

`Resolution.merge` 只在**精确匹配**（名字或别名完全相等，置信度 1.0）时才自动执行。别名判定的结果一律进待确认，让作者点一下。

**实体文件的更新也是写文件。** 作者确认提案后，后端要修改 `03-人物/陈平安.md` 的 frontmatter 和正文区。同样走原子写入，同样保留未知字段。

**别名要双向索引。** `entity.aliases` 是 JSON 数组，查询「平安」属于哪个实体时，MySQL 8 可以用 `JSON_CONTAINS`，但性能一般。**建议额外建一张 `entity_alias` 表**（`entity_id`, `alias`，带唯一索引），查询走这张表，`entity.aliases` 只作为展示用的冗余。

---

## 12. 一致性检查

**接口** `POST /api/chapters/{id}/proofread`（`kind=consistency`）
**类** `ConsistencyService`

### 三种模式

**模式 A：单章内**（快，便宜）

```java
String prompt = promptLoader.load("consistency-chapter", Map.of(
    "chapterText", text,
    "chapterSummary", summary
));
```

**模式 B：跨章一致性**（中）

上下文组装顺序（**重要的放后面**，模型的注意力更集中）：

```java
Map<String, Object> vars = Map.of(
    "entities", formatEntityCards(relevantEntities),   // 人物卡
    "summaries", formatSummaryChain(summaries),         // 章节摘要链
    "chapterText", text                                 // 当前检查的章节放最后
);
```

**模式 C：伏笔追踪**（中）

```java
// 纯 SQL 就能量化，不需要模型
List<Hook> stale = hookRepo.findOpenOlderThan(libId, chapterOrder - 30);
```

模型只负责判断"这些伏笔是否实际已回收但未标记"。

### 长文分级策略

| 书库规模 | 策略 |
|---|---|
| < 20 万字 | 全文直接喂长上下文模型 |
| 20-100 万字 | 按卷分批，每批带完整摘要链 |
| > 100 万字 | 只做实体卡 + 摘要链，不喂全文 |

### 忽略列表

作者标记为"有意为之"的矛盾记入 `setting` 表，格式：

```json
{ "ignores": [
  { "type": "consistency", "chapterId": "ch_41", "signature": "左手持剑|三年前废了" }
] }
```

`signature` 用两个冲突片段的哈希拼接，避免同一矛盾重复报告。

---

## 13. 全文检索

**接口** `GET /api/libraries/{id}/search`
**表** `chapter_content`（FULLTEXT ngram）
**类** `SearchService`

### MySQL 配置（最容易踩的坑）

```sql
CREATE TABLE chapter_content (
  chapter_id  CHAR(26)   NOT NULL PRIMARY KEY,
  body        MEDIUMTEXT NOT NULL,
  FULLTEXT KEY ft_body (body) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**没有 `WITH PARSER ngram`，中文搜索完全失效。** 默认分词器按空格切词，一整段中文会被当成一个词，搜什么都搜不到。

```sql
-- 确认 ngram 生效
SHOW VARIABLES LIKE 'ngram_token_size';   -- 应为 2
```

### 查询

```sql
SELECT chapter_id, body
FROM chapter_content
WHERE MATCH(body) AGAINST (? IN BOOLEAN MODE)
LIMIT ?
```

**用 `BOOLEAN MODE` 不用 `NATURAL LANGUAGE MODE`。** 我们要的是"包含这些词"，不是相关度排序。

### 高亮位置计算（MySQL 不给位置）

**MySQL 全文检索不返回匹配位置。** 要自己算：

```java
private SearchHit buildHit(String chapterId, String body, String query) {
    List<int[]> highlights = new ArrayList<>();
    String lowerBody = body.toLowerCase();
    int pos = 0;
    while ((pos = lowerBody.indexOf(query.toLowerCase(), pos)) >= 0) {
        highlights.add(new int[]{pos, pos + query.length()});
        pos += query.length();
    }

    // 截取第一个命中位置前后各 50 字作为 snippet
    int first = highlights.isEmpty() ? 0 : highlights.get(0)[0];
    int start = Math.max(0, first - 50);
    int end   = Math.min(body.length(), first + query.length() + 50);
    String snippet = body.substring(start, end);

    // highlights 要转成 snippet 内的相对位置
    List<Highlight> relative = highlights.stream()
        .filter(h -> h[0] >= start && h[1] <= end)
        .map(h -> new Highlight(h[0] - start, h[1] - start))
        .toList();

    return new SearchHit(chapterId, snippet, relative);
}
```

**多词查询要用分词。** 查询 "陈平安 一剑" 要先拆成两个词分别高亮。用 HanLP 分词，或者简单按空格拆。

### 坑

**`ngram_token_size` 默认 2，即按双字切分。** 单字查询（搜一个"剑"字）在 ngram(2) 下匹配不到。如果需要支持单字查询，要么改 `ngram_token_size=1`（索引体积暴涨），要么在应用层对单字查询走 `LIKE '%剑%'` 降级。

**建议：单字查询走 LIKE 降级，不做全库扫描的优化。** 单字查询本来就少，且用户预期是模糊匹配。

**`chapter_content` 表是副本。** 它是为了全文索引才存在的，正文真相源永远是文件。两者必须同步——保存正文时同时更新这张表，且要在同一个事务里。

---

## 14. 导出

**接口** `POST /api/libraries/{id}/export` · `GET /api/exports/{taskId}/download`
**类** `ExportService`

### TXT 导出

```java
public void exportTxt(Book book, ExportRequest req, OutputStream out) throws IOException {
    Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
    List<Chapter> chapters = chapterService.listInOrder(book, req.scope());

    for (Chapter ch : chapters) {
        if (req.options().includeTitle()) {
            w.write(ch.getTitle());
            w.write("\n\n");
        }
        w.write(chapterService.readBody(ch.getId()));
        w.write(req.options().chapterSeparator());
    }
    w.flush();
}
```

**中文排版在导出时已经正确。** 因为正文里本来就写了全角空格缩进，不需要导出时再加工。

### 中文文件名（RFC 5987）

```java
String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                          .replace("+", "%20");
response.setHeader("Content-Disposition",
    "attachment; filename=\"" + asciiFallback + "\"; filename*=UTF-8''" + encoded);
```

**两个都要给。** 老客户端不认 `filename*`，只用 `filename`；现代客户端优先 `filename*`。`filename` 里的中文会被截断或乱码，所以要给一个 ASCII 兜底名（拼音或章节数）。

### 坑

**导出要流式，不能全部读进内存。** 一本 100 万字的书拼成字符串是 3MB，尚可；但 DOCX/EPUB 生成会胀大很多倍。全部走 `OutputStream`。

**导出任务时间较长时走任务队列 + SSE 进度**，不要阻塞 HTTP 请求。

---

## 15. 模型配置与热切换

**接口** `GET/PUT /api/settings` · `POST /api/settings/test-model`
**表** `setting`
**类** `ModelProviderFactory` · `SettingsService`

### 关键问题：Spring AI 的 ChatModel 是 Bean，但要运行时切换

Spring AI 的常规用法是把 `ChatModel` 配成 Bean 注入。但亿墨需要**用户在设置页改了配置后立刻生效**，不能重启服务。

**方案：不用注入的 Bean，用工厂按需构建。**

```java
@Component
public class ModelProviderFactory {

    private final Map<String, ChatModel> cache = new ConcurrentHashMap<>();

    public ChatModel get(ModelConfig config) {
        String key = cacheKey(config);
        return cache.computeIfAbsent(key, k -> build(config));
    }

    private ChatModel build(ModelConfig c) {
        return switch (c.provider()) {
            case "openai-compat" -> OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                    .baseUrl(c.baseUrl())
                    .apiKey(c.apiKey())
                    .build())
                .defaultOptions(OpenAiChatOptions.builder()
                    .model(c.model())
                    .temperature(0.3)
                    .build())
                .build();

            case "anthropic" -> AnthropicChatModel.builder()
                .anthropicApi(AnthropicApi.builder()
                    .apiKey(c.apiKey())
                    .build())
                .build();

            case "ollama" -> OllamaChatModel.builder()
                .ollamaApi(OllamaApi.builder().baseUrl(c.baseUrl()).build())
                .build();

            default -> throw new BizException(MODEL_NOT_CONFIGURED);
        };
    }
}
```

**缓存 key 包含 `baseUrl + apiKey 哈希 + model`**，配置变了自然命中不了，自动重建。旧的不主动清理，靠 LRU 或定时清理。

### 密钥脱敏

```java
public SettingView toView(Setting entity) {
    String key = entity.getApiKey();
    String masked = key == null || key.length() < 8
        ? "****"
        : key.substring(0, 3) + "****" + key.substring(key.length() - 4);
    return new SettingView(..., masked);
}
```

**读取时永不返回完整 key。** 更新时 `apiKey` 传 `null` 表示不修改：

```java
public void update(Settings req) {
    for (ModelConfig c : req.models()) {
        if (c.apiKey() == null) {
            c = c.withApiKey(existingKeyOf(c.id()));   // 保持原值
        }
        ...
    }
}
```

### 连通性测试

```java
public ModelTestResult test(ModelTestRequest req) {
    long start = System.currentTimeMillis();
    try {
        ChatModel model = factory.build(new ModelConfig(req));
        String echo = model.call(new Prompt("ping")).getResult().getOutput().getText();
        return new ModelTestResult(true, System.currentTimeMillis() - start, echo, "连接正常");
    } catch (Exception e) {
        throw new BizException(MODEL_TEST_FAILED, classify(e));   // 分类错误
    }
}

private String classify(Exception e) {
    String msg = rootMessage(e).toLowerCase();
    if (msg.contains("401") || msg.contains("invalid api key")) return "API Key 无效";
    if (msg.contains("402") || msg.contains("insufficient"))    return "账户额度不足";
    if (msg.contains("404") || msg.contains("model not found")) return "模型名不存在";
    if (msg.contains("connect") || msg.contains("timeout"))     return "网络不通";
    return "未知错误：" + msg;
}
```

**必须分类返回。** key 无效要重新填，模型名不对要改模型名，网络不通要检查代理——每种原因对应完全不同的处理动作。泛化的"连接失败"让用户无从下手。

### 坑

**Ollama 的 CORS。** 浏览器不能直连 `localhost:11434`。但我们走后端，后端调 Ollama 没有 CORS 限制——这正是引入后端的收益之一。前端只需要调自己的后端。如果 Ollama 需要 `OLLAMA_ORIGINS` 配置，在后端调用时不存在这个问题。

**测试连通性会真的花一次 token。** 用 `ping` 这种极短的提示词，成本可忽略。

---

## 附：开发顺序与验证方式

| 阶段 | 做完什么 | 怎么验证 |
|---|---|---|
| 1 | 书库增删查 | `curl` 打接口，看 JSON |
| 2 | 文件扫描 + 类型推断 | 指向一个真实书库文件夹，看树上章节数对不对 |
| 3 | 读章节 + 保存正文 | 改字 → 保存 → **用记事本打开磁盘文件确认** |
| 4 | 新建/删除章节 | 建一个章，看磁盘上文件生成了没有 |
| 5 | Frontmatter 往返 | 在 md 里手加一个自定义字段，保存后确认它还在 |
| 6 | 规则引擎 | 单元测试 + 用真实稿件目测误报 |
| 7 | LLM 校对 | 看返回的片段是否都能在原文定位 |
| 8 | 批注锚定 | 改稿后确认波浪线跟着走，改太多则进失效区 |
| 9 | 知识库 | 用 20 章真实稿子，人工核对待确认列表 |

**阶段 3 是所有验证里最关键的一步。** 一定要用记事本实际打开磁盘上的文件看，不能只看接口返回。接口返回对但文件写错了，是最危险的一类 bug。
