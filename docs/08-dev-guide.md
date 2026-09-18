# 开发手册：从零到跑起来

面向第一次搭建前后端分离项目的人。每一步都有具体命令和**预期输出**——看到了预期输出再往下走，看不到就先解决当前这步。

预计节奏：环境与仓库 1 天，骨架 3 天，功能迭代按 §5 逐个推进。

---

# 第 0 章 环境准备

目标：让电脑具备运行亿墨的环境。

**用一个办法搞定最难的部分**：数据库不用装，用 Docker 跑。

手工装 MySQL 要配字符集、时区、建库、建用户、设权限——每一步都可能出错，而且错了之后的现象（中文乱码、时间差 8 小时、连不上）都不直观。Docker 把这些一次性配死，你电脑上根本不需要装 MySQL。

代码仍然在本地跑，不放 Docker。原因：后端本地跑，IDEA 才能打断点调试；前端本地跑，热重载最快。

**要装的东西就四样**：Docker Desktop、JDK、Node.js、IDE。加上已有的 Git。

---

## 0.1 装 Docker Desktop

**作用**：跑 MySQL 数据库。

**下载**：https://www.docker.com/products/docker-desktop/ → Download for Windows

**安装**：一路下一步。装完**重启电脑**。

**第一次启动**：双击桌面图标，等右下角鲸鱼图标不再转圈，说明就绪。

**验证**（打开 Git Bash）：

```bash
docker -v
```

预期：

```
Docker version 27.x.x, build xxxxxxx
```

**报 `command not found`**：Docker Desktop 没启动，先启动它。

**报 `Cannot connect to the Docker daemon`**：同上。

**Windows 家庭版提示需要 WSL2**：按提示执行，然后重启。

```bash
wsl --install
```

---

## 0.2 启动数据库

### 第 1 步：建配置文件

在 `D:\Project\yi-mo` 下新建文件 `docker-compose.yml`，内容：

```yaml
services:
  mysql:
    image: mysql:8.0
    container_name: yimo-mysql
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: root_dev_2026
      MYSQL_DATABASE: yimo
      MYSQL_USER: yimo
      MYSQL_PASSWORD: yimo_dev_2026
      TZ: Asia/Shanghai
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_0900_ai_ci
    ports:
      - "127.0.0.1:3308:3306"
    volumes:
      - mysql-data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-uroot", "-proot_dev_2026"]
      interval: 5s
      timeout: 3s
      retries: 12
      start_period: 30s

volumes:
  mysql-data:
```

这份配置里已经有几个关键的设定，所以后面不会再遇到麻烦：

| 配置 | 解决的问题 |
|---|---|
| `--character-set-server=utf8mb4` | 中文不乱码，生僻字能存 |
| `TZ: Asia/Shanghai` | 写入时间不差 8 小时 |
| `127.0.0.1:3308:3306` | 只监听本机，局域网里别人连不上你的数据库 |
| `mysql-data` 卷 | 容器删了数据还在，稿子不会丢 |
| `healthcheck` | 能判断数据库是不是真的就绪了 |

### 第 2 步：启动

```bash
cd D:/Project/yi-mo
docker compose up -d
```

预期：

```
[+] Running 2/2
 ✔ Network yi-mo_default   Created
 ✔ Container yimo-mysql    Started
```

`-d` 是后台运行的意思，不占着你的终端窗口。

**第一次会下载 MySQL 镜像，约 500MB，要等几分钟。**

### 第 3 步：等它就绪

MySQL 第一次启动要初始化数据目录，需要 20-30 秒。

```bash
docker compose ps
```

预期（重点是 `healthy`）：

```
NAME         IMAGE       STATUS                    PORTS
yimo-mysql   mysql:8.0   Up 40 seconds (healthy)   127.0.0.1:3308->3306/tcp
```

显示 `starting` 就等 10 秒再敲一次。**看到 `healthy` 才能往下走。**

### 第 4 步：验证能连上

```bash
docker compose exec mysql mysql -uyimo -pyimo_dev_2026 -e "SHOW DATABASES;"
```

预期：

```
+--------------------+
| Database           |
+--------------------+
| information_schema |
| performance_schema |
| yimo               |
+--------------------+
```

看到 `yimo` 就对了。

### 第 5 步：验证中文没问题

```bash
docker compose exec mysql mysql -uyimo -pyimo_dev_2026 yimo -e "SELECT @@character_set_database;"
```

预期：

```
+--------------------------+
| @@character_set_database |
+--------------------------+
| utf8mb4                  |
+--------------------------+
```

**必须是 `utf8mb4`，不能是 `utf8`。** MySQL 的 `utf8` 是阉割版，存不了生僻字，小说里出现生僻字是常事。

### 日常命令

```bash
docker compose start           # 开工
docker compose stop            # 收工，数据保留
docker compose down            # 停止并删除容器，数据还在
docker compose down -v         # 连数据一起删，慎用
docker compose logs -f mysql   # 看日志
```

**每天开工敲 `docker compose start`，收工敲 `docker compose stop`。**

### 常见问题

| 现象 | 原因 | 解决 |
|---|---|---|
| `port is already allocated` | 3308 也被别的程序占了 | 换成别的空闲端口，比如 `"127.0.0.1:3310:3306"`，后端配置里的端口同步改。查占用：`netstat -ano \| grep ":3308"` |
| 一直显示 `starting` 超过 2 分钟 | 初始化失败 | `docker compose logs mysql` 看日志 |
| 拉镜像卡住不动 | 网络问题 | Docker Desktop → Settings → Docker Engine，加国内镜像源 |

---

## 0.3 装 JDK

**作用**：跑后端 Java 代码。

**下载**：https://adoptium.net/ → 选 `Temurin 21 (LTS)` → Windows x64 `.msi`

**安装**：勾选 **"Set JAVA_HOME variable"**，一路下一步。

**验证**（关掉 Git Bash 再重开）：

```bash
java -version
```

预期：

```
openjdk version "21.0.x" 2025-xx-xx
OpenJDK Runtime Environment Temurin-21.0.x+xx
```

**必须是 21。** 17 或 8 都不行——项目用到了 record 和虚拟线程，低版本编译不过。

**装错了**：控制面板卸载，用 `where java` 确认当前用的是哪个。

**不用单独装 Maven**。后面创建的项目自带 Maven Wrapper（`mvnw`），它自己会下载正确版本的 Maven。IDEA 里也用内置的。

---

## 0.4 装 Node.js

**作用**：跑前端 Vue 代码。

**下载**：https://nodejs.org/ → 选 `20.x LTS` → Windows `.msi`

**安装**：一路下一步。

**验证**：

```bash
node -v && npm -v
```

预期：

```
v20.x.x
10.x.x
```

**配国内镜像**（不配的话装依赖会很慢）：

```bash
npm config set registry https://registry.npmmirror.com
```

验证：

```bash
npm config get registry
```

预期：

```
https://registry.npmmirror.com/
```

---

## 0.5 装 IDE

**后端用 IntelliJ IDEA**

Community 版免费，够用。https://www.jetbrains.com/idea/download/

**前端用 VS Code**

https://code.visualstudio.com/

装完打开 VS Code，点左侧扩展图标，搜这几个装上：

| 插件 | 作用 |
|---|---|
| Vue - Official | Vue 3 语法高亮和提示 |
| ESLint | 代码检查 |
| Prettier | 保存时自动格式化 |

**注意**：Vue 插件装 `Vue - Official`，**不要装 `Vetur`**。Vetur 是给 Vue 2 的，已经废弃。

---

## 0.6 配置 Git

### 第 1 步：配置身份

```bash
git config --global user.name "你的名字"
git config --global user.email "你的邮箱@example.com"
```

**邮箱用注册 GitHub 时的那个**，否则 GitHub 上认不出这些提交是你做的。

**验证**：

```bash
git config --global --list
```

预期包含：

```
user.name=你的名字
user.email=你的邮箱@example.com
```

### 第 2 步：生成 SSH 密钥

作用：配好之后推送代码不用输密码。

```bash
ssh-keygen -t ed25519 -C "你的邮箱@example.com"
```

**连续按三次回车**（用默认路径、不设密码）。

复制公钥：

```bash
cat ~/.ssh/id_ed25519.pub
```

输出是 `ssh-ed25519 AAAAC3Nza...` 开头的一行，**全部选中复制**。

### 第 3 步：加到 GitHub

GitHub 网页 → 右上角头像 → Settings → 左侧 SSH and GPG keys → New SSH key → 标题随便填 → Key 粘贴 → Add SSH key

### 第 4 步：验证

```bash
ssh -T git@github.com
```

第一次会问 `Are you sure you want to continue connecting?`，输入 `yes` 回车。

预期：

```
Hi <你的用户名>! You've successfully authenticated...
```

看到 `successfully authenticated` 就成了。

**卡住超过 30 秒**：国内 22 端口常被墙。新建文件 `~/.ssh/config`，写入：

```
Host github.com
  Hostname ssh.github.com
  Port 443
  User git
```

再试一次。

---

## 0.7 初始化仓库

### 第 1 步：建目录

```bash
cd D:/Project/yi-mo
mkdir -p backend frontend python scripts .github/workflows
echo "0.1.0" > VERSION
```

**验证**：

```bash
ls
```

预期看到：

```
backend  docs  frontend  LICENSE  openapi  python  README.md  scripts  VERSION
```

### 第 2 步：写 .gitignore

在 `D:\Project\yi-mo` 下新建 `.gitignore`：

```gitignore
# ===== Java =====
backend/target/
*.class
*.jar
!.mvn/wrapper/maven-wrapper.jar

# ===== Node / 前端 =====
frontend/node_modules/
frontend/dist/
frontend/.vite/
frontend/coverage/
npm-debug.log*

# ===== Python =====
python/__pycache__/
python/.venv/
python/venv/
*.py[cod]
.pytest_cache/
.ruff_cache/

# ===== IDE =====
.idea/
*.iml
.vscode/
!.vscode/extensions.json

# ===== 操作系统 =====
.DS_Store
Thumbs.db

# ===== 项目特定 =====
test-library/
logs/
*.log
.env
.env.local
```

**为什么必须现在写**：一旦 `node_modules` 或 `target` 被提交进去，要从 Git 历史里清理就得重写历史，非常麻烦。现在写好，成本为零。

### 第 3 步：初始化

```bash
git init
git branch -M main
```

**验证**：

```bash
git status
```

预期看到一串红色的 `Untracked files`。

### 第 4 步：首次提交

```bash
git add .
git status
```

**先看这个输出，确认三件事再提交**：

1. 没有 `node_modules/`、`target/`、`.idea/`
2. `docs/`、`openapi/` 里的文档都在
3. `LICENSE`、`README.md` 在

有问题就改 `.gitignore`，然后重来：

```bash
git rm -r --cached .
git add .
git status
```

确认干净后：

```bash
git commit -m "chore: 初始化仓库与目录结构"
```

**验证**：

```bash
git log --oneline
```

预期：

```
a1b2c3d (HEAD -> main) chore: 初始化仓库与目录结构
```

### 第 5 步：建 GitHub 仓库

打开 https://github.com/new：

| 项 | 填什么 |
|---|---|
| Repository name | `yi-mo` |
| Public / Private | Public |
| Add a README file | **不勾** |
| Add .gitignore | **None** |
| Choose a license | **None** |

**后三项千万别勾。** 本地已经有这些文件，GitHub 上再生成一份会导致推送冲突。

### 第 6 步：推送

```bash
git remote add origin git@github.com:<你的用户名>/yi-mo.git
git push -u origin main
```

`<你的用户名>` 换成你的 GitHub 用户名。

**验证**：

```bash
git status
```

预期：

```
On branch main
Your branch is up to date with 'origin/main'.
nothing to commit, working tree clean
```

刷新 GitHub 页面，确认文件都在，LICENSE 显示为 `AGPL-3.0 license`。

### 常见报错

| 报错 | 原因 | 解决 |
|---|---|---|
| `Permission denied (publickey)` | SSH 没配好 | 回 §0.6 第 4 步 |
| `Connection timed out` | 22 端口被墙 | 用 §0.6 的 443 方案 |
| `failed to push some refs` | GitHub 上有本地没有的文件 | 建仓库时勾了 README 或 license。删掉 GitHub 仓库重建 |
| `remote origin already exists` | 已经加过 remote | `git remote set-url origin <新地址>` |

---

## 环境验证清单

全部做完后跑这一条：

```bash
docker compose ps && java -version && node -v && git --version
```

四条都有正常输出，环境就绪，进入第 1 章。

**日常开工流程**：

```bash
cd D:/Project/yi-mo
docker compose start      # 起数据库
```

---

# 第 1 章 后端骨架

**目标**：跑起来一个 Spring Boot 服务，访问 `http://127.0.0.1:18080/api/ping` 返回 JSON。

## 1.1 创建项目

打开 https://start.spring.io/，按下表填：

| 项 | 值 |
|---|---|
| Project | Maven |
| Language | Java |
| Spring Boot | 4.1.1（选最新的 4.x 正式版，不要选 M 或 SNAPSHOT） |
| Group | `com.yimo` |
| Artifact | `yimo-backend` |
| Name | `yimo-backend` |
| Package name | `com.yimo` |
| Packaging | Jar |
| Java | 21 |

**Dependencies** 选三个：

- Spring Web
- MySQL Driver
- Validation

点 **GENERATE**，下载 `yimo-backend.zip`，解压到 `D:\Project\yi-mo\backend`。

**两个会卡住的地方**：

**1. Initializr 显示的版本号和 Maven 里实际的不一样。** 网页上写 `4.1.1.RELEASE`，但 Maven 仓库里的坐标是 `4.1.1`。生成的项目里 pom 是对的，但如果你手动改版本号，**不要带 `.RELEASE` 后缀**——带后缀的 POM 解析不到，会报：

```
Non-resolvable parent POM ... Could not find artifact
org.springframework.boot:spring-boot-starter-parent:pom:4.1.1.RELEASE
```

**2. Spring Boot 4 的 starter 名字变了。** 生成出来的 pom 里是 `spring-boot-starter-webmvc` 而不是 `spring-boot-starter-web`。这不是错误，是 4.x 的新命名，照抄老教程会编译失败。

| Spring Boot 3.x | 4.x |
|---|---|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `spring-boot-starter-test` | 按模块拆成 `spring-boot-starter-webmvc-test` 等 |
| `mybatis-plus-spring-boot3-starter` | `mybatis-plus-spring-boot4-starter` |

## 1.2 配镜像并补齐依赖

### 先配 Maven 镜像

**这一步必须做**，否则从中央仓库下载依赖会慢到没法开发（几百 MB，可能几十分钟）。

在 Git Bash 里执行，写一份 Maven 配置：

```bash
mkdir -p ~/.m2
cat > ~/.m2/settings.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>central</mirrorOf>
      <name>Aliyun Maven</name>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
EOF
```

**验证**：

```bash
cat ~/.m2/settings.xml
```

能看到刚写进去的内容就对了。

### 再补依赖

Initializr 给的是基础依赖，MyBatis-Plus、HanLP、Hutool 要手动加。

打开 `backend/pom.xml`，在 `<dependencies>` 里加：

```xml
<!-- MyBatis-Plus：数据库访问 -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
    <version>3.5.17</version>
</dependency>

<!-- 中文分词与 NLP -->
<dependency>
    <groupId>com.hankcs</groupId>
    <artifactId>hanlp</artifactId>
    <version>portable-1.8.6</version>
</dependency>

<!-- 工具集：文件、字符串、ID 生成 -->
<dependency>
    <groupId>cn.hutool</groupId>
    <artifactId>hutool-all</artifactId>
    <version>5.8.47</version>
</dependency>
```

**Spring AI 的依赖等做到 §5.7 再加**，现在加了也没用，还多一个下载失败的排查点。

在 `<properties>` 里确认：

```xml
<java.version>21</java.version>
```

**验证**：在 `backend` 目录下执行

```bash
mvn clean compile
```

预期最后几行：

```
[INFO] BUILD SUCCESS
[INFO] Total time:  12.345 s
```

**卡在下载依赖**：检查 §0.3 的阿里云镜像配好没有。第一次跑要下载几百 MB，做好等几分钟的准备。

## 1.3 认识目录结构

```
backend/
├── pom.xml                      依赖清单
├── src/main/java/com/yimo/
│   └── YimoApplication.java     入口
├── src/main/resources/
│   ├── application.yml          配置
│   └── static/                  前端构建产物会拷到这里
└── src/test/java/com/yimo/      测试
```

**按照 `05-tech-stack.md` 的分层，我们要建这些包**：

```
com.yimo
├── YimoApplication.java
├── common/          通用：错误码、异常、工具
│   ├── BizException.java
│   ├── ErrorCode.java
│   ├── GlobalExceptionHandler.java
│   └── ApiError.java
├── controller/      REST 接口
├── service/         业务逻辑
├── domain/          领域模型
├── dto/             请求/响应对象（record）
├── mapper/          数据库访问（MyBatis-Plus）
├── storage/         书库文件读写
├── agent/           AI 编排
└── config/          配置类
```

在 IDEA 里右键 `com.yimo` → New → Package，逐个建出来。**先建空包没关系**，结构清楚了后面不迷路。

## 1.4 配置

把 `src/main/resources/application.properties` 删掉，新建 `application.yml`：

```yaml
server:
  address: 127.0.0.1          # 只监听本机，不暴露到局域网
  port: 18080

spring:
  application:
    name: yimo
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://127.0.0.1:3308/yimo?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
    username: yimo
    password: yimo_dev_2026
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true     # user_name → userName
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl   # 开发时打印 SQL

logging:
  level:
    com.yimo: DEBUG
```

**关于 `server.address: 127.0.0.1`**：亿墨没有认证，绑定 `0.0.0.0` 意味着同一个 Wi-Fi 下任何人都能读写你的稿件。这条不是可选项。

**关于 JDBC URL 的参数**：

| 参数 | 作用 |
|---|---|
| `serverTimezone=Asia/Shanghai` | 不加会报时区错误，`datetime` 字段会差 8 小时 |
| `allowPublicKeyRetrieval=true` | MySQL 8 默认认证方式需要，不加会连不上 |
| `characterEncoding=UTF-8` | 中文不乱码。**必须写 `UTF-8`，不能写 `utf8mb4`**——MySQL 驱动不认后者，会报 `Unsupported character encoding 'utf8mb4'`，服务直接起不来 |

## 1.5 第一个接口

新建 `com/yimo/controller/PingController.java`：

```java
package com.yimo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/ping")
public class PingController {

    @GetMapping
    public Map<String, Object> ping() {
        return Map.of(
            "ok", true,
            "service", "yimo",
            "time", OffsetDateTime.now().toString()
        );
    }
}
```

## 1.6 启动并验证

**方式一：IDEA**

打开 `YimoApplication.java`，点左边绿色三角 → Run。

**方式二：命令行**

```bash
cd D:/Project/yi-mo/backend
mvn spring-boot:run
```

**预期输出**（结尾）：

```
Started YimoApplication in 2.345 seconds (process running for 2.891)
Tomcat started on port 18080 (http) with context path ''
```

**测试**（另开一个终端）：

```bash
curl http://127.0.0.1:18080/api/ping
```

预期：

```json
{"ok":true,"service":"yimo","time":"2026-09-18T14:30:00+08:00","sampleId":"ch_01M2SH979XJAM0FAXK2P5K04G8"}
```

`sampleId` 是后端现生成的 ULID。看到它就说明 ID 生成、JSON 序列化都正常。

**到这里第 1 章完成。** 如果连不上，按顺序检查：

| 现象 | 原因 |
|---|---|
| `Port 18080 was already in use` | 端口被占。亿墨默认用 18080 就是为了避开 8080；如果这个也被占了，换成别的（如 18081），**前端的 `vite.config.ts` 里的代理目标要同步改** |
| `Failed to configure a DataSource` | 数据库连不上。先 `docker compose ps` 看是不是 `healthy`，再查用户名密码 |
| `Non-resolvable parent POM` | pom 里的 Spring Boot 版本带了 `.RELEASE` 后缀。改成 `4.1.1` |
| 访问返回 404 | Controller 不在 `com.yimo` 包下面。Spring Boot 只扫描入口类所在包及其子包 |
| 中文变问号 | 数据库字符集不对，回 §0.2 第 5 步检查 |

---

# 第 2 章 连接 MySQL

**目标**：建一张表，写一个 Mapper，通过接口读出真实数据。

## 2.0 怎么执行 SQL

数据库跑在 Docker 容器里，不是装在电脑上。执行 SQL 有两种方式。

**方式一：命令行**（推荐）

```bash
cd D:/Project/yi-mo
docker compose exec mysql mysql -uyimo -pyimo_dev_2026 yimo
```

提示符会变成 `mysql>`。直接粘贴 SQL 回车执行。输入 `exit` 退出。

**方式二：图形化工具**

装 DBeaver（免费）或 Navicat，用下面的信息连接：

| 项 | 值 |
|---|---|
| 主机 | `127.0.0.1` |
| 端口 | `3308` |
| 数据库 | `yimo` |
| 用户名 | `yimo` |
| 密码 | `yimo_dev_2026` |

**这五样后面配置后端时还要用，记下来。**

## 2.1 建表

先只建最小的一张表，验证链路通。

```sql
USE yimo;

CREATE TABLE library (
  id           CHAR(26)     NOT NULL PRIMARY KEY COMMENT 'ULID',
  name         VARCHAR(100) NOT NULL,
  path         VARCHAR(500) NOT NULL,
  last_opened  DATETIME     NULL,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_path (path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

**验证**：

```sql
DESC library;
```

## 2.2 引入 Lombok

实体类需要 getter / setter，但一个字段配两个方法，写起来又长又没营养。用 Lombok 自动生成。

**第 1 步：加依赖**

`backend/pom.xml` 的 `<dependencies>` 里加：

```xml
<!-- 样板代码消除。版本由 Spring Boot 的 parent 管理，不需要写 -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
```

`<optional>true</optional>` 表示这个依赖不传递给引用本项目的下游。

**第 2 步：让打包时排除它**

Lombok 只在**编译期**工作（生成代码），运行期完全用不到。所以打出的 jar 里不该包含它。

同一个 pom 的 `<build>` 里：

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <excludes>
            <exclude>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
            </exclude>
        </excludes>
    </configuration>
</plugin>
```

**第 3 步：写实体类**

新建 `com/yimo/domain/Library.java`：

```java
package com.yimo.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 书库。一个书库 = 磁盘上的一个文件夹，里面装着若干本书。
 */
@Data
@TableName("library")
public class Library {

    /** INPUT 表示主键由我们自己的代码赋值（这里用 ULID），不是数据库自增 */
    @TableId(type = IdType.INPUT)
    private String id;

    private String name;
    private String path;
    private LocalDateTime lastOpened;
    private LocalDateTime createdAt;
}
```

**`@Data` 在编译时生成**：所有字段的 getter / setter，以及 `toString` / `equals` / `hashCode`。

**用普通类不用 record**：MyBatis-Plus 需要无参构造函数和 setter。record 是不可变的，不适合做数据库实体。

**DTO 用 record，实体用普通类。** 这是清晰的分工——只有实体需要 Lombok。

### 为什么 getter / setter 不能省

`@Data` 挡在前面，你很难再犯这个错。但**必须知道它挡住的是什么**——这个 bug 极其阴险：

**现象**：接口返回 200，内容是 `[{}]`。数组里确实有元素，字段却全是空的。

| 缺什么 | 后果 |
|---|---|
| 缺 getter | Jackson 序列化成 JSON 时**只认 getter**，读不到的字段直接不输出。这就是 `[{}]` 的来源 |
| 缺 setter | MyBatis 从数据库读结果时写不进对象，字段全是 null |

**它不报错、不抛异常、日志里什么都没有**，数据就这么凭空消失了。第一次遇到根本想不到是这个原因。

**IDEA 里确认 Lombok 生效**：写完后 `Build → Recompile`，然后 `View → Show Bytecode`，能看到生成出来的 getter。

**IDEA 需要开注解处理**：`Settings → Build, Execution, Deployment → Compiler → Annotation Processors`，勾选 `Enable annotation processing`。新版 IDEA 装好 Lombok 插件后默认就是开的。

**不用 Lombok 的话**：光标放在类名上按 `Alt + Insert` → `Getter and Setter` → 全选字段 → 确定，效果一样，只是代码里多几十行。

## 2.3 Mapper

新建 `com/yimo/mapper/LibraryMapper.java`：

```java
package com.yimo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yimo.domain.Library;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LibraryMapper extends BaseMapper<Library> {
}
```

**继承 `BaseMapper` 就自带增删改查**，不用写一行 XML。这是 MyBatis-Plus 的核心价值。

记得在启动类上加 `@MapperScan`：

```java
@SpringBootApplication
@MapperScan("com.yimo.mapper")
public class YimoApplication {
    public static void main(String[] args) {
        SpringApplication.run(YimoApplication.class, args);
    }
}
```

## 2.4 测试接口

新建 `com/yimo/controller/LibraryController.java`：

```java
package com.yimo.controller;

import com.yimo.domain.Library;
import com.yimo.mapper.LibraryMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/libraries")
public class LibraryController {

    private final LibraryMapper libraryMapper;

    public LibraryController(LibraryMapper libraryMapper) {
        this.libraryMapper = libraryMapper;
    }

    @GetMapping
    public List<Library> list() {
        return libraryMapper.selectList(null);
    }
}
```

**用构造器注入，不用 `@Autowired` 字段注入。** 构造器注入的依赖是 final 的，且缺依赖时启动就报错，不会拖到运行时。

## 2.5 插一条数据验证

**先看一个必须避开的坑。**

在 PowerShell 或 Git Bash 里直接敲带中文的 `INSERT`，中文会变成 `????`：

```
id                      name    path
lib_01H8XYZ...          ????    D:\Writing\??
```

原因：`docker compose exec` 里的 mysql 客户端默认字符集是 **latin1**。中文在传过去之前就被错误解析了，而且**这种丢失不可逆**——`????` 不是显示问题，是数据真的坏了。

**正确的方式**：显式指定 `--default-character-set=utf8mb4`，并用 heredoc 传 SQL（避免命令行参数被 shell 二次编码）。

```bash
cd D:/Project/yi-mo
docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uyimo -pyimo_dev_2026 yimo << 'SQLEOF'
INSERT INTO library (id, name, path)
VALUES ('lib_01M2SK000000000000000000A1', '测试书库', 'D:/Writing/测试小说');
SQLEOF
```

**`path` 这里用正斜杠 `/`。** 反斜杠在 SQL 字符串里是转义字符，写 `'D:\Writing'` 会被解析成 `D:Writing`（反斜杠消失）。要用反斜杠得写 `CONCAT('D:', CHAR(92), 'Writing')`，太麻烦——直接用正斜杠，Java 的 `Path` 在 Windows 上能正确处理。

验证插入结果：

```bash
docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uyimo -pyimo_dev_2026 yimo -e "SELECT name, path FROM library;"
```

**验证接口**：

```bash
curl http://127.0.0.1:18080/api/libraries
```

预期：

```json
[{"id":"lib_01M2SK000000000000000000A1","name":"测试书库","path":"D:/Writing/测试小说","lastOpened":null,"createdAt":"2026-09-18T17:18:34"}]
```

**中文正确显示**，说明编码链路是通的。

**`lastOpened` 是 null 但其他字段有值**——正确，因为插入时没给这个字段。

**如果结果是 `[{}]`**（数组里有元素但字段全空），说明 `Library` 类缺 getter。见 §2.2 的「为什么 getter / setter 不能省」。

**到这里第 2 章完成。** 数据库链路通了，后面所有持久化都照这个模式来。

---

# 第 3 章 前端骨架

**目标**：跑起来一个 Vue 页面，能调到后端的 `/api/libraries`。

## 3.1 创建项目

```bash
cd D:/Project/yi-mo
npm create vue@latest frontend
```

按提示选择：

```
✔ Add TypeScript?                          Yes
✔ Add JSX Support?                         No
✔ Add Vue Router?                          Yes
✔ Add Pinia?                               Yes
✔ Add Vitest?                              Yes
✔ Add End-to-End Testing?                  No
✔ Add ESLint?                              Yes
✔ Add Prettier?                            Yes
```

然后：

```bash
cd frontend
npm install
npm run dev
```

**预期输出**：

```
  VITE v7.x.x  ready in 1234 ms

  ➜  Local:   http://localhost:5173/
```

浏览器打开 `http://localhost:5173`，看到 Vue 欢迎页。

## 3.2 装额外依赖

```bash
npm install axios naive-ui @tiptap/vue-3 @tiptap/starter-kit
npm install -D tailwindcss @tailwindcss/vite
```

| 包 | 用途 |
|---|---|
| `axios` | HTTP 请求 |
| `naive-ui` | 组件库 |
| `@tiptap/vue-3` | 编辑器 |
| `@tiptap/starter-kit` | 编辑器基础扩展 |
| `tailwindcss` | 样式 |

## 3.3 配置代理

开发时前端跑在 5173，后端跑在 18080。浏览器直接请求 18080 会撞上跨域限制。**在 Vite 里配代理绕过去**。

编辑 `frontend/vite.config.ts`：

```ts
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

export default defineConfig({
  plugins: [vue(), vueDevTools()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:18080',
        changeOrigin: true,
      },
    },
  },
})
```

**原理**：前端请求 `/api/libraries`（相对路径），Vite 开发服务器拦截并转发到 18080。对浏览器来说始终是同源的，没有跨域。

**这样写的好处**：生产环境前后端同源（前端产物打进 jar），代码一个字都不用改。

**验证**：重启 dev server（改配置要重启），然后浏览器直接访问

```
http://localhost:5173/api/libraries
```

**预期**：看到和 `curl http://127.0.0.1:18080/api/libraries` 一样的 JSON。代理通了。

## 3.4 封装 axios

新建 `frontend/src/api/http.ts`：

```ts
import axios from 'axios'

export const http = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

// 响应拦截：成功直接返回数据体，失败统一提示
http.interceptors.response.use(
  (res) => res.data,
  (err) => {
    const e = err.response?.data
    console.error('[API]', e ?? err.message)
    return Promise.reject(e ?? { error: 'NETWORK_ERROR', message: '网络错误' })
  },
)
```

**`http.get(...)` 的返回值直接就是数据**，不用每次 `.data.data`。

新建 `frontend/src/api/library.ts`：

```ts
import { http } from './http'

export interface Library {
  id: string
  name: string
  path: string
  bookCount?: number
  wordCount?: number
  lastOpenedAt: string | null
  createdAt: string
}

export const libraryApi = {
  list: () => http.get<any, Library[]>('/libraries'),
}
```

**注意 `http.get<any, Library[]>`**：axios 的类型定义里第二个泛型才是返回值类型。因为拦截器改了返回值，第一个泛型要写 `any`。

## 3.5 第一个页面

编辑 `frontend/src/App.vue`：

```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { libraryApi, type Library } from '@/api/library'

const libraries = ref<Library[]>([])
const loading = ref(false)
const error = ref('')

onMounted(async () => {
  loading.value = true
  try {
    libraries.value = await libraryApi.list()
  } catch (e: any) {
    error.value = e?.message ?? '加载失败'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div style="padding: 24px">
    <h1>亿墨 YI-MO</h1>

    <p v-if="loading">加载中…</p>
    <p v-else-if="error" style="color: red">{{ error }}</p>
    <p v-else-if="libraries.length === 0">还没有书库</p>

    <ul v-else>
      <li v-for="lib in libraries" :key="lib.id">
        {{ lib.name }} — <code>{{ lib.path }}</code>
      </li>
    </ul>
  </div>
</template>
```

**验证**：刷新浏览器，看到第 2 章插入的「测试书库」。

**到这里第 3 章完成。** 前后端链路全通了。

---

# 第 4 章 打好地基

这三件事现在做，比后面出问题了再补便宜得多。

## 4.1 统一错误处理

现在后端抛异常时，前端收到的是 Spring 默认的错误页（HTML 或一大坨 JSON）。要统一成 `06-api.md` 定义的格式。

新建 `com/yimo/common/ErrorCode.java`：

```java
package com.yimo.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    LIBRARY_NOT_FOUND(HttpStatus.NOT_FOUND, "书库不存在"),
    LIBRARY_PATH_INVALID(HttpStatus.BAD_REQUEST, "书库路径无效或不可读"),
    LIBRARY_PATH_DUPLICATE(HttpStatus.CONFLICT, "该路径已添加过"),

    CHAPTER_NOT_FOUND(HttpStatus.NOT_FOUND, "章节不存在"),
    CHAPTER_TITLE_DUPLICATE(HttpStatus.CONFLICT, "同目录下已有同名章节"),

    PATH_OUT_OF_BOUNDS(HttpStatus.FORBIDDEN, "路径越界"),
    FILE_READ_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "文件读取失败"),
    FILE_WRITE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "文件写入失败"),
    CONTENT_HASH_MISMATCH(HttpStatus.CONFLICT, "文件已被外部修改"),

    MODEL_NOT_CONFIGURED(HttpStatus.BAD_REQUEST, "尚未配置模型"),
    AI_CALL_FAILED(HttpStatus.BAD_GATEWAY, "模型调用失败"),
    AI_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "已达每日用量上限"),
    ;

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() { return status; }
    public String message() { return message; }
}
```

新建 `com/yimo/common/BizException.java`：

```java
package com.yimo.common;

import java.util.Map;

public class BizException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> details;

    public BizException(ErrorCode code) {
        this(code, code.message(), Map.of());
    }

    public BizException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public BizException(ErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    public ErrorCode code() { return code; }
    public Map<String, Object> details() { return details; }
}
```

新建 `com/yimo/common/GlobalExceptionHandler.java`：

```java
package com.yimo.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Map<String, Object>> handleBiz(BizException e) {
        log.warn("业务异常: {} - {}", e.code().name(), e.getMessage());
        return ResponseEntity.status(e.code().status()).body(body(
            e.code().name(), e.getMessage(), e.details()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception e) {
        log.error("未预期异常", e);
        return ResponseEntity.status(500).body(body(
            "INTERNAL_ERROR", "服务内部错误", Map.of()));
    }

    private Map<String, Object> body(String error, String message, Map<String, Object> details) {
        return Map.of(
            "error", error,
            "message", message,
            "details", details,
            "timestamp", OffsetDateTime.now().toString()
        );
    }
}
```

**验证**：把 `LibraryController` 临时改成抛异常：

```java
@GetMapping
public List<Library> list() {
    throw new BizException(ErrorCode.LIBRARY_NOT_FOUND);
}
```

```bash
curl -i http://127.0.0.1:18080/api/libraries
```

预期：

```
HTTP/1.1 404
Content-Type: application/json

{"error":"LIBRARY_NOT_FOUND","message":"书库不存在","details":{},"timestamp":"..."}
```

改回来。

## 4.2 ULID 生成

`02-library-format.md` 要求 ID 是 26 字符 ULID 带类型前缀。Hutool 自带 ULID：

```java
package com.yimo.common;

import cn.hutool.core.lang.ULID;

public final class Ids {

    private Ids() {}

    public static String generate(String prefix) {
        return prefix + ULID.generate();
    }

    public static String library() { return generate("lib_"); }
    public static String book()    { return generate("bk_"); }
    public static String chapter() { return generate("ch_"); }
    public static String entity()  { return generate("ent_"); }
    public static String review()  { return generate("rv_"); }
    public static String task()    { return generate("task_"); }
    public static String thread()  { return generate("th_"); }
}
```

**验证**：写个单元测试。

```java
@Test
void ulidIsMonotonic() {
    String a = Ids.chapter();
    String b = Ids.chapter();
    assertThat(a).startsWith("ch_").hasSize(30);
    assertThat(a.compareTo(b)).isLessThan(0);   // 单调递增，可按创建时间排序
}
```

## 4.3 路径安全

**这是整个项目最不能出错的地方。** 所有涉及用户输入的路径，必须校验解析后的位置仍在书库根目录内。

```java
package com.yimo.storage;

import com.yimo.common.BizException;
import com.yimo.common.ErrorCode;

import java.nio.file.Path;

public final class PathGuard {

    private PathGuard() {}

    /**
     * 把相对路径解析成安全绝对路径。越界直接抛异常。
     */
    public static Path resolve(Path root, String relative) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path target = normalizedRoot.resolve(relative).normalize();

        if (!target.startsWith(normalizedRoot)) {
            throw new BizException(ErrorCode.PATH_OUT_OF_BOUNDS,
                "路径越界: " + relative);
        }
        return target;
    }
}
```

**验证**（这是安全测试，必须写）：

```java
@Test
void blocksPathTraversal() {
    Path root = Path.of("D:/Writing/小说");

    assertThatThrownBy(() -> PathGuard.resolve(root, "../../Windows/System32/config"))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("路径越界");

    assertThat(PathGuard.resolve(root, "剑来/07-正文/第001章.md"))
        .isEqualTo(Path.of("D:/Writing/小说/剑来/07-正文/第001章.md"));
}
```

**这个测试要一直存在。** 后面任何改动都不能让它变红。

---

# 第 5 章 功能迭代

每个迭代都是一个完整的"目标 → 后端 → 前端 → 验证"闭环。**做完一个再做下一个**，不要并行。

迭代顺序经过设计：每一步都建立在前一步已验证的基础上。

---

## 迭代 1：书库管理

**目标**：能添加一个文件夹作为书库，列表里能看到，能移除。

### 后端

**1. DTO**（`com/yimo/dto/`）

```java
public record LibraryCreateRequest(String path, String name) {}

public record LibraryView(
    String id, String name, String path,
    int bookCount, long wordCount,
    OffsetDateTime lastOpenedAt, OffsetDateTime createdAt
) {}
```

**2. Service**（`com/yimo/service/LibraryService.java`）

```java
@Service
public class LibraryService {

    private final LibraryMapper libraryMapper;

    public LibraryService(LibraryMapper libraryMapper) {
        this.libraryMapper = libraryMapper;
    }

    public LibraryView create(LibraryCreateRequest req) {
        Path path = Path.of(req.path()).toAbsolutePath().normalize();

        if (!Files.isDirectory(path)) {
            throw new BizException(ErrorCode.LIBRARY_PATH_INVALID, "目录不存在: " + path);
        }
        if (!Files.isReadable(path)) {
            throw new BizException(ErrorCode.LIBRARY_PATH_INVALID, "目录不可读: " + path);
        }
        Long exists = libraryMapper.selectCount(
            new LambdaQueryWrapper<Library>().eq(Library::getPath, path.toString()));
        if (exists > 0) {
            throw new BizException(ErrorCode.LIBRARY_PATH_DUPLICATE);
        }

        Library lib = new Library();
        lib.setId(Ids.library());
        lib.setPath(path.toString());
        lib.setName(req.name() != null && !req.name().isBlank()
            ? req.name()
            : path.getFileName().toString());
        lib.setCreatedAt(LocalDateTime.now());
        libraryMapper.insert(lib);

        return toView(lib);
    }

    public List<LibraryView> list() {
        return libraryMapper.selectList(
            new LambdaQueryWrapper<Library>().orderByDesc(Library::getLastOpened))
            .stream().map(this::toView).toList();
    }

    public void remove(String id) {
        Library lib = libraryMapper.selectById(id);
        if (lib == null) throw new BizException(ErrorCode.LIBRARY_NOT_FOUND);
        // 只删数据库记录，磁盘文件一个字不动
        libraryMapper.deleteById(id);
    }

    private LibraryView toView(Library lib) {
        return new LibraryView(lib.getId(), lib.getName(), lib.getPath(),
            0, 0, lib.getLastOpened(), lib.getCreatedAt());
    }
}
```

**3. Controller**

```java
@RestController
@RequestMapping("/api/libraries")
public class LibraryController {

    private final LibraryService libraryService;

    public LibraryController(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    @GetMapping
    public List<LibraryView> list() {
        return libraryService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LibraryView create(@RequestBody @Valid LibraryCreateRequest req) {
        return libraryService.create(req);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> remove(@PathVariable String id) {
        libraryService.remove(id);
        return Map.of("id", id, "removed", true, "filesDeleted", false);
    }
}
```

`@Valid` 配上 DTO 里的 `@NotBlank` 做参数校验：

```java
public record LibraryCreateRequest(
    @NotBlank(message = "路径不能为空") String path,
    String name
) {}
```

### 前端

**1. 收窄类型**（`src/api/library.ts` 补上创建和删除）

```ts
export const libraryApi = {
  list: () => http.get<any, Library[]>('/libraries'),
  create: (data: { path: string; name?: string }) =>
    http.post<any, Library>('/libraries', data),
  remove: (id: string) =>
    http.delete<any, { id: string; removed: boolean; filesDeleted: boolean }>(`/libraries/${id}`),
}
```

**2. 页面**（`src/views/LibraryView.vue`）

用 Naive UI 组装：一个列表 + 一个"添加书库"按钮 + 弹窗输入路径。

```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { NCard, NButton, NModal, NInput, NList, NListItem, NEmpty, useMessage } from 'naive-ui'
import { libraryApi, type Library } from '@/api/library'

const message = useMessage()
const libraries = ref<Library[]>([])
const showDialog = ref(false)
const newPath = ref('')
const submitting = ref(false)

async function load() {
  libraries.value = await libraryApi.list()
}

async function submit() {
  if (!newPath.value.trim()) return
  submitting.value = true
  try {
    await libraryApi.create({ path: newPath.value.trim() })
    message.success('书库已添加')
    showDialog.value = false
    newPath.value = ''
    await load()
  } catch (e: any) {
    message.error(e?.message ?? '添加失败')
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>
```

**注意**：浏览器出于安全限制，**不能通过网页获取用户选择的绝对路径**（`<input type="file" webkitdirectory>` 只给相对路径）。所以路径要手填或粘贴。

这是引入后端之后仍然存在的一个限制。**后续可以做成：后端弹一个原生的目录选择对话框**（Java 的 `JFileChooser`），前端调接口触发。这属于 M6 的打磨项，现在手填够用。

### 验证清单

| 检查 | 怎么做 | 预期 |
|---|---|---|
| 添加成功 | 填一个真实存在的目录 | 列表里出现 |
| 重复添加被拒 | 再填同一个路径 | 提示"该路径已添加过" |
| 无效路径被拒 | 填 `D:\不存在的目录` | 提示"目录不存在" |
| 中文路径正常 | 填一个带中文的目录 | 正常显示不乱码 |
| 移除不删文件 | 点移除，然后去文件管理器看 | 文件夹还在，里面的文件都在 |

**最后一条特别重要。** 每次改到删除逻辑，都要手动验证一次。

---

## 迭代 2：文件扫描与章节树

**目标**：添加书库后，自动扫描出里面的书和章节，前端树形展示。

### 后端

**1. 补表**

```sql
CREATE TABLE chapter (
  id            CHAR(26)     NOT NULL PRIMARY KEY,
  library_id    CHAR(26)     NOT NULL,
  book_name     VARCHAR(200) NOT NULL,
  rel_path      VARCHAR(500) NOT NULL,
  title         VARCHAR(200),
  volume        VARCHAR(200),
  sort_order    INT,
  status        VARCHAR(20)  DEFAULT 'draft',
  word_count    INT          DEFAULT 0,
  content_hash  CHAR(64),
  pov           VARCHAR(200),
  story_time    VARCHAR(200),
  updated_at    DATETIME,
  UNIQUE KEY uk_library_path (library_id, rel_path),
  KEY idx_order (library_id, book_name, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

**2. Frontmatter 解析器**（`com/yimo/storage/FrontmatterCodec.java`）

按 `07-implementation.md` §3 实现。核心是那个正则 + SnakeYAML。

先只实现读，写留到迭代 4。

```java
@Component
public class FrontmatterCodec {

    private static final Pattern FM = Pattern.compile(
        "^---\\s*\\r?\\n(.*?)\\r?\\n---\\s*\\r?\\n?", Pattern.DOTALL);

    private final Yaml yaml = new Yaml();

    public ParsedMarkdown parse(String raw) {
        String text = stripBom(raw);
        Matcher m = FM.matcher(text);
        if (!m.find()) {
            return new ParsedMarkdown(new LinkedHashMap<>(), text);
        }
        Map<String, Object> fm = yaml.load(m.group(1));
        return new ParsedMarkdown(
            fm != null ? new LinkedHashMap<>(fm) : new LinkedHashMap<>(),
            text.substring(m.end()));
    }

    private String stripBom(String s) {
        return s.startsWith("\uFEFF") ? s.substring(1) : s;
    }
}

public record ParsedMarkdown(Map<String, Object> frontmatter, String body) {}
```

**3. 扫描服务**（`com/yimo/service/ScanService.java`）

```java
@Service
public class ScanService {

    private static final Logger log = LoggerFactory.getLogger(ScanService.class);
    private static final int MAX_DEPTH = 8;

    private final ChapterMapper chapterMapper;
    private final FrontmatterCodec codec;

    public ScanResult scan(String libraryId, Path root) {
        List<String> errors = new ArrayList<>();
        int count = 0;

        try (Stream<Path> stream = Files.walk(root, MAX_DEPTH)) {
            List<Path> files = stream
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".md"))
                .toList();

            for (Path file : files) {
                try {
                    if (indexFile(libraryId, root, file)) count++;
                } catch (Exception e) {
                    // 单个文件失败不能中断整个扫描
                    log.warn("跳过文件 {}: {}", file, e.getMessage());
                    errors.add(root.relativize(file).toString());
                }
            }
        } catch (IOException e) {
            throw new BizException(ErrorCode.FILE_READ_FAILED, "扫描失败: " + e.getMessage());
        }

        return new ScanResult(files.size(), count, errors);
    }

    private boolean indexFile(String libraryId, Path root, Path file) throws IOException {
        String raw = Files.readString(file, StandardCharsets.UTF_8);
        ParsedMarkdown pm = codec.parse(raw);

        DocType type = TypeInferrer.infer(file, pm.frontmatter(), root);
        if (type != DocType.CHAPTER) return false;

        Chapter ch = new Chapter();
        ch.setId(existingIdOrNew(libraryId, root, file));   // 保留已有 id
        ch.setLibraryId(libraryId);
        ch.setRelPath(root.relativize(file).toString().replace('\\', '/'));
        ch.setBookName(bookNameOf(root, file));
        ch.setTitle(str(pm.frontmatter().get("title"), fileTitle(file)));
        ch.setVolume(str(pm.frontmatter().get("volume"), ""));
        ch.setSortOrder(parseOrder(file, pm.frontmatter()));
        ch.setStatus(str(pm.frontmatter().get("status"), "draft"));
        ch.setWordCount(countWords(pm.body()));
        ch.setContentHash(HashUtil.sha256Hex(pm.body()));
        ch.setUpdatedAt(LocalDateTime.now());

        upsert(ch);
        return true;
    }
}
```

**4. 类型推断**（`com/yimo/storage/TypeInferrer.java`）

按 `07-implementation.md` §2 的规则实现。关键是**剥掉目录名的编号前缀再匹配**：

```java
private static String stripPrefix(String dirName) {
    return dirName.replaceFirst("^\\d+[-_.\\s]*", "");
}
```

**5. 树接口**

```java
@GetMapping("/{libraryId}/tree")
public TreeResponse tree(@PathVariable String libraryId) {
    return scanService.buildTree(libraryId);
}
```

组装成 `book → volumes → chapters` 三层。

### 前端

用 Naive UI 的 `NTree` 组件，把树数据转成它的 `TreeOption` 格式。

**关键**：树节点要显示字数，有待处理批注的章节显示红色角标。

### 验证清单

| 检查 | 怎么做 | 预期 |
|---|---|---|
| 扫描出全部章节 | 指向一个真实书库 | 章节数和文件管理器里数出来的对得上 |
| 中文标题正确 | 看树上的标题 | 和文件里的 `title` 一致 |
| 卷分组正确 | 看层级 | 同 `volume` 的章节归在一起 |
| 无 frontmatter 的文件也能识别 | 放一个纯 md 到正文目录 | 出现在树上，标题取文件名 |
| 坏文件不中断扫描 | 放一个二进制文件改名成 `.md` | 其他章节照常扫描，该文件进错误清单 |
| BOM 文件正常 | 用记事本存一个带 BOM 的 md | frontmatter 能正确解析，不 BOM 那一步会失败 |

**最后一条最容易忽略。** 用记事本打开任意一个章节文件，加一个空格保存，就会带 BOM。这是真实场景。

---

## 迭代 3：读章节

**目标**：点树上的章节，右边显示内容。

### 后端

```java
@GetMapping("/api/chapters/{id}")
public ChapterDetail get(@PathVariable String id) {
    Chapter ch = chapterMapper.selectById(id);
    if (ch == null) throw new BizException(ErrorCode.CHAPTER_NOT_FOUND);

    Path file = PathGuard.resolve(libraryStorage.rootOf(ch.getLibraryId()), ch.getRelPath());
    String raw = libraryStorage.read(file);
    ParsedMarkdown pm = codec.parse(raw);   // 用磁盘内容，不用数据库缓存

    return new ChapterDetail(
        ch.getId(), ch.getBookName(), ch.getRelPath(),
        ch.getTitle(), ch.getVolume(), ch.getSortOrder(), ch.getStatus(),
        ch.getPov(), List.of(), List.of(), ch.getStoryTime(), null,
        ch.getWordCount(), ch.getContentHash(),
        pm.body(),                              // 正文
        ch.getUpdatedAt(), ch.getUpdatedAt()
    );
}
```

**注意：正文从文件读，不从数据库读。** 文件是真相源。数据库只存索引元数据。

### 前端

先**不做编辑器**，用一个只读的 `<pre>` 显示，验证链路：

```vue
<pre style="white-space: pre-wrap">{{ chapter?.content }}</pre>
```

**先跑通，再加复杂度。** 编辑器是最容易出问题的部分，不要在还没验证数据链路时就去接它。

### 验证清单

| 检查 | 怎么做 | 预期 |
|---|---|---|
| 正文和文件一致 | 打开 `.md` 对照 | 一字不差 |
| frontmatter 没混进正文 | 看显示内容 | 没有 `---` 和字段 |
| 全角空格缩进保留 | 看段落开头 | 两个全角空格的缩进在 |
| 切换章节不串内容 | 快速点几章 | 内容跟着变，不残留 |

---

## 迭代 4：保存正文

****这是整个项目的分水岭。** 做完这一步，"能写、能存、数据不会丢"成立，剩下都是加法。**

### 后端

**1. 原子写入**（`com/yimo/storage/LibraryStorage.java`）

```java
public void writeAtomic(Path target, String content) {
    try {
        Path dir = target.getParent();
        Files.createDirectories(dir);

        // 临时文件必须和目标同目录，否则 ATOMIC_MOVE 会降级成跨分区拷贝
        Path tmp = dir.resolve(target.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);

        Files.move(tmp, target,
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
        throw new BizException(ErrorCode.FILE_WRITE_FAILED, e.getMessage());
    }
}
```

**2. Frontmatter 写回**

按 `07-implementation.md` §3 实现 `serialize`。**关键是用 `LinkedHashMap` 且固定已知字段顺序**。

**3. 保存接口**

```java
@PutMapping("/api/chapters/{id}/content")
public ChapterContentUpdateResponse save(
    @PathVariable String id,
    @RequestBody ChapterContentUpdateRequest req
) {
    return chapterService.saveContent(id, req);
}
```

Service 里实现幂等 + 冲突检测，见 `07-implementation.md` §6。

### 前端

```ts
let timer: number | null = null

function onContentChange(md: string) {
  if (timer) clearTimeout(timer)
  timer = window.setTimeout(() => save(md), 1500)
}

async function save(md: string) {
  try {
    const res = await chapterApi.saveContent(chapterId.value, {
      content: md,
      contentHash: currentHash.value,
    })
    currentHash.value = res.contentHash
    saveState.value = 'saved'
  } catch (e: any) {
    if (e?.error === 'CONTENT_HASH_MISMATCH') {
      // 弹窗让作者选：用我的 / 用文件里的
    } else {
      saveState.value = 'error'
    }
  }
}
```

### 验证清单

| 检查 | 怎么做 | 预期 |
|---|---|---|
| 改动落盘 | 改字 → 等 2 秒 → **用记事本打开磁盘文件** | 改动在文件里 |
| frontmatter 保留 | 保存后打开文件看头部 | 字段都在，顺序没乱 |
| 未知字段保留 | 手动往 md 头部加 `my_field: hello`，然后在亿墨里改一个字保存 | `my_field: hello` 还在 |
| 幂等 | 不改内容连点保存 | 文件 `updatedAt` 不变 |
| 冲突检测 | 用 Typora 改同一个文件 → 回亿墨改字 → 保存 | 弹冲突提示 |
| 中断不损坏 | 保存瞬间强杀进程 | 文件要么是旧内容要么是新内容，不会是半截 |
| 中文不乱码 | 保存后记事本打开 | 中文正常 |

**第 3 条（未知字段保留）是评判前两个里程碑是否合格的核心测试。** 它证明了亿墨没有绑架作者的元数据。

**第 6 条（中断不损坏）** 可以用更暴力的方式测：写一个循环，一边不断保存一边强杀进程，重复 20 次，每次检查文件是否完整。

---

## 迭代 5：新建与删除章节

### 后端

按 `07-implementation.md` §4 实现。重点是**文件名生成时的非法字符处理**：

```java
public String buildFileName(int order, String title) {
    String safe = title
        .replaceAll("[\\\\/:*?\"<>|]", "_")   // Windows 非法字符
        .replaceAll("[.\\s]+$", "")           // 结尾的点和空格
        .trim();

    if (safe.isEmpty()) safe = "未命名";
    if (isReservedName(safe)) safe = "_" + safe;   // CON PRN AUX NUL COM1-9 LPT1-9

    String name = String.format("第%03d章-%s.md", order, safe);

    // Windows 路径长度限制
    if (name.length() > 150) {
        name = name.substring(0, 140) + ".md";
    }
    return name;
}
```

**删除是移到 `.yimo/trash/`**：

```java
public void delete(String chapterId) {
    Chapter ch = require(chapterId);
    Path src = PathGuard.resolve(root(ch.getLibraryId()), ch.getRelPath());
    Path trash = root(ch.getLibraryId())
        .resolve(".yimo/trash")
        .resolve(ch.getRelPath().replace('/', '_') + "." + System.currentTimeMillis());

    Files.createDirectories(trash.getParent());
    Files.move(src, trash, StandardCopyOption.REPLACE_EXISTING);
    chapterMapper.deleteById(chapterId);
}
```

### 验证清单

| 检查 | 怎么做 | 预期 |
|---|---|---|
| 中文标题正常 | 建一个「第三章 泥瓶巷」 | 文件名 `第003章-泥瓶巷.md` |
| 非法字符替换 | 建一个标题带 `/` 和 `:` 的 | 文件名里是 `_`，不报错 |
| 同名检测 | 同一卷建两个同名章 | 第二次提示"已有同名章节" |
| 删到回收站 | 删除一章，去 `.yimo/trash/` 看 | 文件在里面 |
| 删除不误伤 | 删一章后看其他章 | 其他文件都在，内容没变 |
| 文件名不超长 | 建一个超长标题的章 | 文件创建成功，不被截断成非法名 |
| 保留名 | 标题叫「CON」 | 文件名叫 `_CON.md`，创建成功 |

---

## 迭代 6：接入编辑器

**目标**：把只读的 `<pre>` 换成 Tiptap。

### 前端

**1. 基础接入**

```vue
<script setup lang="ts">
import { useEditor, EditorContent } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import { watch } from 'vue'

const props = defineProps<{ modelValue: string }>()
const emit = defineEmits<{ 'update:modelValue': [string] }>()

const editor = useEditor({
  content: props.modelValue,
  extensions: [StarterKit],
  onUpdate: ({ editor }) => {
    emit('update:modelValue', serializeMarkdown(editor.getJSON()))
  },
})
</script>

<template>
  <EditorContent :editor="editor" />
</template>
```

**2. Markdown 序列化**

装 `tiptap-markdown`：

```bash
npm install tiptap-markdown
```

按 `07-implementation.md` §5 配置**节点白名单**，并写往返自检。

**3. 往返自检**

```ts
export function selfCheck(md: string): string[] {
  const doc = parseMarkdown(md)
  const back = serializeMarkdown(doc)
  if (back === md) return []
  return diffLines(md, back)
}
```

在 `requestIdleCallback` 里跑，失败在状态栏警告。

### 验证清单

| 检查 | 怎么做 | 预期 |
|---|---|---|
| 内容显示正确 | 打开一个已有章节 | 和 `<pre>` 显示的一致 |
| 格式保留 | 加粗几个字，保存，用记事本看 | 是 `**粗体**` |
| 往返不丢 | 打开 10 个真实章节，看状态栏 | 没有往返失败警告 |
| 中文输入法正常 | 用拼音输入法打一段字 | 不出乱码、不丢字、光标不跳 |
| 粘贴过滤 | 从网页复制一段带样式的文字粘进来 | 变成纯文本，不带一堆 span |
| 全角空格缩进 | 看段落开头 | 缩进在，视觉正常 |

**中文输入法那条要专门测。** 用搜狗/微软拼音连续输入一大段，中途不要回车，观察有没有丢字或光标跳动。

---

## 迭代 7：规则引擎

**目标**：打开章节，波浪线标出「的/地/得」这类错误。

### 后端

**1. 规则接口**（`src/main/java/com/yimo/rules/`）

```java
public interface Rule {
    String id();
    String description();
    Severity defaultSeverity();
    boolean defaultEnabled();
    List<RuleIssue> check(String text, RuleContext ctx);
}

public record RuleIssue(
    int start, int end,
    String message,
    List<String> suggestions,
    double confidence
) {}
```

**2. 先实现三条最简单、最不会误报的**

```java
@Component
public class EllipsisRule implements Rule {

    private static final Pattern WRONG = Pattern.compile("\\.{3,}|。{3,}");

    @Override public String id() { return "ellipsis"; }
    @Override public String description() { return "省略号应使用……"; }
    @Override public Severity defaultSeverity() { return Severity.WARNING; }
    @Override public boolean defaultEnabled() { return true; }

    @Override
    public List<RuleIssue> check(String text, RuleContext ctx) {
        List<RuleIssue> issues = new ArrayList<>();
        Matcher m = WRONG.matcher(text);
        while (m.find()) {
            issues.add(new RuleIssue(m.start(), m.end(),
                "省略号应使用「……」", List.of("……"), 0.95));
        }
        return issues;
    }
}
```

**3. 引擎**

```java
@Service
public class RuleEngine {
    private final List<Rule> rules;

    public List<Review> check(String chapterId, String text) {
        List<Review> reviews = new ArrayList<>();
        for (Rule rule : rules) {
            if (!isEnabled(rule)) continue;
            for (RuleIssue issue : rule.check(text, ctx)) {
                reviews.add(toReview(chapterId, text, rule, issue));
            }
        }
        return reviews.stream()
            .sorted(Comparator.comparingInt(r -> r.getAnchor().from()))
            .toList();
    }
}
```

### 前端

**1. 批注渲染用 Decoration**（`07-implementation.md` §8）

**2. 点击波浪线弹菜单**：显示 message + 建议 + 接受/忽略。

### 验证清单

| 检查 | 怎么做 | 预期 |
|---|---|---|
| 波浪线出现 | 打一段含 `...` 的文字 | 保存后出现波浪线 |
| 点开有建议 | 点波浪线 | 弹出菜单显示「……」 |
| 接受后改字 | 点接受 | 正文里的 `...` 变成 `……`，波浪线消失 |
| 误报率检查 | 拿 10 万字真实稿件跑一遍 | 人工抽查 200 条，错的不超过 4 条 |
| 位置准确 | 看波浪线位置 | 正好盖住错误文字，不偏移 |

**误报率那条是硬指标。** 超过 2% 就说明规则写得有问题，宁可先关掉那条规则。

---

## 迭代 8：接入 AI 校对

**目标**：点"AI 校对"，等几秒，波浪线陆续出现。

### 后端

**1. 加 Spring AI 依赖**

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
```

**2. 模型工厂**（按 `07-implementation.md` §15，用工厂不用注入 Bean）

**3. 提示词文件**（`src/main/resources/prompts/proofread.md`）

**4. 分批 + 校验 + SSE**（按 `07-implementation.md` §10）

### 验证清单

| 检查 | 怎么做 | 预期 |
|---|---|---|
| 连通性 | 设置页点"测试连接" | 显示延迟，不报错 |
| 校对出结果 | 点校对 | 几秒后波浪线陆续出现 |
| 片段能定位 | 看后端日志 | 没有"丢弃无法定位的片段" |
| 连接池不耗尽 | 校对时并发打其他接口 | 其他接口正常响应 |
| 缓存生效 | 同一章连点两次校对 | 第二次秒回，标注 `cached: true` |
| 额度护栏 | 把每日上限设成 1000 token | 超过后提示已达上限 |
| 报错可读 | 故意填错 API Key | 提示"API Key 无效"不是"连接失败" |

**第 4 条（连接池）** 是验证 §1.2 那个坑有没有踩。校对进行中，用浏览器同时刷新页面，如果卡住不动就是踩了。

---

# 第 6 章 调试手册

## 常见错误速查

| 报错 | 原因 | 解决 |
|---|---|---|
| `Failed to configure a DataSource` | 数据库连不上 | 先 `docker compose ps` 看是不是 `healthy`，再检查用户名密码 |
| `Public Key Retrieval is not allowed` | JDBC URL 缺参数 | 加 `allowPublicKeyRetrieval=true` |
| `Unknown database 'yimo'` | 库没建 | 回 §0.2 重启数据库容器 |
| 中文变 `???` | 字符集不对 | 库里应该是 `utf8mb4` 不是 `utf8` |
| 时间差 8 小时 | 时区参数缺失 | JDBC URL 加 `serverTimezone=Asia/Shanghai` |
| `Invalid bound statement` | Mapper 没扫描到 | 启动类加 `@MapperScan("com.yimo.mapper")` |
| 接口 404 | Controller 包位置不对 | 必须在 `com.yimo` 及其子包下 |
| 前端请求 404 | 代理没配或没重启 | 改 `vite.config.ts` 后必须重启 dev server |
| 中文文件名创建失败 | Windows 非法字符 | 检查 `buildFileName` 的替换逻辑 |
| `AccessDeniedException` | 文件被别的程序占着 | Windows 上 Typora、Word 会锁文件，关掉再试 |

## 怎么调试

**后端看 SQL**：`application.yml` 里配了 `log-impl: StdOutImpl`，每条 SQL 都会打印到控制台。**这是排查数据问题最快的方法**。

**前端看请求**：F12 → Network → 筛 `Fetch/XHR`。看请求体、响应体、状态码。

**断点调试**：IDEA 里在代码行号左边点一下，出现红点，然后 Debug 模式启动。请求打过来会停在那里，可以看所有变量的值。**比 `System.out.println` 高效得多，值得花时间学会。**

**看文件到底写成什么样**：任何时候怀疑文件写入有问题，直接去文件管理器打开那个 `.md` 看。不要只看接口返回。

---

# 第 7 章 提交规范

## 每个迭代结束提交一次

```bash
cd D:/Project/yi-mo
git add .
git commit -m "feat(iteration-1): 书库管理 - 增删查"
```

提交信息格式：

```
<类型>(<范围>): <简短描述>
```

| 类型 | 用于 |
|---|---|
| `feat` | 新功能 |
| `fix` | 修 bug |
| `refactor` | 重构，不改行为 |
| `test` | 只改测试 |
| `docs` | 只改文档 |
| `chore` | 构建、依赖、配置 |

## 提交前检查

```bash
cd backend && mvn test          # 后端测试全过
cd frontend && npm run type-check && npm run lint
```

**测试挂了不要提交。** 一旦开始提交红色的代码，后面就再也回不去了。

## 每个迭代都要手动回归

自动化测试覆盖不了的，列个清单每次手动过一遍：

- [ ] 能添加书库，重复添加被拒
- [ ] 能扫描出章节，中文不乱码
- [ ] 能打开章节，内容正确
- [ ] 能改字保存，**用记事本确认文件真的改了**
- [ ] 未知 frontmatter 字段保存后还在
- [ ] 能新建章节，非法字符被处理
- [ ] 删除的章节在回收站里

这个清单会随着迭代增加。**它比自动化测试更重要**，因为亿墨的核心风险是"静默损坏作者的稿子"，而这类 bug 恰恰是单元测试覆盖不到的。

---

# 常见问题

## 学不动了怎么办

**不要按"学完一个再学下一个"的方式推进。**

反面教材：先花两周看完 Vue 官方文档，再花两周看 Spring Boot 教程，然后发现自己什么都不会做。

正确方式：**每个迭代都做出一个能跑的东西**，哪怕很粗糙。第 3 章做完你就有个能列出书库的页面了；迭代 4 做完你就能真的用它写小说了（虽然很简陋）。

看着浏览器里显示出自己写的数据，是唯一能撑下去的动力。

## 遇到不会的 API 怎么办

**优先查官方文档**，不是搜索引擎。搜索引擎返回的答案经常是几年前版本的。

| 查什么 | 去哪 |
|---|---|
| Vue 3 | https://cn.vuejs.org/ |
| Spring Boot | https://spring.io/projects/spring-boot |
| MyBatis-Plus | https://baomidou.com/ |
| Tiptap | https://tiptap.dev/docs |
| Spring AI | https://docs.spring.io/spring-ai/reference/ |

**问 AI 的时候把版本号说清楚。** "Vue 3.5 + TypeScript 怎么定义 props" 比 "Vue 怎么定义 props" 得到的答案有用得多。

## 卡住了怎么办

**先缩小范围。** 一个"保存失败"的问题，拆成：

1. 请求发出去了吗？→ 看浏览器 Network
2. 后端收到了吗？→ 看后端日志
3. 数据库写了吗？→ 看 SQL 日志
4. 文件写了吗？→ 去文件管理器看

四步一定能定位到问题在哪一层。**大部分人在第 1 步就停了，然后开始瞎改代码。**
