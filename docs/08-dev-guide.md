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
    # 用哪个镜像。8.0 是当前长期支持版本
    image: mysql:8.0
    # 容器名。不指定的话 Docker 会随机生成一个，容器多了分不清谁是谁
    container_name: yimo-mysql
    # 开机自启（除非手动停过）。电脑重启后不用记得手动起数据库
    restart: unless-stopped

    environment:
      # 这五个是 MySQL 镜像约定的环境变量，容器第一次启动时会用它初始化
      MYSQL_ROOT_PASSWORD: root_dev_2026    # 管理员密码，只用来管理，应用不用它
      MYSQL_DATABASE: yimo                  # 自动建好这个库，不用手动 CREATE DATABASE
      MYSQL_USER: yimo                      # 应用连接用的账号
      MYSQL_PASSWORD: yimo_dev_2026
      TZ: Asia/Shanghai                     # 时区。不设的话容器用 UTC，写入的时间差 8 小时

    command:
      # 传给 MySQL 服务的启动参数。
      # 字符集必须显式指定 utf8mb4——MySQL 的「utf8」是阉割版，
      # 存不了 emoji 和部分生僻字，而小说里出现生僻字是常事
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_0900_ai_ci

    ports:
      # 端口映射，格式是「宿主机端口:容器端口」。
      # 3306 被本机的 MySQL 服务占了，所以外面用 3308。
      # 前面写 127.0.0.1 是关键——只监听本机，
      # 同一个 Wi-Fi 下别人扫不到你的数据库
      - "127.0.0.1:3308:3306"

    volumes:
      # 数据存哪。不挂这个卷的话，容器一删数据全没
      - mysql-data:/var/lib/mysql

    healthcheck:
      # 健康检查：每隔 5 秒执行一次 mysqladmin ping。
      # 有它才能用 docker compose ps 看出数据库是不是「真的可以连了」，
      # 而不是只看容器有没有在运行
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-uroot", "-proot_dev_2026"]
      interval: 5s          # 每 5 秒查一次
      timeout: 3s           # 单次超时 3 秒
      retries: 12           # 连续失败 12 次才判定为不健康
      start_period: 30s     # 启动后给 30 秒宽限期，这段时间内失败不算数
                            # （MySQL 第一次启动要初始化数据目录，比较慢）

# 声明上面用到的命名卷。Docker 会把它存在自己的数据目录里，
# 容器删了重建，数据还在
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
# mkdir -p：建目录。-p 的意思是「不存在就建，已存在也不报错」
# ~ 在 Git Bash 里代表当前用户的主目录，即 C:\Users\你的用户名
# .m2 是 Maven 约定的配置目录，它会自动去这里找 settings.xml
mkdir -p ~/.m2

# 从 << 'EOF' 到 EOF 之间的内容，原样写进 ~/.m2/settings.xml
# 给 EOF 加单引号是必须的——不加的话 shell 会去解析内容里的 $ 符号，
# 把 XML 里的变量当环境变量替换掉
cat > ~/.m2/settings.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
  <mirrors>
    <mirror>
      <!-- 给这个镜像起个名字，随便写，出错时日志里能看到 -->
      <id>aliyun</id>
      <!-- 替换哪个仓库。central 是 Maven 中央仓库，所有公开依赖都从它下载 -->
      <mirrorOf>central</mirrorOf>
      <name>Aliyun Maven</name>
      <!-- 阿里云的镜像地址，内容和中央仓库一致，但从国内访问快得多 -->
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
<!--
  Maven 坐标由三部分组成：
    groupId     组织名，相当于 Java 包名的倒写
    artifactId  项目名
    version     版本号
  三者合起来唯一确定一个依赖。
-->

<!-- 数据库访问。省掉写 SQL 的样板代码 -->
<dependency>
    <groupId>com.baomidou</groupId>
    <!-- 注意是 spring-boot4-starter 不是 spring-boot3-starter。
         Spring Boot 4 换了坐标，用错了会启动失败 -->
    <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
    <version>3.5.17</version>
</dependency>

<!-- 中文分词与自然语言处理。规则引擎里判断「他/她混用」要用到分词 -->
<dependency>
    <groupId>com.hankcs</groupId>
    <artifactId>hanlp</artifactId>
    <!-- portable 版自带数据包，约 30MB，不用另外下载模型 -->
    <version>portable-1.8.6</version>
</dependency>

<!-- 工具集：文件操作、字符串处理、哈希计算等。
     有了它就不用自己写一堆 StringUtils、FileUtils -->
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
# ===== Web 服务器 =====
server:
  # 只监听本机。亿墨没有账号体系，绑到 0.0.0.0 的话，
  # 同一个 Wi-Fi 下任何人都能读写你的稿件。这条不是可选项
  address: 127.0.0.1
  # 用 18080 而不是 8080：本机 8080 已经被别的服务占了。
  # 端口只在启动时用一次（随后自动打开浏览器），所以不用好记
  port: 18080

spring:
  application:
    name: yimo

  # ===== 数据库连接 =====
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver

    # JDBC URL 里每个参数都有原因，详见下面的表格：
    #   3308           docker-compose.yml 里映射出来的端口
    #   UTF-8          中文不乱码（不能写 utf8mb4，驱动不认）
    #   Asia/Shanghai  时区，不加 datetime 字段会差 8 小时
    #   allowPublicKeyRetrieval  MySQL 8 的认证方式需要
    #   useSSL=false   本地连接不需要加密，开着反而慢
    url: jdbc:mysql://127.0.0.1:3308/yimo?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
    username: yimo
    password: yimo_dev_2026

    # 连接池。数据库连接是稀缺资源，池子负责复用
    hikari:
      maximum-pool-size: 10    # 最多同时 10 个连接
      minimum-idle: 2          # 至少保持 2 个空闲连接，来了请求不用现建

# ===== MyBatis-Plus =====
mybatis-plus:
  configuration:
    # 数据库字段是下划线风格（last_opened），Java 字段是驼峰（lastOpened）。
    # 打开这个开关，框架自动帮你转换，不用在每个字段上写 @TableField
    map-underscore-to-camel-case: true
    # 把执行的 SQL 打印到控制台。排查数据问题最快的方法，生产环境要去掉
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl

# ===== 日志 =====
logging:
  level:
    # 自己写的包用 DEBUG 级别，方便看细节
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

import com.yimo.common.Ids;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查接口。
 *
 * <p>用途是验证「服务起没起来」和「客户端能不能连上」，
 * 不涉及任何业务逻辑，所以它也是最小、最好写的一个接口。
 */
@RestController                    // 这个类是处理 HTTP 请求的，返回值自动转 JSON
@RequestMapping("/api/ping")       // 类里所有接口的 URL 都以这个开头
public class PingController {

    /**
     * GET /api/ping
     *
     * <p>返回 Map 而不是定义 DTO，是因为这里只有三个字段、
     * 只在开发期用，定义一个类反而啰嗦。
     */
    @GetMapping
    public Map<String, Object> ping() {
        // 用 LinkedHashMap 而不是 Map.of()：
        // Map.of 的遍历顺序是不保证的，返回的 JSON 字段顺序每次都可能不同，
        // 调试时看着别扭。LinkedHashMap 保持插入顺序
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("service", "yimo");
        // 带时区的时间。用 LocalDateTime 的话前端不知道这是哪个时区的时间
        result.put("time", OffsetDateTime.now().toString());
        // 顺便生成一个 ULID 返回，用来验证 ID 生成器工作正常
        result.put("sampleId", Ids.chapter());
        return result;
    }
}
```

**`@RestController` 和 `@Controller` 的区别**：前者相当于 `@Controller` + `@ResponseBody`，方法返回值会被转成 JSON 直接写进响应体。用 `@Controller` 的话，返回值会被当成「视图名」去找 HTML 模板——那是服务端渲染的用法，我们的前后端分离项目用不上。

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
-- 切到 yimo 库。后面的操作都在这个库里进行
USE yimo;

CREATE TABLE library (
  -- CHAR(26) 而不是 VARCHAR(26)：定长类型在 MySQL 里存得更紧凑，
  -- 而且能提前挡住「不小心存了长度不对的 id」这种事。
  -- 26 是 ULID 本身的长度，不含 lib_ 前缀
  id           CHAR(26)     NOT NULL PRIMARY KEY COMMENT 'ULID，不含前缀',

  -- VARCHAR(100) 对书库名足够。这里必须给长度，MySQL 不允许 VARCHAR 不写长度
  name         VARCHAR(100) NOT NULL,

  -- VARCHAR(500)：Windows 的路径上限是 260 字符，
  -- 留到 500 是为了兼容更长的路径和以后可能的调整
  path         VARCHAR(500) NOT NULL,

  -- 允许为空：新添加的书库还没被打开过。
  -- 列表按这个字段倒序排，NULL 会排在最后
  last_opened  DATETIME     NULL,

  -- 不填时自动写入当前时间，不用应用层管
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

  -- 唯一索引：同一个路径不能添加两次。
  -- 有了它，即使应用层的查重逻辑被绕过（比如并发请求），
  -- 数据库这一层也会兜住
  UNIQUE KEY uk_path (path)
) ENGINE=InnoDB                       -- InnoDB 支持事务和行级锁，是 MySQL 8 的默认引擎
  DEFAULT CHARSET=utf8mb4             -- 字符集必须是 utf8mb4，见前面的说明
  COLLATE=utf8mb4_0900_ai_ci;         -- 排序规则：不区分大小写和重音
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
<!-- 这是 Spring Boot 官方的打包插件，负责把项目打成可执行的 fat jar -->
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>

    <configuration>
        <!-- 打包时排除下列依赖 -->
        <excludes>
            <!--
              Lombok 只在编译期工作（帮你生成 getter/setter 的字节码），
              运行期完全不需要它。

              不排除的话，打出的 jar 里会多一个几百 KB 的 lombok.jar，
              而且可能和其他版本的 Lombok 冲突。
            -->
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

/**
 * 书库的数据访问接口。
 *
 * <p>注意这是个 interface 而不是 class——你不需要写实现。
 * MyBatis 运行时会用动态代理生成一个实现类，
 * 里面每个方法的 SQL 都由 BaseMapper 定义。
 */
@Mapper     // 告诉 MyBatis「这个接口要生成实现」，不加的话注入时会找不到
public interface LibraryMapper extends BaseMapper<Library> {
    // 这里什么都不用写。
    //
    // 继承 BaseMapper<Library> 之后自动拥有：
    //   insert(entity)          插入
    //   deleteById(id)          按主键删除
    //   updateById(entity)      按主键更新
    //   selectById(id)          按主键查询
    //   selectList(wrapper)     条件查询，wrapper 传 null 表示查全部
    //   selectCount(wrapper)    条件计数
    //   insertOrUpdate(entity)  有主键就更新，没有就插入
    //   ...还有十几个
    //
    // 需要复杂查询时，在接口里加方法并配 @Select 注解或 XML，
    // 但我们的场景用不到——绝大多数操作 BaseMapper 都覆盖了
}
```

**继承 `BaseMapper` 就自带增删改查**，不用写一行 XML。这是 MyBatis-Plus 的核心价值。

记得在启动类上加 `@MapperScan`：

```java
package com.yimo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用入口。
 */
@SpringBootApplication
// 扫描 com.yimo.mapper 包下所有带 @Mapper 的接口并为它们生成实现。
// 不写这个的话，每个 Mapper 接口都要单独加 @Mapper 注解——
// 项目大了容易漏，不如统一在这里声明一次
@MapperScan("com.yimo.mapper")
public class YimoApplication {

    public static void main(String[] args) {
        // run() 内部做了三件事：
        //   1. 创建 Spring 容器
        //   2. 扫描当前包及子包，把所有 @Component / @Service / @RestController
        //      标记的类实例化并装配好依赖
        //   3. 启动内嵌的 Tomcat，开始监听端口
        SpringApplication.run(YimoApplication.class, args);
    }
}
```

**这个类后面还会改。** 做到「文件夹选择窗口」那一步时，要在 `run()` 之前加一行 `app.setHeadless(false)`，否则弹不出系统对话框。现在先不用管。

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

/**
 * 书库接口。
 *
 * <p>这一版先做成最简单的形式：Controller 直接调 Mapper，
 * 不经过 Service。等业务规则变多了（校验、查重、报错），
 * 再在迭代 1 里把它拆成三层。
 */
@RestController
@RequestMapping("/api/libraries")
public class LibraryController {

    // final 表示「构造完就不能改」。
    // Spring 创建这个对象时，会从容器里找 LibraryMapper 的实现塞进来
    private final LibraryMapper libraryMapper;

    /**
     * 构造函数。
     *
     * <p>Spring 见到「只有一个构造函数」的类，会自动用它来注入依赖，
     * 不需要加 @Autowired 注解。
     *
     * <p>也可以用「字段注入」——在字段上加 @Autowired 让 Spring 直接赋值。
     * 但那种写法有两个坏处：字段不能是 final（可能被意外改动），
     * 以及缺依赖时要等运行到那一行才报错，而不是启动就报错。
     */
    public LibraryController(LibraryMapper libraryMapper) {
        this.libraryMapper = libraryMapper;
    }

    /**
     * GET /api/libraries
     *
     * <p>selectList(null) 里的 null 表示「没有查询条件」，即查全部。
     * 需要条件时传一个 QueryWrapper。
     */
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
npm_config_yes=true npm create vue@latest frontend -- --ts --router --pinia --vitest --eslint --prettier --force
```

**为什么用这一长串参数**：不加参数的话，`create-vue` 会逐个问你八个问题，每次都要回车，还容易选错。直接把选项写在命令里，一次到位。

拆开看每个参数：

| 参数 | 作用 |
|---|---|
| `--ts` | 用 TypeScript |
| `--router` | 装 Vue Router（页面路由） |
| `--pinia` | 装 Pinia（状态管理） |
| `--vitest` | 装 Vitest（单元测试） |
| `--eslint` | 代码检查 |
| `--prettier` | 代码格式化 |
| `--force` | 目录已存在时直接覆盖，不追问 |
| `npm_config_yes=true` | 自动确认 npm 下载 create-vue 这个包 |

**没选的两个**：JSX（Vue 里用不上）、端到端测试（后面需要时再单独装 Playwright）。

**预期输出**（结尾）：

```
┌  Vue.js - The Progressive JavaScript Framework
│
│  正在初始化项目 D:\Project\yi-mo\frontend...
│
└  项目初始化完成
```

然后装依赖并启动：

```bash
cd frontend
npm install
npm run dev
```

**预期输出**：

```
  VITE v8.x.x  ready in 859 ms

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

**Tailwind 4 的接入方式和 3 完全不一样，网上搜到的教程大多是 3 的，照抄会踩坑。**

Tailwind 4 只需要两步：

**第 1 步**：`frontend/src/assets/main.css` 第一行加

```css
@import 'tailwindcss';
```

**第 2 步**：`vite.config.ts` 加插件（见下一节）。

Tailwind 3 要写 `tailwind.config.js`、要加三个 `@tailwind` 指令、要配 PostCSS——**4 全都不要了**。如果你搜到的教程让你建 `tailwind.config.js`，那是 3 的教程，跳过。

## 3.3 配置代理

开发时前端跑在 5173，后端跑在 18080。浏览器直接请求 18080 会撞上跨域限制。**在 Vite 里配代理绕过去**。

编辑 `frontend/vite.config.ts`：

```ts
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [vue(), vueDevTools(), tailwindcss()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    proxy: {
      // 开发时把 /api 转发到后端，浏览器看到的是同源请求，没有跨域问题。
      // 生产环境前端产物打进 jar，前后端本来就同源，这段配置不影响生产。
      //
      // 前端代码里所有请求都写相对路径 '/api/xxx'，
      // 绝对不要写死 http://127.0.0.1:18080 —— 那会导致生产环境全部 404
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

### `src/api/http.ts`

```ts
import axios from 'axios'

/**
 * 统一的 HTTP 客户端。
 *
 * baseURL 是相对路径 '/api'，开发时由 Vite 代理转发到后端，
 * 生产时前后端同源。不要在这里写死完整域名。
 */
export const http = axios.create({
  // 所有请求的公共前缀。写相对路径 '/api' 而不是完整域名，
  // 是因为开发时 Vite 会代理到后端、生产时前后端同源——
  // 两种情况都不需要改代码
  baseURL: '/api',
  // 30 秒超时。请求超过这个时间自动失败，避免界面一直转圈。
  // 注意后面有个接口要单独改这个值（文件夹选择窗口，见 3.5 节）
  timeout: 30000,
})

/** 后端返回的统一错误体，格式见 docs/06-api.md */
export interface ApiError {
  error: string
  message: string
  details?: Record<string, unknown>
  timestamp?: string
}

/**
 * 把 catch 到的未知错误转成 ApiError。
 *
 * 为什么需要这个函数：
 * - TypeScript 4.4+ 在 strict 模式下，catch 变量的类型是 unknown，不能直接当 ApiError 用
 * - 写 `catch (e: any)` 会被 ESLint 的 @typescript-eslint/no-explicit-any 拦下
 *
 * 所以统一在这里做一次安全的类型收窄，业务代码里 `catch (e)` 之后调 toApiError(e) 即可。
 */
export function toApiError(e: unknown): ApiError {
  if (e && typeof e === 'object' && 'error' in e && 'message' in e) {
    return e as ApiError
  }
  return {
    error: 'UNKNOWN_ERROR',
    message: e instanceof Error ? e.message : String(e),
  }
}

http.interceptors.response.use(
  // 成功时直接返回数据体，调用方不用每次 .data.data
  (res) => res.data,
  (err) => {
    const body = err.response?.data as ApiError | undefined
    const apiError: ApiError = body ?? {
      error: 'NETWORK_ERROR',
      message: '无法连接后端服务，请确认它正在运行',
    }
    console.error('[API]', apiError.error, apiError.message)
    return Promise.reject(apiError)
  },
)
```

**拦截器做了两件事**：

- 成功时把 `res.data` 拆出来直接返回，所以 `http.get(...)` 拿到的就是数据本身，不用写 `.data.data`
- 失败时统一包装成 `ApiError`，调用方只需要 `catch (e) { e.message }`

### `src/api/ping.ts`

```ts
import { http } from './http'

export interface PingResult {
  ok: boolean
  service: string
  time: string
  sampleId: string
}

export const pingApi = {
  /** 健康检查。返回的 sampleId 可以顺便验证 ULID 生成是否正常 */
  ping: () => http.get<unknown, PingResult>('/ping'),
}
```

**泛型为什么是两个**：axios 的签名是 `get<T, R>()`，`T` 是响应体类型，`R` 是返回值类型。因为拦截器改了返回值，我们真正拿到的是 `R`，所以第二个泛型才关键。第一个写 `unknown` 表示不关心原始响应体类型。

### `src/api/library.ts`

```ts
import { http } from './http'

/**
 * 书库。
 *
 * 字段名必须和后端返回的 JSON 完全一致——
 * 后端返回 lastOpened，这里写成 lastOpenedAt 就会拿到 undefined。
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

export const libraryApi = {
  /** 路径写 '/libraries' 而不是 '/api/libraries'——baseURL 已经带了 /api */
  list: () => http.get<unknown, Library[]>('/libraries'),

  create: (data: { path: string; name?: string }) =>
    http.post<unknown, Library>('/libraries', data),

  remove: (id: string) =>
    http.delete<unknown, { id: string; removed: boolean; filesDeleted: boolean }>(
      `/libraries/${id}`,
    ),
}
```

三个方法对应后端的三个接口。`list` 现在就能用（后端已有），`create` 和 `remove` 等做到迭代 1 时再写后端。

## 3.5 第一个页面

### 第 1 步：清掉脚手架的示例代码

```bash
cd D:/Project/yi-mo/frontend
rm -rf src/components src/views/AboutView.vue src/assets/base.css src/assets/logo.svg src/stores/counter.ts
```

留下的结构：

```
src/
├── api/
│   ├── http.ts          HTTP 客户端封装
│   └── library.ts       书库相关接口
├── assets/main.css
├── router/index.ts
├── views/HomeView.vue   首页
├── App.vue
└── main.ts
```

### 第 2 步：改页面标题

脚手架生成的是 `<title>Vite App</title>`，浏览器标签页上显示这个很奇怪。

编辑 `frontend/index.html`：

```html
<!DOCTYPE html>
<!-- lang="zh-CN" 告诉浏览器这是中文页面，
     影响字体选择和自动断行规则。写 "en" 的话中文的断行会按英文规则处理 -->
<html lang="zh-CN">
  <head>
    <!-- 声明文件编码。必须是 head 里的第一行，
         放在后面浏览器可能已经用错编码解析过前面的内容了 -->
    <meta charset="UTF-8">

    <link rel="icon" href="/favicon.ico">

    <!-- 移动端适配。width=device-width 让页面宽度等于设备宽度，
         不加的话手机浏览器会按 980px 渲染再缩小，字小得看不清 -->
    <meta name="viewport" content="width=device-width, initial-scale=1.0">

    <title>亿墨 YI-MO</title>
  </head>
  <body>
    <!-- Vue 应用挂载到这里。main.ts 里的 app.mount('#app') 就是找这个 id -->
    <div id="app"></div>

    <!-- type="module" 让浏览器按 ES 模块方式加载。
         Vite 开发时会把 /src/main.ts 转译成浏览器能懂的代码 -->
    <script type="module" src="/src/main.ts"></script>
  </body>
</html>
```

改了两处：`lang="en"` → `lang="zh-CN"`（告诉浏览器这是中文页面，影响字体渲染和断行规则），标题改成「亿墨 YI-MO」。

### 第 3 步：`App.vue` 只留一个路由出口

```vue
<script setup lang="ts">
import { RouterView } from 'vue-router'
</script>

<template>
  <RouterView />
</template>
```

**为什么要这样拆**：`App.vue` 是整个应用的根容器，管的是整体布局（将来的左侧项目树、顶部工具栏都放这）。具体页面放在 `views/` 下，由路由决定显示哪个。混在一起的话，加第二个页面时就要大改。

### 第 4 步：路由指向首页

编辑 `frontend/src/router/index.ts`：

```ts
import { createRouter, createWebHistory } from 'vue-router'
// 首页直接 import：它是首屏就要显示的，没必要延迟加载
import HomeView from '@/views/HomeView.vue'

const router = createRouter({
  // history 模式：URL 长这样 /libraries，不带 # 号。
  // 另一种写法 createWebHashHistory() 会得到 /#/libraries，
  // 兼容性好但难看。前后端同源的项目用 history 模式更自然
  history: createWebHistory(import.meta.env.BASE_URL),

  routes: [
    {
      path: '/',              // URL 路径
      name: 'home',           // 路由名，代码里可写 router.push({ name: 'home' })
      component: HomeView,    // 对应哪个组件
    },
  ],
})

// 导出给 main.ts 用（app.use(router)）
export default router
```

### 第 5 步：写首页

`frontend/src/views/HomeView.vue`：

```vue
<script setup lang="ts">
// lang="ts" 表示这个组件用 TypeScript 写。
// 不加的话是普通 JavaScript，类型检查就没了
import { ref, onMounted } from 'vue'
import { pingApi, type PingResult } from '@/api/ping'
import { toApiError, type ApiError } from '@/api/http'

/*
 * ref 是 Vue 的响应式容器。
 *
 * 它的作用是：当 .value 被改变时，模板里用到它的地方会自动重新渲染。
 * 如果直接用普通变量 `let ping = null`，改了值页面不会更新——
 * 因为 JavaScript 不知道你改了它。
 *
 * 注意在 <script> 里访问要写 .value，在 <template> 里不用写（Vue 自动拆包）：
 *   script:  ping.value = xxx
 *   template: {{ ping }}
 */
const ping = ref<PingResult | null>(null)   // 接口返回的数据
const error = ref<ApiError | null>(null)    // 出错信息
const loading = ref(true)                   // 是否正在加载

async function check() {
  loading.value = true
  error.value = null      // 重试前先清掉上次的错误

  try {
    ping.value = await pingApi.ping()
  } catch (e) {
    // 不要写 catch (e: any)，也不要写 e as ApiError——
    // 前者违反 ESLint 的 no-explicit-any，后者是硬断言。
    // 统一用 toApiError 做类型收窄
    error.value = toApiError(e)
  } finally {
    // finally 里的代码无论成功失败都会执行。
    // 放在这里保证「加载中」的状态一定会被关掉，
    // 不会因为异常导致界面永远转圈
    loading.value = false
  }
}

// onMounted 是生命周期钩子：组件挂载到页面上之后执行。
// 数据请求放在这里，不用写在 setup 的顶层——
// 那样会在组件还没渲染时就发请求
onMounted(check)
</script>

<template>
  <div class="min-h-screen bg-neutral-50 flex items-center justify-center p-8">
    <div class="w-full max-w-lg">
      <h1 class="text-2xl font-semibold text-neutral-800">亿墨 YI-MO</h1>
      <p class="text-sm text-neutral-500 mt-1 mb-6">
        本地优先、零注册、Agent 增强的小说创作工作台
      </p>

      <!--
        v-if / v-else-if 是条件渲染：条件为真才渲染这个元素。
        三个分支互斥，同一时刻只会显示一个。

        不要用 v-show 替代——v-show 是「渲染出来但用 CSS 隐藏」，
        对这里没差别，但 v-if 语义更准确。
      -->

      <!-- 加载中 -->
      <div v-if="loading" class="text-sm text-neutral-500">正在连接后端…</div>

      <!-- 连接失败 -->
      <div v-else-if="error" class="rounded-lg border border-red-200 bg-red-50 p-4">
        <p class="text-sm font-medium text-red-800">后端连接失败</p>
        <!-- {{ }} 是插值语法，把表达式的值显示出来。会自动做 HTML 转义，防 XSS -->
        <p class="text-xs text-red-600 font-mono mt-1">{{ error.error }}</p>
        <p class="text-xs text-red-600 mt-1">{{ error.message }}</p>
        <p class="text-xs text-red-500 mt-3 leading-relaxed">
          确认后端已启动：<br />
          <code class="bg-red-100 px-1 rounded">cd backend &amp;&amp; mvn spring-boot:run</code>
        </p>
        <button
          class="mt-3 text-xs px-3 py-1.5 rounded border border-red-300 text-red-700 hover:bg-red-100"
          @click="check"
        >
          重试
        </button>
      </div>

      <!-- 连接成功 -->
      <div v-else-if="ping" class="rounded-lg border border-green-200 bg-green-50 p-4">
        <p class="text-sm font-medium text-green-800 mb-3">前后端已连通</p>
        <dl class="text-xs text-green-700 font-mono space-y-1.5">
          <div class="flex gap-3">
            <dt class="w-20 text-green-600 shrink-0">service</dt>
            <dd>{{ ping.service }}</dd>
          </div>
          <div class="flex gap-3">
            <dt class="w-20 text-green-600 shrink-0">server time</dt>
            <dd>{{ ping.time }}</dd>
          </div>
          <div class="flex gap-3">
            <dt class="w-20 text-green-600 shrink-0">sample id</dt>
            <dd>{{ ping.sampleId }}</dd>
          </div>
        </dl>
        <p class="text-xs text-green-600 mt-3 leading-relaxed">
          sample id 是后端生成的 ULID（26 字符 + 类型前缀）。<br />
          如果它看起来正常，说明 ID 生成、JSON 序列化、跨域代理都通了。
        </p>
      </div>

      <p class="text-xs text-neutral-400 mt-8">
        下一步：迭代 1 书库管理 —— 添加一个文件夹，让它出现在列表里。
      </p>
    </div>
  </div>
</template>
```

这个页面用 Tailwind 的类名排版，顺带验证了 Tailwind 接入是否成功——如果样式没生效，说明 §3.2 的 `@import 'tailwindcss'` 或 §3.3 的插件没配好。

**这一步的目的是验证链路，不是做界面。** 等迭代 1 做书库管理时，这个页面会被真正的界面替换掉。

**验证**：刷新浏览器，看到绿色的「前后端已连通」和三个字段。

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

/**
 * 所有业务错误码。
 *
 * <p>为什么把「错误码」和「HTTP 状态码」绑在一起：
 * 前端拿到 404 只知道「资源不存在」，但不知道是哪个资源。
 * 带上错误码 LIBRARY_NOT_FOUND，前端就能针对性地处理——
 * 比如书库不存在时跳回列表页，章节不存在时只提示一下。
 *
 * <p>为什么用 enum 而不是一堆常量字符串：
 * 编译器会检查拼写。写错 LIBRARY_NOT_FOUN 会直接编译不过，
 * 而字符串常量 "LIBRARY_NOT_FOUN" 会静静地跑到线上。
 */
public enum ErrorCode {

    // ===== 书库 =====
    // 格式：错误码(HTTP状态码, 给用户看的中文说明)
    LIBRARY_NOT_FOUND(HttpStatus.NOT_FOUND, "书库不存在"),
    LIBRARY_PATH_INVALID(HttpStatus.BAD_REQUEST, "书库路径无效或不可读"),
    LIBRARY_PATH_DUPLICATE(HttpStatus.CONFLICT, "该路径已添加过"),

    // ===== 章节 =====
    CHAPTER_NOT_FOUND(HttpStatus.NOT_FOUND, "章节不存在"),
    CHAPTER_TITLE_DUPLICATE(HttpStatus.CONFLICT, "同目录下已有同名章节"),

    // ===== 文件 =====
    PATH_OUT_OF_BOUNDS(HttpStatus.FORBIDDEN, "路径越界"),
    FILE_READ_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "文件读取失败"),
    FILE_WRITE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "文件写入失败"),
    CONTENT_HASH_MISMATCH(HttpStatus.CONFLICT, "文件已被外部修改"),

    // ===== AI =====
    MODEL_NOT_CONFIGURED(HttpStatus.BAD_REQUEST, "尚未配置模型"),
    AI_CALL_FAILED(HttpStatus.BAD_GATEWAY, "模型调用失败"),
    AI_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "已达每日用量上限"),
    ;

    // enum 的字段也是 final 的，在构造时赋值
    private final HttpStatus status;
    private final String message;

    // enum 的构造函数只能是 private（写不写 private 都一样），
    // 因为枚举值在编译期就固定了，不允许运行时 new 新的出来
    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    // 只提供读取方法，不提供 setter——枚举值本身就应该不可变
    public HttpStatus status() { return status; }
    public String message() { return message; }
}
```

新建 `com/yimo/common/BizException.java`：

```java
package com.yimo.common;

import java.util.Map;

/**
 * 业务异常。
 *
 * <p>业务代码里遇到「不该继续走下去」的情况时抛这个，
 * 由 GlobalExceptionHandler 统一翻译成 HTTP 响应。
 *
 * <p>为什么继承 RuntimeException 而不是 Exception：
 * 受检异常（Exception）会逼着每个调用方写 try-catch 或往上抛，
 * 代码里到处都是 `throws`。而业务异常本来就该由全局处理器统一处理，
 * 让它一路往上冒就行。
 */
public class BizException extends RuntimeException {

    private final ErrorCode code;

    /** 补充信息，比如「是哪个路径出的问题」。可以是空的 */
    private final Map<String, Object> details;

    /** 最简用法：只需一个错误码，消息用错误码自带的 */
    public BizException(ErrorCode code) {
        this(code, code.message(), Map.of());
    }

    /** 需要更具体的说明时用这个，比如把出错的路径拼进消息里 */
    public BizException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    /**
     * 完整用法。
     *
     * @param code    错误码，决定 HTTP 状态码
     * @param message 给用户看的说明
     * @param details 结构化补充信息，会原样返回给前端
     */
    public BizException(ErrorCode code, String message, Map<String, Object> details) {
        super(message);     // 传给父类，这样 e.getMessage() 能拿到
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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常处理。
 *
 * <p>作用：把代码里抛出的异常统一翻译成 HTTP 响应，
 * 而不是让 Spring 默认的错误页（一大坨 HTML）返回给前端。
 *
 * <p>统一后的格式见 docs/06-api.md：
 * { "error": "错误码", "message": "说明", "details": {}, "timestamp": "..." }
 */
@RestControllerAdvice
// @RestControllerAdvice = @ControllerAdvice + @ResponseBody。
// 前者让这个类的方法作用于所有 Controller，
// 后者让返回值直接转成 JSON
public class GlobalExceptionHandler {

    // 日志对象。用 SLF4J 的接口而不是直接依赖 Logback，
    // 这样以后换日志实现不用改代码
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理我们自己的业务异常。
     *
     * <p>用 warn 级别而不是 error——业务异常是「预期内的失败」
     * （比如用户填了个不存在的路径），不是系统故障。
     * 用 error 的话日志里会混进一堆噪音，真正的故障反而被淹没。
     */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Map<String, Object>> handleBiz(BizException e) {
        log.warn("业务异常: {} - {}", e.code().name(), e.getMessage());

        return ResponseEntity
                .status(e.code().status())      // HTTP 状态码由错误码决定
                .body(body(e.code().name(), e.getMessage(), e.details()));
    }

    /**
     * 兜底：所有上面没接住的异常都到这里。
     *
     * <p>这里必须用 error 级别并打印完整堆栈——
     * 走到这里说明有代码没考虑到的 bug，堆栈是唯一的线索。
     *
     * <p>返回给前端的只有一句「服务内部错误」，
     * 具体的异常信息不外泄（可能包含文件路径、SQL 等敏感内容）。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception e) {
        log.error("未预期异常", e);

        return ResponseEntity
                .status(ErrorCode.INTERNAL_ERROR.status())
                .body(body(ErrorCode.INTERNAL_ERROR.name(),
                           ErrorCode.INTERNAL_ERROR.message(),
                           Map.of()));
    }

    /** 组装统一格式的响应体 */
    private Map<String, Object> body(String error, String message, Map<String, Object> details) {
        // 用 LinkedHashMap 而不是 Map.of：
        // 后者不保证遍历顺序，返回的 JSON 字段顺序会变，调试时看着难受
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", error);
        m.put("message", message);
        m.put("details", details);
        m.put("timestamp", OffsetDateTime.now().toString());
        return m;
    }
}
```

**为什么需要 `details` 这个字段**：有些错误前端要拿到额外信息才能正确处理。比如「文件已被外部修改」，前端需要拿到磁盘上的最新内容来做对比弹窗——那些内容就放在 `details` 里。

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

`02-library-format.md` 要求 ID 是 26 字符 ULID，带类型前缀（`lib_`、`ch_` 等）。

#### 先说清楚 ULID 是什么

**ULID** = Universally Unique Lexicographically Sortable Identifier，唯一且可按字典序排序的标识符。

它把两个需求合到了一起：

**需求一：唯一。** 不用数据库自增，也不用中心服务器分配号段——本地生成就不会撞。

**需求二：能按时间排序。** 数据库的主键如果是随机的（比如 UUID），插入时索引会频繁重排，数据量大了很慢。ULID 的开头是时间戳，**越晚生成的 ULID 字典序越大**，插入时永远是往后追加。

结构（26 个字符）：

```
01M2SHDZE8Y8MAZW13WSSP60WC
└────┬────┘└──────┬──────┘
  48位时间戳     80位随机数
 （毫秒级）    （同一毫秒内不会重复）
```

前 10 个字符是时间戳的 Crockford Base32 编码，后 16 个是随机数。

**Crockford Base32** 是一种编码方案，用 32 个字符（`0-9` 和去掉 `I L O U` 的字母）表示二进制数据。去掉那几个字母是因为它们容易和 `1 0` 混淆——这个 ID 可能被人念出来或手抄。

#### 为什么自己写而不找现成的

Hutool 5.8.x **没有** ULID 类（6.x 才加）。其他能用的库要么版本不匹配，要么引入一个只为一个功能的大依赖。

而这个算法本身只有四十行，自己写更好控制。

新建 `backend/src/main/java/com/yimo/common/Ids.java`：

```java
package com.yimo.common;

import java.math.BigInteger;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ULID 生成器。
 *
 * <p>26 个字符，字典序等于时间序，可以直接用作数据库主键。
 * 不带前缀时是纯 ULID；带上前缀（如 {@code ch_}）便于人眼辨认类型。
 */
public final class Ids {

    /** Crockford Base32 的字符表。去掉了 I、L、O、U，它们容易和 1、0 混淆 */
    private static final char[] CROCKFORD =
            "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    /** 编码用的基数。32 个字符，所以是 2 的 5 次方——每次处理 5 位 */
    private static final BigInteger BASE = BigInteger.valueOf(32);

    /** ULID 的长度固定 26 个字符 */
    private static final int LENGTH = 26;

    /**
     * 私有构造函数。
     *
     * <p>这是个工具类，所有方法都是静态的，不需要创建实例。
     * 加私有构造函数是为了防止别人 new 它——IDE 会直接报错。
     */
    private Ids() {
    }

    /** 生成带前缀的 ID，比如 ch_01M2SHDZE8Y8MAZW13WSSP60WC */
    public static String generate(String prefix) {
        return prefix + ulid();
    }

    /**
     * 生成一个纯 ULID（26 字符，无前缀）。
     */
    public static String ulid() {
        long time = System.currentTimeMillis();

        // 准备 16 字节（128 位）的数据：前 6 字节时间戳 + 后 10 字节随机数
        byte[] data = new byte[16];

        // 把 48 位的时间戳拆成 6 个字节。
        // >>> 是无符号右移，从高位开始每 8 位取一个字节
        for (int i = 0; i < 6; i++) {
            data[i] = (byte) (time >>> (8 * (5 - i)));
        }

        // 后 10 字节填随机数，保证同一毫秒内生成的 ID 也不重复。
        // ThreadLocalRandom 比 new Random() 快，且多线程下没有竞争
        byte[] random = new byte[10];
        ThreadLocalRandom.current().nextBytes(random);
        System.arraycopy(random, 0, data, 6, 10);

        return encode(data);
    }

    /**
     * 把 16 字节编码成 26 个字符。
     *
     * <p>128 位除以每字符 5 位 = 25.6，向上取整就是 26 个字符。
     * 多出来的 2 位补零，所以第 1 个字符的取值范围比后面小。
     */
    private static String encode(byte[] data) {
        // BigInteger(1, data) 里的 1 表示「按无符号数解释」。
        // 不传的话，最高位是 1 时会被当成负数
        BigInteger value = new BigInteger(1, data);
        char[] out = new char[LENGTH];

        // 从最低位开始，每次取 5 位（相当于除以 32 取余）
        for (int i = LENGTH - 1; i >= 0; i--) {
            out[i] = CROCKFORD[value.mod(BASE).intValue()];
            value = value.shiftRight(5);    // 右移 5 位 = 除以 32
        }
        return new String(out);
    }

    // ===== 按类型生成，前缀让 ID 一眼能看出是什么 =====

    public static String library() { return generate("lib_"); }
    public static String book()    { return generate("bk_"); }
    public static String chapter() { return generate("ch_"); }
    public static String entity()  { return generate("ent_"); }
    public static String review()  { return generate("rv_"); }
    public static String task()    { return generate("task_"); }
    public static String thread()  { return generate("th_"); }
}
```

#### 验证

新建 `backend/src/test/java/com/yimo/common/IdsTest.java`：

```java
package com.yimo.common;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdsTest {

    @Test
    void hasCorrectLengthAndPrefix() {
        String id = Ids.chapter();
        // 29 = 前缀 "ch_" 的 3 个字符 + ULID 本身的 26 个字符
        assertEquals(29, id.length());
        assertTrue(id.startsWith("ch_"));
    }

    @Test
    void isMonotonic() throws InterruptedException {
        String a = Ids.chapter();
        Thread.sleep(2);              // 等两毫秒，确保时间戳变了
        String b = Ids.chapter();

        // 后生成的字典序更大。这是 ULID 相对 UUID 的核心优势——
        // 数据库插入时不用重排索引，也能直接按 id 排序当时间序用
        assertTrue(a.compareTo(b) < 0, a + " 应该小于 " + b);
    }

    @Test
    void isUniqueWithinSameMillisecond() {
        // 同一毫秒内生成一万个，不应该有重复。
        // 这验证的是随机数部分够不够随机
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(Ids.ulid());
        }
        assertEquals(10_000, ids.size());
    }

    @Test
    void containsOnlyCrockfordChars() {
        // 生成的 ID 里不能出现 I、L、O、U——这几个字符容易和数字混淆
        String id = Ids.ulid();
        assertTrue(id.matches("[0-9A-HJKMNP-TV-Z]{26}"), "出现了非法字符: " + id);
    }
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

### 做完是什么样

```
┌────────────────────────────────┐
│  书库              [+ 添加书库] │
├────────────────────────────────┤
│  我的小说                       │
│  D:\Writing\我的小说      [移除]│
└────────────────────────────────┘
```

点「添加书库」弹窗，可以手填路径，也可以点「浏览…」调系统窗口选。关掉浏览器重开，书库还在。

### 为什么第一个做这个

三个理由：

**它是所有功能的前置。** 没有书库，章节、批注、知识库全都无从谈起。

**它是完整链路的最小样本。** 前端表单 → HTTP 请求 → 后端处理 → 数据库 → 返回 → 前端渲染，这条链路跑通一次，后面所有功能都是在这个骨架上加东西。

**它没有技术难点。** 不涉及富文本、文件解析、AI、并发。你能把全部注意力放在「理解前后端怎么对上」，而不是同时跟五个问题搏斗。

对比一下，为什么不是别的：

| 候选 | 为什么不是第一个 |
|---|---|
| 编辑器 | 富文本 + Markdown 往返 + 输入法，三个难点叠在一起，出问题分不清是谁的锅 |
| AI 校对 | 依赖书库、章节、批注、模型配置，前置太多 |
| 文件扫描 | frontmatter 解析、类型推断、编码/BOM，够写一天 |

### 这一段会碰到的新概念

| 概念 | 一句话说明 |
|---|---|
| 分层架构 | Controller / Service / Mapper 三层各管一摊，混在一起后期没法维护 |
| DTO | 专门用来在前后端之间传数据的类，不参与数据库操作 |
| `record` | Java 14+ 的不可变数据载体，一行顶一个完整类 |
| MyBatis-Plus | 让数据库操作不用手写 SQL 的框架 |
| REST 风格 | 用 HTTP 方法（GET/POST/DELETE）表达「要做什么」的接口设计方式 |

---

### 后端

后端要写三个文件，它们分属三层，职责不同：

```
Controller  ← 收 HTTP 请求，返回 HTTP 响应。只做转发，不写业务逻辑
    ↓
Service     ← 业务逻辑都在这里：校验、组装、事务
    ↓
Mapper      ← 只跟数据库打交道，一个方法对应一类 SQL
```

**为什么非要分三层？** 因为它们的「变化原因」不同：

- 前端要改接口格式 → 改 Controller
- 业务规则变了（比如「同一个目录不能重复添加」）→ 改 Service
- 换数据库或者改表结构 → 改 Mapper

如果全塞在一个类里，改任何一样都要动同一个文件，改完还得把不相关的部分重新测一遍。分开之后，改哪层测哪层。

现在最开始的三个文件是：DTO（两层都要用，单独放一个包）、Service、Controller。

#### 1. 定义 DTO

**DTO 是什么**：Data Transfer Object，数据传输对象。它专门用来在前后端之间传数据，**不参与数据库操作**。

**为什么不直接用 `Library` 实体当接口的出入参**：

| 直接用实体 | 用 DTO |
|---|---|
| 表加个字段，接口返回跟着变，前端可能因此报错 | 返回什么是明确的契约，加字段不影响前端 |
| 数据库的所有字段都暴露给前端 | 只暴露前端需要的 |
| 将来加了内部字段（比如缓存的索引路径），容易误传出去 | 想传什么写什么 |

举个具体的：`Library` 实体有 `lastOpened`（上次打开时间），列表接口暂时用不上。用 DTO 就可以不返回。

**两个方向要分开定义**：

- `LibraryCreateRequest` —— 前端**发给后端**的数据。创建书库时需要什么？路径、可选的名字。就这两个。
- `LibraryView` —— 后端**返回给前端**的数据。前端要显示什么？id、名字、路径、书籍数、字数、时间。

**命名习惯**：进来的叫 `XxxRequest`，出去叫 `XxxView` 或 `XxxResponse`。

**为什么用 `record` 不用 `class`**：

`record` 是 Java 14 引入的**不可变数据载体**。看这一行：

```java
public record LibraryCreateRequest(String path, String name) {}
```

编译器会自动生成：

- 两个 `private final` 字段
- 一个全参构造函数
- 两个读取方法（注意是 `path()` 和 `name()`，**没有 get 前缀**）
- `equals` / `hashCode` / `toString`

手写这些要四十多行。

**关键在「不可变」**：字段是 final 的，构造完就不能改。这对 DTO 正合适——它就是一次性传值的容器。

而实体类需要可变（MyBatis 从数据库读数据时要往里面填），所以那边用普通类加 Lombok 的 `@Data`。

**记住这个分工**：

| | 用途 | 写法 | 为什么 |
|---|---|---|---|
| DTO | 前后端传值 | `record` | 不可变，传完就丢 |
| 实体 | 对应数据库表 | `class` + `@Data` | 要能被 MyBatis 填充 |

**开始写代码**。

新建 `backend/src/main/java/com/yimo/dto/LibraryCreateRequest.java`：

```java
package com.yimo.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 添加书库的请求。
 *
 * @param path 文件夹的绝对路径，必填
 * @param name 显示名，不填则取文件夹名
 */
public record LibraryCreateRequest(
        @NotBlank(message = "路径不能为空") String path,
        String name
) {
}
```

`@NotBlank` 是校验注解，配合 Controller 上的 `@Valid` 使用——路径为空时 Spring 会自动返回 400，不用你手写 if 判断。

新建 `backend/src/main/java/com/yimo/dto/LibraryView.java`：

```java
package com.yimo.dto;

import java.time.OffsetDateTime;

/**
 * 书库的展示数据。
 *
 * @param bookCount   书籍数，暂时固定为 0（迭代 2 扫描后才有值）
 * @param wordCount   总字数，同上
 * @param lastOpenedAt 上次打开时间，可能为 null
 */
public record LibraryView(
        String id,
        String name,
        String path,
        int bookCount,
        long wordCount,
        OffsetDateTime lastOpenedAt,
        OffsetDateTime createdAt
) {
}
```

**注意时间字段的类型**：这里用的是 `OffsetDateTime`（带时区偏移），而实体里是 `LocalDateTime`（不带时区）。

为什么要换？`LocalDateTime` 转成 JSON 是一串 `2026-09-18T17:18:34`——前端拿到不知道这是哪个时区的时间。`OffsetDateTime` 会输出 `2026-09-18T17:18:34+08:00`，带上了 `+08:00`，前端就能正确显示。

转换在 Service 里做，下一步会看到。

#### 2. 写 Service

**Service 层做什么**：所有业务逻辑。校验、查重、组装数据、决定要不要报错——都在这。

**为什么 Controller 不直接调 Mapper**：

假设前端直接调 Mapper，那么「同一个目录不能重复添加」这条规则就得写在 Controller 里。将来如果加一个「批量导入书库」的功能，它也得自己去查重——同一条规则写两遍，改的时候容易漏掉一处。

放在 Service 里，规则只有一份，谁要用谁调。

新建 `backend/src/main/java/com/yimo/service/LibraryService.java`：

```java
package com.yimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yimo.common.BizException;
import com.yimo.common.ErrorCode;
import com.yimo.common.Ids;
import com.yimo.domain.Library;
import com.yimo.dto.LibraryCreateRequest;
import com.yimo.dto.LibraryView;
import com.yimo.mapper.LibraryMapper;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class LibraryService {

    private final LibraryMapper libraryMapper;

    public LibraryService(LibraryMapper libraryMapper) {
        this.libraryMapper = libraryMapper;
    }

    public LibraryView create(LibraryCreateRequest req) {
        // 1. 规范化路径：转成绝对路径，并解析掉 . 和 ..
        Path path = Path.of(req.path()).toAbsolutePath().normalize();

        // 2. 校验：必须是存在且可读的目录
        if (!Files.isDirectory(path)) {
            throw new BizException(ErrorCode.LIBRARY_PATH_INVALID, "目录不存在: " + path);
        }
        if (!Files.isReadable(path)) {
            throw new BizException(ErrorCode.LIBRARY_PATH_INVALID, "目录不可读: " + path);
        }

        // 3. 查重：同一个目录不能添加两次
        Long exists = libraryMapper.selectCount(
                new LambdaQueryWrapper<Library>().eq(Library::getPath, path.toString()));
        if (exists > 0) {
            throw new BizException(ErrorCode.LIBRARY_PATH_DUPLICATE);
        }

        // 4. 组装实体并入库
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
        if (lib == null) {
            throw new BizException(ErrorCode.LIBRARY_NOT_FOUND);
        }
        // 只删数据库记录，磁盘文件一个字不动
        libraryMapper.deleteById(id);
    }

    /** 实体 → DTO。时间字段顺带转换时区 */
    private LibraryView toView(Library lib) {
        return new LibraryView(
                lib.getId(),
                lib.getName(),
                lib.getPath(),
                0,      // bookCount，迭代 2 扫描后才有值
                0,      // wordCount，同上
                toOffset(lib.getLastOpened()),
                toOffset(lib.getCreatedAt()));
    }

    private OffsetDateTime toOffset(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
```

**逐段看几个地方**：

**`@Service` 是什么**：告诉 Spring「这个类由你管理」。Spring 启动时发现它，创建一个实例放进容器。别的地方需要 `LibraryService` 时，Spring 会自动把它塞进构造函数——这叫**依赖注入**。

**为什么用构造函数注入而不是 `@Autowired` 字段注入**：

```java
// 推荐：构造函数注入
private final LibraryMapper libraryMapper;

public LibraryService(LibraryMapper libraryMapper) {
    this.libraryMapper = libraryMapper;
}
```

```java
// 不推荐：字段注入
@Autowired
private LibraryMapper libraryMapper;
```

构造函数注入的好处：字段可以是 `final`（创建后不能改），而且**缺依赖时启动就报错**，不会拖到运行时才炸。

**`LambdaQueryWrapper` 是什么**：MyBatis-Plus 提供的查询条件构造器。这一句：

```java
new LambdaQueryWrapper<Library>().eq(Library::getPath, path.toString())
```

等价于 SQL 的 `WHERE path = '...'`。

用 `Library::getPath` 而不是写字符串 `"path"` 的好处：**改字段名时编译器会报错**。写成字符串的话，字段改名了，这里还静静地跑着，直到查询出错才发现。

**为什么要 `.toAbsolutePath().normalize()`**：用户输入 `D:/Writing/../Writing/小说` 时，规范化之后变成 `D:/Writing/小说`，这样查重才能正确判断是不是同一个目录。

**`orderByDesc(Library::getLastOpened)`**：按最近打开时间倒序。列表里最近用过的书库排最前面。

#### 3. 写 Controller

**Controller 只做三件事**：接请求、调 Service、返回结果。不写业务逻辑。

新建 `backend/src/main/java/com/yimo/controller/LibraryController.java`：

```java
package com.yimo.controller;

import com.yimo.dto.LibraryCreateRequest;
import com.yimo.dto.LibraryView;
import com.yimo.service.LibraryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/libraries")
public class LibraryController {

    private final LibraryService libraryService;

    public LibraryController(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    /** GET /api/libraries —— 列出所有书库 */
    @GetMapping
    public List<LibraryView> list() {
        return libraryService.list();
    }

    /** POST /api/libraries —— 添加书库 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LibraryView create(@RequestBody @Valid LibraryCreateRequest req) {
        return libraryService.create(req);
    }

    /** DELETE /api/libraries/{id} —— 移除书库 */
    @DeleteMapping("/{id}")
    public Map<String, Object> remove(@PathVariable String id) {
        libraryService.remove(id);
        // 显式告诉前端：只从列表里移除了，磁盘文件一个字没动
        return Map.of("id", id, "removed", true, "filesDeleted", false);
    }
}
```

**注解逐个说**：

| 注解 | 作用 |
|---|---|
| `@RestController` | 这个类是处理 HTTP 请求的，返回值自动转成 JSON |
| `@RequestMapping("/api/libraries")` | 类里所有接口的 URL 都以这个开头 |
| `@GetMapping` | 处理 GET 请求 |
| `@PostMapping` | 处理 POST 请求 |
| `@DeleteMapping("/{id}")` | 处理 DELETE 请求，`{id}` 是路径参数 |
| `@RequestBody` | 把请求体里的 JSON 转成 Java 对象 |
| `@PathVariable` | 把 URL 里的 `{id}` 取出来赋给参数 |
| `@Valid` | 触发 DTO 上的校验注解（比如 `@NotBlank`） |
| `@ResponseStatus(CREATED)` | 成功时返回 201 而不是默认的 200 |

**为什么用 REST 风格**：URL 表示「资源」，HTTP 方法表示「对它做什么」。

```
GET    /api/libraries        列出
POST   /api/libraries        新建
DELETE /api/libraries/{id}   删除
```

对比另一种常见写法 `POST /api/getLibraryList`、`POST /api/deleteLibrary`——后者把动作写在 URL 里，是早期风格。REST 风格的好处是**语义清晰**：看方法就知道会不会修改数据，能不能被缓存、能不能重试都有明确规则。

**`@ResponseStatus(HttpStatus.CREATED)`**：创建成功时返回 201 而不是 200。这是 HTTP 语义——201 表示「资源被创建了」，前端可以据此判断。

**删除接口为什么返回一个 Map 而不是空**：因为要明确告诉前端「磁盘文件没删」。

```java
Map.of("id", id, "removed", true, "filesDeleted", false)
```

这个字段很重要。用户看到「移除」两个字会担心文件被删了，接口返回 `filesDeleted: false`，前端就能显示「已移除（磁盘文件未删除）」。

**注意 `@Valid` 的写法**：它放在 `@RequestBody` 后面，触发的是 DTO 里的校验注解：

```java
public record LibraryCreateRequest(
        @NotBlank(message = "路径不能为空") String path,
        String name
) {
}
```

路径为空时，Spring 自动返回 400 和错误信息，不用你写 if 判断。**校验规则写在数据定义旁边**，而不是散落在业务代码里。

#### 4. 验证后端

启动后端：

```bash
cd D:/Project/yi-mo/backend
mvn spring-boot:run
```

**测列表**（另开一个终端）：

```bash
curl http://127.0.0.1:18080/api/libraries
```

预期返回一个 JSON 数组（可能为空 `[]`）。

**测创建**：

```bash
curl -X POST http://127.0.0.1:18080/api/libraries \
  -H "Content-Type: application/json" \
  -d '{"path":"D:/Project/yi-mo/test-library"}'
```

先建一个空目录当测试用：

```bash
mkdir -p D:/Project/yi-mo/test-library
```

预期返回：

```json
{"id":"lib_01M2...","name":"test-library","path":"D:\\Project\\yi-mo\\test-library","bookCount":0,"wordCount":0,"lastOpenedAt":null,"createdAt":"2026-09-18T20:30:00+08:00"}
```

**`createdAt` 带 `+08:00`**——这就是前面用 `OffsetDateTime` 的效果。

**测重复添加**：

```bash
curl -X POST http://127.0.0.1:18080/api/libraries \
  -H "Content-Type: application/json" \
  -d '{"path":"D:/Project/yi-mo/test-library"}'
```

预期 409 和「该路径已添加过」。

**测路径不存在**：

```bash
curl -X POST http://127.0.0.1:18080/api/libraries \
  -H "Content-Type: application/json" \
  -d '{"path":"D:/不存在的目录"}'
```

预期 400 和「目录不存在」。

**测路径为空**：

```bash
curl -X POST http://127.0.0.1:18080/api/libraries \
  -H "Content-Type: application/json" \
  -d '{"path":""}'
```

预期 400 和「path: 路径不能为空」——这就是 `@Valid` + `@NotBlank` 的效果。

**测删除**：

```bash
curl -X DELETE http://127.0.0.1:18080/api/libraries/<刚才返回的id>
```

预期：

```json
{"id":"lib_01M2...","removed":true,"filesDeleted":false}
```

**然后去文件管理器看 `D:/Project/yi-mo/test-library` 还在不在**。在，就说明「移除只是移除记录，不碰文件」这条守住了。

**这几个 curl 命令建议存成一个文件**，改代码后挨个跑一遍。后面的迭代会越加越多，手工测很容易漏。

### 前端

**1. 收窄类型**（`src/api/library.ts` 补上创建和删除）

```ts
export const libraryApi = {
  list: () => http.get<unknown, Library[]>('/libraries'),
  create: (data: { path: string; name?: string }) =>
    http.post<unknown, Library>('/libraries', data),
  remove: (id: string) =>
    http.delete<unknown, { id: string; removed: boolean; filesDeleted: boolean }>(`/libraries/${id}`),
}
```

**2. 页面**（`src/views/LibraryView.vue`）

用 Naive UI 组装：一个列表 + 一个"添加书库"按钮 + 弹窗输入路径。

```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { NCard, NButton, NModal, NInput, NList, NListItem, NEmpty, useMessage } from 'naive-ui'
import { libraryApi, type Library } from '@/api/library'
import { toApiError } from '@/api/http'

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
  } catch (e) {
    message.error(toApiError(e).message)
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="p-6 max-w-2xl mx-auto">
    <div class="flex items-center justify-between mb-5">
      <h2 class="text-lg font-medium text-neutral-800">书库</h2>
      <n-button type="primary" size="small" @click="showDialog = true">
        添加书库
      </n-button>
    </div>

    <n-empty
      v-if="libraries.length === 0"
      description="还没有书库，添加一个文件夹开始吧"
      class="py-16"
    />

    <n-list v-else bordered>
      <n-list-item v-for="lib in libraries" :key="lib.id">
        <div class="flex items-center justify-between">
          <div class="min-w-0">
            <div class="text-sm font-medium text-neutral-800">{{ lib.name }}</div>
            <div class="text-xs text-neutral-500 font-mono truncate mt-0.5">
              {{ lib.path }}
            </div>
          </div>
          <n-button size="tiny" quaternary @click="remove(lib.id)">移除</n-button>
        </div>
      </n-list-item>
    </n-list>

    <n-modal v-model:show="showDialog">
      <n-card
        style="width: 520px"
        title="添加书库"
        :bordered="false"
        role="dialog"
      >
        <p class="text-xs text-neutral-500 mb-3">
          填写一个文件夹的完整路径。亿墨会把它当作书库，里面的每个子文件夹是一本书。
        </p>
        <n-input
          v-model:value="newPath"
          placeholder="D:\我的小说"
          @keyup.enter="submit"
        />
        <template #footer>
          <div class="flex justify-end gap-2">
            <n-button size="small" @click="showDialog = false">取消</n-button>
            <n-button
              size="small"
              type="primary"
              :loading="submitting"
              :disabled="!newPath.trim()"
              @click="submit"
            >
              确定
            </n-button>
          </div>
        </template>
      </n-card>
    </n-modal>
  </div>
</template>
```

**注意几个地方**：

- `NCard` 用 `<template #footer>` 放底部按钮——Naive UI 的插槽写法
- `NModal` 用 `v-model:show` 控制显隐
- `NInput` 加了 `@keyup.enter="submit"`，输入路径后直接回车就能提交
- 列表里的移除按钮调 `remove(lib.id)`，你需要在 `<script>` 里补上这个函数：

```ts
async function remove(id: string) {
  try {
    await libraryApi.remove(id)
    message.success('已移除（磁盘文件未删除）')
    await load()
  } catch (e) {
    message.error(toApiError(e).message)
  }
}
```

**Naive UI 的样式要额外接一下**。`main.ts` 里如果用 `useMessage()`，需要外层有 `<n-message-provider>` 包着，否则会报 `No outer <n-message-provider />`。最简单的做法是在 `App.vue` 里包一层：

```vue
<script setup lang="ts">
import { RouterView } from 'vue-router'
import { NMessageProvider } from 'naive-ui'
</script>

<template>
  <n-message-provider>
    <RouterView />
  </n-message-provider>
</template>
```

### 3. 加一个文件夹选择按钮

**浏览器出于安全限制，不允许网页获取用户选择的绝对路径**（`<input type="file" webkitdirectory>` 只给相对路径）。所以这个功能只能由后端做——后端没有这个限制，可以直接调系统的文件夹选择窗口。

#### 3.1 后端

**第 1 步：错误码**

`ErrorCode` 里加三个：

```java
// ===== 原生对话框 =====
PICKER_UNSUPPORTED(HttpStatus.BAD_REQUEST, "当前环境没有图形界面，请手动填写路径"),
PICKER_BUSY(HttpStatus.CONFLICT, "已经有一个选择窗口打开了"),
PICKER_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "打开选择窗口失败"),
```

**第 2 步：DTO**

**两个类，两个文件**（Java 里一个 `.java` 文件只能有一个 `public` 类）。

新建 `backend/src/main/java/com/yimo/dto/PickDirectoryRequest.java`：

```java
package com.yimo.dto;

/**
 * 弹出目录选择窗口的请求。
 *
 * @param initialPath 窗口打开时定位到哪个目录，可为 null（则用系统默认位置）
 */
public record PickDirectoryRequest(String initialPath) {
}
```

新建 `backend/src/main/java/com/yimo/dto/PickDirectoryResponse.java`：

```java
package com.yimo.dto;

/**
 * 目录选择窗口的返回。
 *
 * @param picked 用户是否选了目录（点取消则为 false）
 * @param path   选中的绝对路径，未选中时为 null
 */
public record PickDirectoryResponse(boolean picked, String path) {
}
```

`picked` 是给「用户点了取消」准备的——取消不是错误，返回 `picked: false` 就行。

**第 3 步：Service**

新建 `com/yimo/service/DirectoryPickerService.java`：

```java
@Service
public class DirectoryPickerService {

    private static final Logger log = LoggerFactory.getLogger(DirectoryPickerService.class);

    /** 同一时间只允许一个选择窗口，防止用户连点弹出多个 */
    private final Semaphore semaphore = new Semaphore(1);

    public Optional<String> pickDirectory(String initialPath) {
        if (GraphicsEnvironment.isHeadless()) {
            throw new BizException(ErrorCode.PICKER_UNSUPPORTED);
        }
        if (!semaphore.tryAcquire()) {
            throw new BizException(ErrorCode.PICKER_BUSY);
        }

        try {
            AtomicReference<File> chosen = new AtomicReference<>();

            // JFileChooser 必须在事件调度线程（EDT）上创建和显示，
            // 直接在 Tomcat 的请求线程里 new 会出各种诡异问题
            SwingUtilities.invokeAndWait(() -> {
                useSystemLookAndFeel();

                JFileChooser chooser = new JFileChooser();
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.setDialogTitle("选择书库文件夹");
                chooser.setAcceptAllFileFilterUsed(false);

                File initial = toExistingDirectory(initialPath);
                if (initial != null) {
                    chooser.setCurrentDirectory(initial);
                }

                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    chosen.set(chooser.getSelectedFile());
                }
            });

            File file = chosen.get();
            return file == null ? Optional.empty() : Optional.of(file.getAbsolutePath());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.PICKER_FAILED, "等待用户选择时被中断");
        } catch (InvocationTargetException e) {
            log.error("弹出目录选择窗口失败", e.getCause());
            throw new BizException(ErrorCode.PICKER_FAILED,
                    e.getCause() != null ? e.getCause().getMessage() : "未知原因");
        } finally {
            semaphore.release();
        }
    }

    /** 用系统外观，窗口长得跟其他 Windows 程序一致，而不是 Swing 默认的金属灰 */
    private void useSystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            log.debug("设置系统外观失败，使用默认外观", e);
        }
    }

    private File toExistingDirectory(String path) {
        if (path == null || path.isBlank()) return null;
        File file = new File(path);
        return file.isDirectory() ? file : null;
    }
}
```

**第 4 步：`YimoApplication` 关掉 headless**

**这一步不做，弹窗会直接抛 `HeadlessException`。**

Spring Boot 默认以 headless 模式启动，那个模式下 AWT/Swing 全被禁用。

```java
public static void main(String[] args) {
    SpringApplication app = new SpringApplication(YimoApplication.class);

    // Spring Boot 默认 headless 模式，AWT/Swing 被禁用，
    // 弹文件夹选择窗口会抛 HeadlessException。关掉它才能用 DirectoryPickerService。
    //
    // 没有图形界面的环境不受影响：Service 里会先检测
    // GraphicsEnvironment.isHeadless()，检测到就降级报错，不会让服务起不来
    app.setHeadless(false);

    app.run(args);
}
```

**第 5 步：Controller**

新建 `com/yimo/controller/SystemController.java`：

```java
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final DirectoryPickerService pickerService;

    public SystemController(DirectoryPickerService pickerService) {
        this.pickerService = pickerService;
    }

    /** 弹出原生文件夹选择窗口。这个请求会阻塞到用户选完或取消 */
    @PostMapping("/pick-directory")
    public PickDirectoryResponse pickDirectory(
            @RequestBody(required = false) PickDirectoryRequest req) {

        String initialPath = req != null ? req.initialPath() : null;
        Optional<String> picked = pickerService.pickDirectory(initialPath);

        return new PickDirectoryResponse(picked.isPresent(), picked.orElse(null));
    }
}
```

#### 3.2 前端

**第 1 步：API**

新建 `frontend/src/api/system.ts`：

```ts
import { http } from './http'

export interface PickDirectoryResult {
  picked: boolean
  path: string | null
}

export const systemApi = {
  pickDirectory: (initialPath?: string) =>
    http.post<unknown, PickDirectoryResult>(
      '/system/pick-directory',
      { initialPath: initialPath ?? null },
      // 超时设为 0（不超时）：用户可能在窗口里翻半天文件夹，
      // 默认的 30 秒会让请求提前失败
      { timeout: 0 },
    ),
}
```

**第 2 步：页面上加按钮**

`LibraryView.vue` 的 `<script setup>` 里加：

```ts
import { systemApi } from '@/api/system'

async function browse() {
  try {
    const res = await systemApi.pickDirectory(newPath.value.trim() || undefined)
    if (res.picked && res.path) {
      newPath.value = res.path
    }
    // 用户点了取消：picked 是 false，什么都不做
  } catch (e) {
    // 没有图形界面的环境会返回 PICKER_UNSUPPORTED
    message.warning(toApiError(e).message)
  }
}
```

模板里把输入框包成一组，右边挂按钮：

```vue
<n-input-group>
  <n-input
    v-model:value="newPath"
    placeholder="D:\我的小说"
    @keyup.enter="submit"
  />
  <n-button @click="browse">浏览…</n-button>
</n-input-group>
```

别忘了在 `naive-ui` 的 import 里加上 `NInputGroup`。

#### 3.3 验证

重启后端（改了 Java 代码必须重启），然后：

| 检查 | 怎么做 | 预期 |
|---|---|---|
| 弹窗出现 | 点「浏览…」 | 弹出系统的文件夹选择窗口 |
| 选中后填进输入框 | 选一个目录，点确定 | 路径自动填入，可以直接点确定 |
| 取消不报错 | 打开窗口直接点取消 | 什么都不发生，输入框内容不变 |
| 连点不会弹多个 | 快速点两下「浏览…」 | 只弹一个，第二次请求返回「已经有一个选择窗口打开了」 |
| 窗口外观 | 看窗口样式 | 跟其他 Windows 程序一致，不是 Swing 默认的金属灰 |
| 无图形界面时降级 | 加 `-Djava.awt.headless=true` 启动 | 点浏览提示「当前环境没有图形界面，请手动填写路径」 |

**这个功能只在有图形界面的机器上有效。** 跑在 Docker 或无头服务器上时，前端会收到提示并让用户手动填写——不会因为弹不出窗口就整个功能瘫痪。

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

**2. Frontmatter 解析器**

#### 先说清楚 frontmatter 是什么

书稿的 `.md` 文件长这样：

```markdown
---
yimo: chapter
id: ch_01H8XYZ
title: 第一章 雪夜
volume: 第一卷 少年游
order: 1
---

　　他站在城头，看雪落下来。
```

开头被两行 `---` 夹住的那部分叫 **frontmatter**，里面是 YAML 格式的元数据。这是 Jekyll、Hugo、Obsidian 等工具通用的约定。

**为什么需要它**：Markdown 本身只能表达「哪些字加粗了」这类排版信息，没法表达「这一章属于哪一卷、序号是几、出场了哪些人物」。这些结构化字段得另找地方放。

三个选择：

| 方案 | 问题 |
|---|---|
| 另建一个 JSON 文件存元数据 | 两份数据必然不同步——md 删了 JSON 还在，改了一边另一边不知道 |
| 把元数据放数据库 | 作者的文件夹拷到别的电脑就丢了元数据，也不能用 Typora 打开 |
| **写在文件头部的 frontmatter** | 元数据和正文同生共死，任何 Markdown 编辑器都能打开，Git 能对比 |

选第三个。这也是 `02-library-format.md` 里「明文优先」原则的具体体现。

#### 为什么解析比看起来麻烦

**坑一：Windows 记事本存的 UTF-8 文件带 BOM**

BOM 是文件开头三个看不见的字节（十六进制 `EF BB BF`）。带 BOM 的文件，实际内容是：

```
﻿---
yimo: chapter
```

开头的 `---` 前面多了个字符，正则的 `^---` 就匹配不上。结果是**整份文件被当成没有元数据**——章节标题、序号、卷名全丢。

所以 `stripBom()` 是必须的，不是可有可无的优化。

**坑二：字段顺序不能乱**

解析结果要存进 Map。如果用 `HashMap`，遍历顺序每次可能不同——写回文件时字段顺序跟着变，作者的 Git 里会出现一堆「只调换了字段顺序」的无意义 diff。

用 `LinkedHashMap`，它保持插入顺序。

**坑三：可能压根没有 frontmatter**

作者直接写了个 `.md` 丢进来，没有元数据头。这时候不能报错——返回空的 frontmatter，让上层按目录和文件名推断类型（`02-library-format.md` 定义了这套推断规则）。

#### 写代码

**这里是两个类，必须放在两个文件里。**

Java 的规则：**一个 `.java` 文件只能有一个 `public` 类，且文件名必须和这个 public 类同名**。写在一个文件里编译不过。

#### 为什么 `ParsedMarkdown` 放 `storage` 不放 `dto`

它也是 `record`，看起来像 DTO，但它不是。

**DTO 在这个项目里的约定是「跟前端交互的数据」**：

```
LibraryCreateRequest   前端 → 后端
LibraryView            后端 → 前端
```

`ParsedMarkdown` 从头到尾不出后端——前端根本不知道它存在：

```
Markdown 文件 → FrontmatterCodec.parse() → ParsedMarkdown → ScanService 取用
                 ↑ 全程在后端内部
```

**判断规则**：这个 record 会不会被序列化成 JSON 发给前端？

| 会 | 不会 |
|---|---|
| 放 `dto` | 放它所属的模块 |

**放 `storage` 的三个理由**：

1. **它是存储层的实现细节。** 将来如果亿墨支持直接读 `.txt` 或 `.docx`，解析器会变，`ParsedMarkdown` 可能跟着变或者消失。而 `dto` 包里的东西是**接口契约**——改了要同步改前端，轻易动不得。

2. **`dto` 包会变成垃圾桶。** 如果所有 record 都往里塞，打开 `dto` 包分不清哪些是「改了要通知前端」的，哪些是「内部随便改」。这个区分很重要。

3. **内聚性。** `ParsedMarkdown` 只被 `storage` 包里的类创建和消费（现在只有 `FrontmatterCodec`，后面 `FileWriter` 也会用）。放在一起，改它的时候只影响这一个包。

**它更准确的叫法是「值对象」（Value Object）。** `record` 是语言特性，DTO 是用途——同一行 `record` 声明，按用途可以是 DTO（跨边界、有契约含义），也可以是值对象（内部不可变数据）。名字不重要，重要的是**改它的时候你知道会影响谁**。

**文件一**：`backend/src/main/java/com/yimo/storage/ParsedMarkdown.java`

```java
package com.yimo.storage;

import java.util.Map;

/**
 * 解析后的 Markdown：元数据 + 正文。
 *
 * @param frontmatter 文件头部的 YAML 字段。没有 frontmatter 时是空 Map（不是 null）
 * @param body        去掉 frontmatter 之后的正文
 */
public record ParsedMarkdown(Map<String, Object> frontmatter, String body) {
}
```

**文件二**：`backend/src/main/java/com/yimo/storage/FrontmatterCodec.java`

```java
package com.yimo.storage;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 读写 Markdown 的 frontmatter。
 *
 * <p>这一版只实现「读」，写回留到迭代 4（保存正文）时再做。
 */
@Component
public class FrontmatterCodec {

    /**
     * 匹配文件开头的 frontmatter 块。
     *
     * ^---\s*\r?\n      开头的 --- 加换行（\r? 兼容 Windows 的 CRLF）
     * (.*?)             中间的内容，非贪婪，所以只吃到第一个结尾的 ---
     * \r?\n---\s*\r?\n? 结尾的 --- 加换行
     *
     * DOTALL 让点号能匹配换行符，否则中间的多行内容匹配不到
     */
    private static final Pattern FM = Pattern.compile(
            "^---\\s*\\r?\\n(.*?)\\r?\\n---\\s*\\r?\\n?", Pattern.DOTALL);

    private final Yaml yaml = new Yaml();

    public ParsedMarkdown parse(String raw) {
        String text = stripBom(raw);

        Matcher m = FM.matcher(text);
        if (!m.find()) {
            // 没有 frontmatter：返回空元数据 + 全部内容当正文
            return new ParsedMarkdown(new LinkedHashMap<>(), text);
        }

        Map<String, Object> fm = yaml.load(m.group(1));

        // 只有一行 --- 时 yaml.load 返回 null，兜底成空 Map。
        // 再包一层 LinkedHashMap 是保险：SnakeYAML 返回的类型不保证有序
        return new ParsedMarkdown(
                fm != null ? new LinkedHashMap<>(fm) : new LinkedHashMap<>(),
                text.substring(m.end()));
    }

    /**
     * 剥掉 UTF-8 BOM。
     *
     * 不剥的话，开头的 --- 会变成「BOM + ---」，正则匹配失败，
     * 整份文件被误判成没有元数据。
     */
    private String stripBom(String s) {
        return s.startsWith("\uFEFF") ? s.substring(1) : s;
    }
}
```

**`@Component` 是什么**：跟 Service 上的 `@Service` 一样，告诉 Spring「这个类交给你管」。区别只是语义——`@Service` 表示业务层，`@Component` 表示通用组件。功能上等价，Spring 都会创建实例并管理依赖。

**`Yaml` 从哪来**：Spring Boot 的依赖里已经带了 SnakeYAML（Spring 自己用它读配置文件），不用额外加依赖。

#### 验证

写单元测试比手工测快，而且能覆盖那些「手工想不到」的边界情况。

新建 `backend/src/test/java/com/yimo/storage/FrontmatterCodecTest.java`：

```java
package com.yimo.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontmatterCodecTest {

    private final FrontmatterCodec codec = new FrontmatterCodec();

    @Test
    void parsesNormalFrontmatter() {
        String raw = """
                ---
                title: 第一章 雪夜
                order: 1
                ---

                　　正文内容
                """;

        ParsedMarkdown pm = codec.parse(raw);

        assertEquals("第一章 雪夜", pm.frontmatter().get("title"));
        assertEquals(1, pm.frontmatter().get("order"));
        assertTrue(pm.body().contains("正文内容"));
    }

    @Test
    void handlesFileWithoutFrontmatter() {
        String raw = "　　这是一份没有元数据的文件。";

        ParsedMarkdown pm = codec.parse(raw);

        assertTrue(pm.frontmatter().isEmpty());
        assertEquals(raw, pm.body());
    }

    @Test
    void handlesBomFromNotepad() {
        // 用记事本打开任意 md 文件，加个空格再保存，就会带上 BOM
        String raw = "\uFEFF---\ntitle: 带 BOM 的文件\n---\n\n正文";

        ParsedMarkdown pm = codec.parse(raw);

        // 没有 stripBom 的话，这里会是 null（正则匹配失败）
        assertEquals("带 BOM 的文件", pm.frontmatter().get("title"));
    }

    @Test
    void handlesCrlfLineEnding() {
        String raw = "---\r\ntitle: Windows 换行\r\n---\r\n\r\n正文";

        ParsedMarkdown pm = codec.parse(raw);

        assertEquals("Windows 换行", pm.frontmatter().get("title"));
    }
}
```

**第三条测试（BOM）是重点**。它验证的正是前面说的那个坑——你拿记事本随手存一下任意 `.md`，就会触发这个场景。没有这条测试，那个 bug 会一直潜伏到某个作者用记事本改了稿子才爆发。

**跑测试**：

```bash
cd D:/Project/yi-mo/backend
mvn test -Dtest=FrontmatterCodecTest
```

**3. 扫描服务**

#### 扫描要做什么

用户添加书库后，亿墨要遍历那个文件夹，把里面的 `.md` 找出来、解析、判断类型、写进数据库。这样左侧的章节树才有东西显示。

```
书库文件夹
├── 剑来/
│   ├── 07-正文/
│   │   ├── 第001章-雪夜.md     ← 解析出 title/order/volume，写进 chapter 表
│   │   └── 第002章-泥瓶巷.md
│   └── 03-人物/
│       └── 陈平安.md           ← 这是人物卡，不是章节，跳过
└── 长夜余火/
    └── 07-正文/...
```

#### 三个必须处理好的点

**1. 单个文件失败不能中断整个扫描**

作者的目录里可能混着各种东西，或者某个文件正被 Typora 独占打开（Windows 会抛 `FileSystemException`）。

如果遇到一个坏文件就整个扫描失败，用户会觉得「这软件连我的文件夹都读不了」。

正确做法：**记下错误继续扫**，扫完把错误清单展示出来。

**2. 限制遍历深度**

`Files.walk(root)` 不传深度参数会无限递归。如果用户把书库指向一个装了 `node_modules` 的目录，会直接卡死。

设个上限——书库结构是「书/目录/文件.md」这种浅层，8 层绰绰有余。

**3. 读文件必须指定 UTF-8**

`Files.readString(path)` 不传编码时用**系统默认编码**。Windows 中文环境下可能是 GBK，读 UTF-8 的稿子会全是乱码。

这个坑很隐蔽——代码在 macOS 或 Linux 上完全正常，一到 Windows 就出问题。

#### 写代码

新建 `backend/src/main/java/com/yimo/service/ScanService.java`：

```java
package com.yimo.service;

import cn.hutool.core.util.HashUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yimo.common.BizException;
import com.yimo.common.ErrorCode;
import com.yimo.common.Ids;
import com.yimo.domain.Chapter;
import com.yimo.mapper.ChapterMapper;
import com.yimo.storage.DocType;
import com.yimo.storage.FrontmatterCodec;
import com.yimo.storage.ParsedMarkdown;
import com.yimo.storage.TypeInferrer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class ScanService {

    private static final Logger log = LoggerFactory.getLogger(ScanService.class);

    /** 遍历深度上限。不设的话，指向含 node_modules 的目录会无限递归 */
    private static final int MAX_DEPTH = 8;

    private final ChapterMapper chapterMapper;
    private final FrontmatterCodec codec;

    public ScanService(ChapterMapper chapterMapper, FrontmatterCodec codec) {
        this.chapterMapper = chapterMapper;
        this.codec = codec;
    }

    /**
     * 扫描整个书库，把章节写进数据库。
     *
     * @param libraryId 书库 id
     * @param root      书库根目录
     * @return 文件总数、成功索引数、失败清单
     */
    public ScanResult scan(String libraryId, Path root) {
        List<Path> files;

        // try-with-resources：Stream 用完必须关闭，否则会一直占着文件句柄，
        // 后面想重命名或删除文件时会失败
        try (Stream<Path> stream = Files.walk(root, MAX_DEPTH)) {
            files = stream
                    // 只要普通文件（跳过目录、符号链接）
                    .filter(Files::isRegularFile)
                    // 只看 .md。toLowerCase 是为了兼容 .MD 这种大写扩展名
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".md"))
                    .toList();
        } catch (IOException e) {
            // 连根目录都遍历不了，这是致命错误，直接抛给上层的全局异常处理器
            throw new BizException(ErrorCode.FILE_READ_FAILED, "扫描失败: " + e.getMessage());
        }

        int indexed = 0;
        List<String> errors = new ArrayList<>();

        for (Path file : files) {
            try {
                if (indexFile(libraryId, root, file)) {
                    indexed++;
                }
            } catch (Exception e) {
                // 关键：单个文件失败不能中断整个扫描。
                // 注意这里捕获的是 Exception（宽），不是 IOException（窄）——
                // 编码错误、解析错误、数据库错误都要兜住
                log.warn("跳过文件 {}: {}", file, e.getMessage());
                errors.add(root.relativize(file).toString());
            }
        }

        log.info("扫描完成：共 {} 个文件，索引 {} 个章节，失败 {} 个",
                files.size(), indexed, errors.size());

        return new ScanResult(files.size(), indexed, errors);
    }

    /**
     * 解析并索引单个文件。
     *
     * @return true 表示这是章节并已入库；false 表示不是章节，跳过
     */
    private boolean indexFile(String libraryId, Path root, Path file) throws IOException {
        // 必须指定 UTF-8。不指定则用系统默认编码（Windows 上可能是 GBK），
        // 读 UTF-8 的稿子会全是乱码
        String raw = Files.readString(file, StandardCharsets.UTF_8);
        ParsedMarkdown pm = codec.parse(raw);

        // 判断文件类型。不是章节就直接返回，不走后面的写库逻辑
        DocType type = TypeInferrer.infer(file, pm.frontmatter(), root);
        if (type != DocType.CHAPTER) {
            return false;
        }

        String relPath = relPath(root, file);
        Map<String, Object> fm = pm.frontmatter();

        Chapter ch = new Chapter();
        // 已经索引过的文件要沿用原来的 id。
        // 换新 id 的话，挂在它上面的批注和快照全都会失联
        ch.setId(findExistingId(libraryId, relPath).orElseGet(Ids::chapter));
        ch.setLibraryId(libraryId);
        ch.setRelPath(relPath);
        ch.setBookName(bookNameOf(root, file));
        // frontmatter 里没写 title 时，退回用文件名当标题
        ch.setTitle(stringOr(fm.get("title"), titleFromFileName(file)));
        ch.setVolume(stringOr(fm.get("volume"), ""));
        ch.setSortOrder(parseOrder(file, fm));
        ch.setStatus(stringOr(fm.get("status"), "draft"));
        ch.setWordCount(countWords(pm.body()));
        // 内容哈希：下次扫描时比对，内容没变就跳过重新解析
        ch.setContentHash(HashUtil.sha256Hex(pm.body()));
        ch.setUpdatedAt(LocalDateTime.now());

        chapterMapper.insertOrUpdate(ch);
        return true;
    }

    // ===== 下面都是辅助方法 =====

    /**
     * 相对书库根的路径。
     *
     * 统一换成正斜杠：Windows 的 Path 会给出反斜杠，而反斜杠在 JSON 和
     * 正则里都要转义，统一成正斜杠能让后面省很多事。
     */
    private String relPath(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    /** 书库根下的第一层目录名就是书名：书库/剑来/07-正文/xxx.md → 剑来 */
    private String bookNameOf(Path root, Path file) {
        Path relative = root.relativize(file);
        return relative.getNameCount() > 1
                ? relative.getName(0).toString()
                : root.getFileName().toString();
    }

    /** 从文件名提取标题：第001章-雪夜.md → 第001章-雪夜 */
    private String titleFromFileName(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(".md")
                ? name.substring(0, name.length() - 3)
                : name;
    }

    /** 取 frontmatter 里的值，取不到就用默认值 */
    private String stringOr(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    /**
     * 排序序号。
     *
     * 优先用 frontmatter 里的 order；没有的话从文件名里的数字提取
     * （第012章-xxx.md → 12）。这样作者手改文件名排序也能生效。
     */
    private int parseOrder(Path file, Map<String, Object> fm) {
        Object order = fm.get("order");
        if (order instanceof Number n) {
            return n.intValue();
        }

        // 从文件名里找「第」和「章」之间的数字
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("第\\s*(\\d+)\\s*章")
                .matcher(file.getFileName().toString());
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /**
     * 统计字数。
     *
     * 现在是简化版：去掉所有空白字符后的长度。
     * 中文一个字算一个，英文单词会被按字母数算——精确统计留到以后的迭代。
     */
    private int countWords(String text) {
        return text.replaceAll("\\s", "").length();
    }

    /** 查这个路径之前是否已经索引过，是的话返回原来的 id */
    private Optional<String> findExistingId(String libraryId, String relPath) {
        Chapter existing = chapterMapper.selectOne(
                new LambdaQueryWrapper<Chapter>()
                        .eq(Chapter::getLibraryId, libraryId)
                        .eq(Chapter::getRelPath, relPath));
        return existing == null ? Optional.empty() : Optional.of(existing.getId());
    }
}
```

再建一个 `ScanResult.java`（同样，一个文件一个 public 类）：

```java
package com.yimo.service;

import java.util.List;

/**
 * 扫描结果。
 *
 * @param total    扫到的文件总数
 * @param indexed  成功索引的章节数
 * @param errors   解析失败的文件相对路径，会展示给作者
 */
public record ScanResult(int total, int indexed, List<String> errors) {
}
```

**几个值得注意的地方**：

**`insertOrUpdate`** 是 MyBatis-Plus 提供的方法：主键存在就更新，不存在就插入。不用自己写「先查再判断」。

**`findExistingId` 先查一次数据库**，是为了沿用旧 id。这一步不能省——每次扫描都生成新 id 的话，作者之前处理过的批注会全部失联。

**`relPath` 统一用正斜杠**。Windows 的 `Path.toString()` 给的是反斜杠，而反斜杠在 JSON 里要写成 `\\`、在正则里是转义符，一路都是麻烦。存进数据库时就统一成斜杠，读取时不用再转换。

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

### 做完是什么样

点左侧树上的章节，右边显示它的内容。现在还是只读的纯文本，编辑器在下一个迭代。

### 为什么这一步先不做编辑器

编辑器（Tiptap）是**整个项目最容易出问题的部分**——富文本的文档模型、Markdown 双向转换、中文输入法，三个难点叠在一起。

如果现在就把编辑器接进来，一旦显示不对，你分不清是：
- 接口返回的数据有问题？
- 解析 Markdown 有问题？
- 编辑器渲染有问题？

**先用最笨的 `<pre>` 标签把数据链路验证通**，确认「接口返回的正文和文件里的一模一样」，再换编辑器。到那时出问题，就只可能是编辑器自己的锅。

### 后端

#### 1. 先写 LibraryStorage

读文件这件事会被反复用到（读章节、写章节、做快照、导出……），抽成一个类。

**为什么不让 Service 直接调 `Files.readString`**：

- 读取要指定 UTF-8、要处理异常、要校验路径。这些逻辑散在各处的话，改一处漏一处
- 将来如果要支持「书库在网盘上」这类场景，只改这一个类

新建 `backend/src/main/java/com/yimo/storage/LibraryStorage.java`：

```java
package com.yimo.storage;

import com.yimo.common.BizException;
import com.yimo.common.ErrorCode;
import com.yimo.domain.Library;
import com.yimo.mapper.LibraryMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 书库文件的读写。
 *
 * <p>所有碰磁盘的操作都从这里走，别的地方不直接调 Files。
 * 这样「指定 UTF-8」「处理异常」「校验路径」这些事只有一份实现。
 */
@Component
public class LibraryStorage {

    private final LibraryMapper libraryMapper;

    public LibraryStorage(LibraryMapper libraryMapper) {
        this.libraryMapper = libraryMapper;
    }

    /** 取书库的根目录 */
    public Path rootOf(String libraryId) {
        Library lib = libraryMapper.selectById(libraryId);
        if (lib == null) {
            throw new BizException(ErrorCode.LIBRARY_NOT_FOUND);
        }
        return Path.of(lib.getPath());
    }

    /**
     * 读书库里的文件。
     *
     * <p>强制 UTF-8：不指定的话用系统默认编码，Windows 上可能是 GBK，
     * 读 UTF-8 的稿子会全是乱码。
     */
    public String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BizException(ErrorCode.FILE_READ_FAILED, e.getMessage());
        }
    }

    /**
     * 原子写入。
     *
     * <p>先写临时文件再重命名，这是为了防「写到一半断电」——
     * 直接覆盖原文件的话，中途崩溃会留下一个半截的稿子。
     * 重命名是操作系统级的原子操作，要么完成要么没发生。
     */
    public void writeAtomic(Path target, String content) {
        try {
            Path dir = target.getParent();
            Files.createDirectories(dir);

            // 临时文件必须和目标在同一个目录（同一个磁盘分区），
            // 否则 ATOMIC_MOVE 会降级成「复制 + 删除」，就不再是原子的了
            Path tmp = dir.resolve(target.getFileName() + ".tmp");
            Files.writeString(tmp, content, StandardCharsets.UTF_8);

            Files.move(tmp, target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

        } catch (IOException e) {
            throw new BizException(ErrorCode.FILE_WRITE_FAILED, e.getMessage());
        }
    }
}
```

**这个类里的 `writeAtomic` 现在还用不到，但下一步就要用。** 先一起写了，因为它和 `read` 是同一个职责。

#### 2. 定义 ChapterDetail

这个 DTO 字段比较多，因为前端渲染一个章节需要这些信息。

新建 `backend/src/main/java/com/yimo/dto/ChapterDetail.java`：

```java
package com.yimo.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 章节详情。前端的编辑器拿这个来渲染。
 *
 * @param bookName   所属书名
 * @param relPath    相对书库根的路径，前端用它显示面包屑
 * @param sortOrder  卷内序号
 * @param pov        视角人物，可为 null
 * @param characters 出场人物 id 列表
 * @param locations  出场地点 id 列表
 * @param storyTime  故事内时间，作者自由填写，可为 null
 * @param summary    一句话梗概，可为 null
 * @param contentHash 内容哈希，**保存时必须原样回传**——后端用它检测文件是否被外部改过
 * @param content    正文，不含 frontmatter
 */
public record ChapterDetail(
        String id,
        String bookName,
        String relPath,
        String title,
        String volume,
        Integer sortOrder,
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
) {
}
```

**`contentHash` 这个字段现在是「只传不用」，但要记住它**。下一步保存正文时，前端要把它原样发回来，后端比对不一致就说明「你打开编辑器期间，有别的东西改了这个文件」，于是拒绝写入、弹窗让作者选择。没有它就会静默覆盖掉作者用别的编辑器做的修改。

**`characters` 和 `locations` 现在固定传空列表**，因为实体识别还没做（迭代 4）。先把字段占上，接口的形状定下来，前端可以照常写。

#### 3. 写 Controller

新建 `backend/src/main/java/com/yimo/controller/ChapterController.java`：

```java
package com.yimo.controller;

import com.yimo.common.BizException;
import com.yimo.common.ErrorCode;
import com.yimo.domain.Chapter;
import com.yimo.dto.ChapterDetail;
import com.yimo.mapper.ChapterMapper;
import com.yimo.storage.FrontmatterCodec;
import com.yimo.storage.LibraryStorage;
import com.yimo.storage.ParsedMarkdown;
import com.yimo.storage.PathGuard;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/chapters")
public class ChapterController {

    private final ChapterMapper chapterMapper;
    private final LibraryStorage storage;
    private final FrontmatterCodec codec;

    public ChapterController(ChapterMapper chapterMapper,
                             LibraryStorage storage,
                             FrontmatterCodec codec) {
        this.chapterMapper = chapterMapper;
        this.storage = storage;
        this.codec = codec;
    }

    @GetMapping("/{id}")
    public ChapterDetail get(@PathVariable String id) {
        // 1. 从数据库拿元数据（标题、序号、卷名这些索引信息）
        Chapter ch = chapterMapper.selectById(id);
        if (ch == null) {
            throw new BizException(ErrorCode.CHAPTER_NOT_FOUND);
        }

        // 2. 正文从文件读，不从数据库读。
        //    文件是唯一真相源，数据库只存索引。
        //    PathGuard 会校验解析出的路径仍在书库目录内，防止 ../ 越界
        Path file = PathGuard.resolve(storage.rootOf(ch.getLibraryId()), ch.getRelPath());
        String raw = storage.read(file);

        // 3. 剥掉 frontmatter，只要正文
        ParsedMarkdown pm = codec.parse(raw);

        return new ChapterDetail(
                ch.getId(),
                ch.getBookName(),
                ch.getRelPath(),
                ch.getTitle(),
                ch.getVolume(),
                ch.getSortOrder(),
                ch.getStatus(),
                ch.getPov(),
                List.of(),      // characters，实体识别做完后才有值
                List.of(),      // locations，同上
                ch.getStoryTime(),
                null,           // summary，迭代 4 生成
                ch.getWordCount(),
                ch.getContentHash(),
                pm.body(),      // ← 正文在这里
                ch.getUpdatedAt(),
                ch.getUpdatedAt());
    }
}
```

**为什么这个 Controller 里没有 Service**：现在的逻辑就是「查库 → 读文件 → 拼 DTO」，全是转发，没有业务规则。

如果加了业务规则（比如「未登录不能读」「读一次要记访问日志」），就该抽 Service 了。**不要为了形式而分层**。

### 前端

#### 1. 加 API 方法

新建 `frontend/src/api/chapter.ts`：

```ts
import { http } from './http'

/**
 * 章节详情。
 *
 * 字段名和后端 ChapterDetail 一一对应，不能写错——
 * 后端返回 contentHash，这里写成 content_hash 就会拿到 undefined。
 */
export interface ChapterDetail {
  id: string
  bookName: string
  relPath: string
  title: string
  volume: string
  sortOrder: number
  status: 'draft' | 'revising' | 'done'
  pov: string | null
  characters: string[]
  locations: string[]
  storyTime: string | null
  summary: string | null
  wordCount: number
  contentHash: string
  content: string
  createdAt: string
  updatedAt: string
}

export const chapterApi = {
  get: (id: string) => http.get<unknown, ChapterDetail>(`/chapters/${id}`),
}
```

#### 2. 写读取页面

新建 `frontend/src/views/ChapterView.vue`：

```vue
<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { chapterApi, type ChapterDetail } from '@/api/chapter'
import { toApiError } from '@/api/http'

const route = useRoute()

const chapter = ref<ChapterDetail | null>(null)
const loading = ref(false)
const error = ref('')

async function load(id: string) {
  loading.value = true
  error.value = ''
  try {
    chapter.value = await chapterApi.get(id)
  } catch (e) {
    error.value = toApiError(e).message
    chapter.value = null
  } finally {
    loading.value = false
  }
}

// watch 而不是 onMounted：地址栏里的 id 变了要重新加载。
// 用 onMounted 的话，从「第1章」跳到「第2章」页面不会刷新
watch(
  () => route.params.id as string,
  (id) => {
    if (id) load(id)
  },
  { immediate: true },   // immediate 让首次进入也触发
)
</script>

<template>
  <div class="p-6 max-w-3xl mx-auto">
    <div v-if="loading" class="text-sm text-neutral-500">加载中…</div>

    <div v-else-if="error" class="rounded-lg border border-red-200 bg-red-50 p-4">
      <p class="text-sm text-red-800">{{ error }}</p>
    </div>

    <template v-else-if="chapter">
      <h1 class="text-xl font-medium text-neutral-800 mb-1">{{ chapter.title }}</h1>
      <p class="text-xs text-neutral-400 mb-6">
        {{ chapter.bookName }} · {{ chapter.volume }} · {{ chapter.wordCount }} 字
      </p>

      <!--
        white-space: pre-wrap 让换行和空格照原样显示。
        没有它的话，HTML 会把连续的空白折叠成一个空格，
        段首的全角空格缩进就没了。
      -->
      <pre class="whitespace-pre-wrap font-sans text-base leading-loose text-neutral-700">{{ chapter.content }}</pre>
    </template>
  </div>
</template>
```

**`<pre>` 的 `white-space: pre-wrap` 是关键**。HTML 默认会把连续空白折叠成一个空格，没有这个样式，稿子里段的缩进和空行全都会被吃掉。

### 配置路由

`frontend/src/router/index.ts` 加一条：

```ts
{
  path: '/chapters/:id',
  name: 'chapter',
  component: () => import('@/views/ChapterView.vue'),
},
```

### 验证

**先造点测试数据**——需要一个真实的书库文件夹。

```bash
mkdir -p "D:/Project/yi-mo/test-library/剑来/07-正文"
```

新建 `D:/Project/yi-mo/test-library/剑来/07-正文/第001章-雪夜.md`，内容：

```markdown
---
title: 第一章 雪夜
order: 1
volume: 第一卷 少年游
status: draft
---

　　他站在城头，看雪落下来。

　　"你确定要走？"身后有人问。

　　他没有回头。
```

然后：

1. 后端启动，访问 `POST /api/libraries` 添加 `D:/Project/yi-mo/test-library`
2. 访问 `POST /api/libraries/{id}/rescan` 触发扫描（迭代 2 的接口）
3. 访问 `GET /api/libraries/{id}/tree` 拿到章节 id
4. 访问 `GET /api/chapters/{章节id}` 看返回

最后浏览器打开 `http://localhost:5173/chapters/{章节id}`。

| 检查 | 怎么做 | 预期 |
|---|---|---|
| 正文和文件一致 | 打开 `.md` 对照 | 一字不差 |
| frontmatter 没混进正文 | 看显示内容 | 没有 `---` 和 `title:` 这些 |
| 全角空格缩进保留 | 看段落开头 | 两个全角空格的缩进在 |
| 切换章节不串内容 | 改地址栏的 id | 内容跟着变，不残留 |
| 中文不乱码 | 看正文 | 正常显示 |

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

下面是自动保存的核心逻辑（片段，放在编辑器组件里）。注意 `toApiError` 要从 `@/api/http` 导入。

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
  } catch (e) {
    const err = toApiError(e)
    if (err.error === 'CONTENT_HASH_MISMATCH') {
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

**1. 规则接口**

**四个类，四个文件**（Java 里一个 `.java` 文件只能有一个 `public` 类）。都放在 `backend/src/main/java/com/yimo/rules/` 下。

`Severity.java`：

```java
package com.yimo.rules;

/** 问题严重程度，决定波浪线的颜色 */
public enum Severity {
    /** 确定是错的，比如错别字 */
    ERROR,
    /** 可能有问题，比如语病 */
    WARNING,
    /** 只是建议 */
    INFO
}
```

`RuleIssue.java`：

```java
package com.yimo.rules;

import java.util.List;

/**
 * 一条规则命中的结果。
 *
 * @param start       问题文本在章节里的起始位置（字符偏移）
 * @param end         结束位置
 * @param message     给作者看的问题说明
 * @param suggestions 候选替换文本，可以为空
 * @param confidence  置信度 0-1，低于 0.6 的会在界面上弱化显示
 */
public record RuleIssue(
        int start,
        int end,
        String message,
        List<String> suggestions,
        double confidence
) {
}
```

`RuleContext.java`：

```java
package com.yimo.rules;

/**
 * 规则执行时能拿到的上下文。
 *
 * <p>有些规则需要知道章节之外的信息——比如「他/她混用」要判断指代的是谁，
 * 得查出场人物表。这些东西通过这里传进来。
 *
 * <p>现在先留空，需要时再加字段。
 */
public record RuleContext(String chapterId) {
}
```

`Rule.java`：

```java
package com.yimo.rules;

import java.util.List;

/**
 * 一条校对规则。
 *
 * <p>实现类必须是纯函数——不碰数据库、不读文件、不调网络。
 * 这样它能被独立单元测试，也能安全地在多线程里跑。
 */
public interface Rule {

    /** 规则唯一标识，用于「这条规则我不想看」的忽略记录 */
    String id();

    /** 规则说明，显示在设置页的规则列表里 */
    String description();

    /** 命中时默认的严重程度 */
    Severity defaultSeverity();

    /** 默认是否开启。误报率高的规则默认关掉 */
    boolean defaultEnabled();

    /** 检查文本，返回所有命中的问题 */
    List<RuleIssue> check(String text, RuleContext ctx);
}
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
