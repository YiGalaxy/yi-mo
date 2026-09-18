# Git 速查

只列这个项目会用到的命令。完整手册：https://git-scm.com/book/zh/v2

项目位置 `D:/Project/yi-mo`，单仓库，主分支 `main`。

---

## 项目约定

1. **提交信息不加任何署名**，作者只有你本人。
2. **`main` 分支保持随时能跑**。做一半的功能放分支上，做完再合。
3. **提交前跑测试**：`cd backend && mvn test`。

---

## 每天用的四步

```bash
cd D:/Project/yi-mo

git status                    # 1. 看改了什么
git add .                     # 2. 暂存所有改动
git commit -m "feat: 说明"     # 3. 提交
git push                      # 4. 推到 GitHub
```

**第 1 步不要跳过。** 看一眼有没有 `node_modules`、`target`、`*.log` 混进来。这些一旦提交，从历史里清理要重写历史，很麻烦。

提交信息格式：

| 前缀 | 用于 |
|---|---|
| `feat` | 新功能 |
| `fix` | 修 bug |
| `refactor` | 重构，不改行为 |
| `test` | 只改测试 |
| `docs` | 只改文档 |
| `chore` | 依赖、配置、构建 |

例：

```bash
git commit -m "feat: 书库管理 - 添加与列表"
git commit -m "fix: 保存正文时中文标题乱码"
git commit -m "docs: 补充 Docker 环境说明"
```

---

## 看状态和历史

```bash
git status -sb                    # 简洁状态（分支 + 改动文件）
git status                        # 详细状态

git log --oneline -10             # 最近 10 次提交
git log --oneline --graph --all   # 带分支线，看结构
git log -p docs/08-dev-guide.md   # 看某个文件的修改历史

git diff                          # 改了但还没 add 的内容
git diff --staged                 # 已 add 待提交的内容
git show <hash>                   # 某次提交改了什么

git remote -v                     # 看远程仓库地址
git branch -vv                    # 本地分支及跟踪关系
```

**`git diff --staged` 是提交前最后一道检查**。它会显示你即将提交的全部内容，扫一眼比事后补救省事得多。

---

## 撤销：按"错到哪一步"找

### 改了文件，想还原成上次提交的样子

```bash
git restore 文件路径
git restore .                     # 全部还原
```

**危险**：改动直接消失，**无法恢复**。确认那个文件没有想保留的东西。

### add 了，但还不想提交

```bash
git restore --staged 文件路径      # 取消暂存，文件内容不动
```

安全。文件还在，只是从暂存区拿出来了。

### 提交信息写错了，还没 push

```bash
git commit --amend -m "新的提交信息"
```

安全。会替换掉上一条提交的信息。

### 提交的内容错了，还没 push

```bash
git reset --soft HEAD~1           # 撤销提交，改动保留在暂存区
```

安全，**改动一个字都不会丢**，只是回到了"已 add 未提交"的状态，可以重新提交。

### 已经 push 了，才发现有问题

```bash
git revert <hash>                 # 生成一次「反向提交」抵消它
git push
```

安全。历史记录不动，而是新增一次把改动撤回来的提交。这是**唯一正确的公开分支撤销方式**。

**不要用 `git push --force`。** 除非你完全清楚后果——它会重写远程历史，别的机器上的记录会对不上。

### 提交了不该提交的文件

```bash
git rm --cached 文件名             # 从 git 移除，磁盘文件保留
echo "文件名" >> .gitignore
git commit -m "chore: 移出误提交的文件"
```

如果是**已经 push 的大文件**（比如几百 MB 的 jar），需要重写历史，用 `git filter-repo`，比较麻烦。所以第 1 步的 `git status` 一定要看。

---

## 分支

单人开发也需要分支——它能让你在做一个没完成的功能时，随时切回去修一个紧急的 bug。

```bash
git switch -c feat/library        # 新建并切到新分支
git switch main                   # 切回主分支
git branch                        # 看所有本地分支（* 是当前）

git merge feat/library            # 在 main 上执行，把分支合进来
git branch -d feat/library        # 合并后删除分支
```

**用 `switch` 不用 `checkout`。** `checkout` 一个命令干太多事（切分支、还原文件、切换提交），容易误操作。`switch` 只切分支，语义清晰。Git 2.23+ 都支持。

标准流程：

```bash
git switch -c feat/library        # 1. 开分支
# ...写代码...
git add . && git commit -m "feat: 书库管理"
git switch main                   # 2. 切回 main
git merge feat/library            # 3. 合并
git push                          # 4. 推送
git branch -d feat/library        # 5. 清理分支
```

---

## 救命

### 感觉把东西弄丢了

```bash
git reflog                        # 所有 HEAD 移动记录，包括「已删除」的提交
```

找到想回到的那次操作，然后：

```bash
git switch -c rescue <hash>       # 从那个点开个新分支看看
```

**`reflog` 是 Git 的后悔药。** 只要提交过，基本都能找回来。默认保留 90 天。

### 改到一半，要临时切去修别的东西

```bash
git stash                         # 把当前改动收进抽屉
git switch main                   # 去干别的
# ...
git switch -                           # 回来
git stash pop                     # 从抽屉取出来
```

```bash
git stash list                    # 看抽屉里有几样东西
git stash drop                    # 丢掉最近一次 stash
```

### 拉取时冲突了

```bash
git pull                          # 报冲突
git status                        # 列出冲突的文件
```

打开冲突文件，会看到：

```
<<<<<<< HEAD
你的版本
=======
远程的版本
>>>>>>> origin/main
```

手动改成想要的样子，删掉 `<<<<<<<`、`=======`、`>>>>>>>` 这三行标记，然后：

```bash
git add 冲突文件
git commit                        # 完成合并
```

想放弃合并回到拉取前：

```bash
git merge --abort
```

---

## 这个项目特有的坑

**Windows 行尾警告。** 提交时可能看到：

```
warning: in the working copy of 'xxx', LF will be replaced by CRLF
```

**正常现象，不用管。** 项目根目录有 `.gitattributes` 统一了行尾规则。

**中文文件名。** 默认会显示成 `\346\226\207\344\273\266.md` 这种八进制转义。改成正常显示：

```bash
git config --global core.quotepath false
```

**不需要提交的东西**（`.gitignore` 已处理，列出来是让你别手动 `git add -f`）：

```
backend/target/           Maven 构建产物
frontend/node_modules/    前端依赖
frontend/dist/            前端构建产物
*.log                     日志
.env                      含密钥的配置
```

**数据库容器不在 git 里。** 它由 `docker-compose.yml` 定义，数据存在 Docker 的命名卷里，跟仓库无关。

---

## 一条原则

**不确定的命令，先 `--dry-run` 或者先开分支试。**

```bash
git switch -c try/experiment      # 开个临时分支随便折腾
```

试错了直接删分支，`main` 一点没动。这比事后用 `reflog` 抢救轻松得多。
