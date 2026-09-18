# 书库格式规范 v1

本文件定义亿墨在磁盘上的数据契约。它是项目里唯一需要向后兼容的部分——改动它意味着改动所有作者的书。

## 设计原则

1. **明文优先**。所有作者创作的内容都是 Markdown + YAML frontmatter。不引入自定义二进制格式。
2. **`.yimo/` 可删除**。该目录下的任何内容都是派生数据，删除后能完整重建。作者的内容不在里面。
3. **文件即结构**。卷、章、人物卡的层级关系由目录和文件名表达，不由元数据表达。作者在文件管理器里重命名 / 移动文件，亿墨应当能理解。
4. **不认识的字段要保留**。读取 frontmatter 时遇到未知字段必须原样写回。作者可能用同一批文件喂给别的工具。

## 目录结构

```
<书库根>/                          就是一个普通文件夹
│
├── <项目名>/                      一个文件夹 = 一本书
│   ├── .yimo/                    项目派生数据（可删，删了不影响稿件）
│   │   ├── project.json          项目配置
│   │   ├── snapshots/            版本快照
│   │   │   └── <chapterId>/<时间戳>.md
│   │   └── trash/                删除的章节，保留 30 天
│   │       └── <章节名>.<时间戳>.md
│   │
│   ├── 00-简介.md
│   ├── 01-大纲.md
│   ├── 02-背景设定.md
│   ├── 03-人物/
│   │   ├── 陈平安.md
│   │   └── 宁姚.md
│   ├── 04-地点/
│   ├── 05-物品/
│   ├── 06-组织/
│   ├── 07-正文/
│   │   ├── 第001章-雪夜.md
│   │   └── 第002章-泥瓶巷.md
│   └── 08-草稿箱/
│
└── <另一个项目名>/
```

**目录编号前缀**（`01-`、`03-` 等）用于控制默认排序。作者可以删除编号，亿墨按以下顺序推断类型：目录名包含"正文/章"→ 正文；包含"人物/角色"→ 人物；包含"地点"→ 地点；包含"大纲"→ 大纲。全都不匹配时，该目录按普通文件夹处理，其下所有 `.md` 都可编辑。

**为什么用文件夹最外层隔离项目**：作者可能同时在写多本书，希望每本书能独立 `git init`、独立备份、独立分享。文件夹是唯一能做到这点的结构。

## 文件类型

亿墨识别四类 `.md` 文件，通过 frontmatter 里的 `yimo` 字段区分：

| `yimo` 值 | 含义 | 缺少该字段时 |
|---|---|---|
| `chapter` | 正文章节 | 位于正文目录 → 视为章节 |
| `entity` | 知识库实体（人物/地点/物品/组织/术语） | 位于人物等目录 → 视为实体 |
| `outline` | 大纲 | 位于根目录且文件名含"大纲" → 视为大纲 |
| `note` | 普通笔记 | 兜底类型 |

`yimo` 字段的存在让亿墨能处理"作者自己写的、没有 frontmatter 的 Markdown 文件"——把整个 Obsidian 库当书库用也能跑。

## 章节文件

`07-正文/第001章-雪夜.md`：

```markdown
---
yimo: chapter
id: ch_01H8XYZABCDEF
title: 第一章 雪夜
volume: 第一卷 少年游
order: 1
status: draft
pov: 陈平安
characters: [ent_chenpingan, ent_ningyao]
locations: [ent_nipingxiang]
storyTime: "第三天 · 夜"
summary: 陈平安在城头遇见宁姚，得知小镇即将封闭。
created: 2026-09-18T10:00:00+08:00
updated: 2026-09-18T12:30:00+08:00
---

　　他站在城头，看雪落下来。

　　"你确定要走？"身后有人问。

　　他没有回头。
```

### 字段

| 字段 | 类型 | 必需 | 说明 |
|---|---|---|---|
| `yimo` | `chapter` | 否 | 缺失时由目录推断 |
| `id` | string | 是 | 稳定标识，首次保存时生成，此后不变 |
| `title` | string | 是 | 章节标题，显示用 |
| `volume` | string | 否 | 所属卷名。空 = 未分卷 |
| `order` | number | 否 | 卷内序号。缺失时按文件名排序 |
| `status` | enum | 否 | `draft` / `revising` / `done`，默认 `draft` |
| `pov` | string | 否 | 视角人物（实体 id 或名字） |
| `characters` | string[] | 否 | 出场人物 |
| `locations` | string[] | 否 | 出场地点 |
| `storyTime` | string | 否 | 故事内时间。自由文本，供作者自己描述 |
| `summary` | string | 否 | 一句话梗概。可由 Agent 生成，作者可覆盖 |
| `created` / `updated` | ISO8601 | 是 | 时间戳，带时区 |

**`id` 是唯一强约束**。文件名会改，标题会改，`id` 不能改——所有跨文件引用（批注、实体提及、快照）都挂在 `id` 上。

**`order` 与文件名的关系**：若章节文件名以 `第NNN章` 形式开头，解析出的数字优先于 `order`。作者手改文件名排序应立即生效。

**正文格式约束**：正文只使用 Markdown 基础语法（段落、加粗、斜体、引用、列表、分隔线）。开启 `strictMarkdown`（默认）时，亿墨会在保存时拒绝写入表格、脚注、HTML 标签，避免往返丢失。

**中文排版**：段首缩进用全角空格 `　　`（两个 U+3000）显式写出，不依赖 CSS。这样导出的 TXT 在任意阅读器里都是正确的。亿墨在编辑器中以样式呈现缩进，但落盘时写全角空格。

## 实体文件

`03-人物/陈平安.md`：

```markdown
---
yimo: entity
id: ent_chenpingan
type: character
name: 陈平安
aliases: [平安, 小平安, 隐官, 陈十一]
tags: [主角, 剑修, 泥瓶巷]
status: alive
firstAppear: ch_01H8XYZABCDEF
relations:
  - target: ent_ningyao
    type: 道侣
    since: ch_012ABCDEF
    note: ""
  - target: ent_qijingchun
    type: 师徒
    note: 齐静春代师收徒
attributes:
  境界: 十四境
  本命飞剑: 十五
  籍贯: 骊珠洞天
created: 2026-09-18T10:00:00+08:00
updated: 2026-09-18T12:30:00+08:00
---

## 外貌

## 性格

## 背景

## 能力

## 人物弧光

## 备注
```

### 字段

| 字段 | 类型 | 必需 | 说明 |
|---|---|---|---|
| `yimo` | `entity` | 否 | 缺失时由目录推断 |
| `id` | string | 是 | 稳定标识 |
| `type` | enum | 是 | `character` / `location` / `item` / `org` / `term` |
| `name` | string | 是 | 主名称 |
| `aliases` | string[] | 否 | **别名，中文小说的关键字段** |
| `tags` | string[] | 否 | 自由标签 |
| `status` | enum | 否 | 人物：`alive` / `dead` / `missing` / `unknown`；其他类型可忽略 |
| `firstAppear` | string | 否 | 首次出场章节 id |
| `relations` | object[] | 否 | 有向关系，见下 |
| `attributes` | map | 否 | 自由键值对。不同 `type` 用不同键 |

**`relations` 是有向的**：A 的 `relations` 里写了 B，B 的文件里不一定有 A。显示关系图时双向合并，冲突时以双方都声明且不一致的情况标记为待确认。

**`attributes` 的键名不设白名单**。玄幻写"境界"，悬疑写"动机"，言情写"情感状态"——预设 schema 会立刻变成牢笼。UI 上按 `type` 提供建议键名，但允许任意键。

**正文区（`## 外貌` 等）是自由文本，由作者写**。亿墨不往里塞生成内容，只在作者确认提案时写入。Agent 生成的建议以批注形式挂在文件上，不直接改正文。

## 大纲文件

`01-大纲.md`：

```markdown
---
yimo: outline
id: out_01H8XYZ
title: 剑来 · 总纲
---

## 一句话简介

## 核心冲突

## 三幕结构

### 第一幕：泥瓶巷

- [ ] 小镇封闭
- [x] 陈平安得剑

## 分卷大纲

### 第一卷 少年游

| 章 | 事件 | 状态 |
```

大纲文件只有一层结构，内容完全自由。亿墨只解析任务列表 `- [ ]` 用于进度统计，其余按普通 Markdown 渲染。

## 派生数据（`.yimo/`）

### `project.json`

```json
{
  "schemaVersion": 1,
  "id": "prj_01H8XYZ",
  "name": "剑来",
  "created": "2026-09-18T10:00:00+08:00",
  "settings": {
    "strictMarkdown": true,
    "autoSnapshot": { "enabled": true, "intervalMinutes": 10, "keep": 50 },
    "proofread": { "onSave": true, "ruleEngine": true, "llm": false },
    "agent": { "defaultProfile": "balanced", "autoExtractEntities": true }
  }
}
```

**若 `.yimo/` 被删除**，亿墨应从 `docs/` 目录结构重建出等价的 `project.json`，项目名取自文件夹名。这条必须始终成立。

### 数据库中的派生数据

索引、批注、摘要、对话、统计全部存在 MySQL 里（表结构见 `01-architecture.md` §5）。它们的共同点是**都能从书库文件夹重建**——例外是对话记录和写作统计，那是用户行为数据，丢了不可恢复，需要单独导出。

**章节摘要链**（`summary` 表）是全书分析的核心数据结构。把 N 章的摘要喂给长上下文模型，就能在不塞入全文的情况下做跨章一致性检查。每章一条：

```json
{
  "chapterId": "ch_01H8XYZABCDEF",
  "order": 1,
  "title": "第一章 雪夜",
  "summary": "陈平安在城头遇见宁姚，得知小镇即将封闭。",
  "events": [
    { "text": "陈平安与宁姚初遇", "type": "plot" },
    { "text": "小镇封闭的消息传出", "type": "world" }
  ],
  "entitiesAppear": ["ent_chenpingan", "ent_ningyao"],
  "entitiesMentioned": ["ent_nipingxiang"],
  "stateChanges": [
    { "entity": "ent_chenpingan", "field": "已知信息", "from": "", "to": "小镇将封闭" }
  ],
  "timeline": { "storyTime": "第三天 · 夜", "span": "数小时" }
}
```

**伏笔单独一张 `hook` 表**，因为它有独立的状态流转（open / resolved / abandoned），需要按状态查询。

**实体文件仍是真相源**。`entity` 表是实体文件的扁平投影，用于快速检索和实体消解。检测到文件 mtime 变化时重建对应条目。

**批注（`review` 表）不写进正文 Markdown**，用文本锚点定位。理由见 `01-architecture.md` §8。

### `snapshots/<chapterId>/<ISO8601>.md`

版本快照，纯 Markdown 副本。**不存 diff**——diff 需要基准，基准会被清理，反而变成负担。整章副本在 3000 字规模下压缩后极小。默认保留最近 50 个，按时间淘汰。

## 兼容性与演进

**`schemaVersion`**：出现在 `project.json` 里。当前为 `1`。

**读取规则**：
- 主版本相同 → 直接读。
- 主版本更高 → **只读模式**打开，顶部提示"此项目由更新版本的亿墨创建，请升级"，禁止写入，防止降级破坏数据。
- 主版本更低 → 运行迁移函数，迁移前自动整库备份到 `.yimo/backup/<时间戳>/`。

**写入规则**：
- 保留所有无法识别的 frontmatter 字段，原样写回。
- 保留所有无法识别的目录和文件，不移动、不删除。
- 未知类型的 `.md` 文件当作 `note` 处理，可编辑，不参与索引。

**ID 生成**：ULID 变体，前缀标识类型（`lib_` `bk_` `ch_` `ent_` `rv_` `task_` `th_` `out_`），后接 Crockford Base32 编码的时间戳 + 随机位。单调递增，可按创建时间排序，无中心协调。

**`.gitignore` 建议**（亿墨在项目初始化时提供）：
```gitignore
# 亿墨派生数据，可安全删除或忽略
.yimo/snapshots/
.yimo/trash/
.yimo/backup/
```

**纳入版本控制**：`project.json` 记录项目配置，建议提交。正文、大纲、人物卡是创作内容，当然要提交——这正是把稿件存成明文 Markdown 的意义。批注和对话记录在 MySQL 里，不进 Git；如果需要保留，用亿墨的导出功能。
