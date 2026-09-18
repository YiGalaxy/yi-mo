# 构建、打包与缓存

仓库组织策略见 [技术选型](05-tech-stack.md) 的「仓库组织」一节。本文只讲怎么把代码变成用户能双击运行的东西。

## 1. 目录约定

```
yi-mo/
├── backend/              Spring Boot 后端
├── frontend/             Vue 3 前端
├── python/               Python 服务（预留，暂空）
├── docs/                 设计文档
├── openapi/              API 契约
├── scripts/              构建与启动脚本
│   ├── dev.bat / dev.sh          开发模式一键启动
│   ├── 启动亿墨.bat / start.sh    用户用的启动器
│   └── build.sh                  发布构建
├── VERSION               版本号（三端共用）
└── README.md
```

## 2. 开发模式一键启动

开发时后端在前端各自跑自己的 dev server，改代码即时生效。

**`scripts/dev.bat`**（Windows）

```bat
@echo off
chcp 65001 >nul
title 亿墨 · 开发模式

echo [1/2] 启动后端 http://127.0.0.1:18080
start "yimo-backend" cmd /k "cd /d %~dp0..\backend && mvn spring-boot:run"

echo [2/2] 启动前端 http://localhost:5173
start "yimo-frontend" cmd /k "cd /d %~dp0..\frontend && npm run dev"

echo.
echo 两个窗口已启动。浏览器访问 http://localhost:5173
echo 关闭：直接关掉那两个命令行窗口
```

**`scripts/dev.sh`**（macOS / Linux）

```bash
#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."

# 退出时一并杀掉两个子进程
trap 'kill 0' EXIT

(cd backend  && mvn spring-boot:run) &
(cd frontend && npm run dev) &
wait
```

**开发时的地址是 5173，不是 18080。** 前端代码里所有请求都写相对路径 `/api/xxx`，由 Vite 代理转发到 18080。这样生产环境（前后端同源）一个字都不用改。

**绝对不要在代码里写死 `http://localhost:18080`。** 这是最常见的部署事故来源。

## 3. 生产构建：前端打进 jar

### 3.1 Maven 插件配置

`backend/pom.xml` 加两个插件：

```xml
<build>
  <plugins>
    <!-- 构建时自动跑 npm install + npm run build -->
    <plugin>
      <groupId>com.github.eirslett</groupId>
      <artifactId>frontend-maven-plugin</artifactId>
      <version>1.15.1</version>
      <configuration>
        <workingDirectory>${project.basedir}/../frontend</workingDirectory>
        <installDirectory>${project.build.directory}/node</installDirectory>
        <!-- 国内网络必须配镜像，否则下载 Node 会卡住 -->
        <nodeDownloadRoot>https://npmmirror.com/mirrors/node/</nodeDownloadRoot>
        <npmDownloadRoot>https://registry.npmmirror.com/npm/-/</npmDownloadRoot>
      </configuration>
      <executions>
        <execution>
          <id>install-node-and-npm</id>
          <goals><goal>install-node-and-npm</goal></goals>
          <configuration>
            <nodeVersion>v20.18.0</nodeVersion>
          </configuration>
        </execution>
        <execution>
          <id>npm-ci</id>
          <goals><goal>npm</goal></goals>
          <configuration><arguments>ci</arguments></configuration>
        </execution>
        <execution>
          <id>npm-build</id>
          <goals><goal>npm</goal></goals>
          <configuration><arguments>run build</arguments></configuration>
        </execution>
      </executions>
    </plugin>

    <!-- 把前端产物拷进 jar 的 static 目录 -->
    <plugin>
      <artifactId>maven-resources-plugin</artifactId>
      <executions>
        <execution>
          <id>copy-frontend-dist</id>
          <phase>process-resources</phase>
          <goals><goal>copy-resources</goal></goals>
          <configuration>
            <outputDirectory>${project.build.outputDirectory}/static</outputDirectory>
            <resources>
              <resource>
                <directory>${project.basedir}/../frontend/dist</directory>
                <filtering>false</filtering>
              </resource>
            </resources>
          </configuration>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

**执行顺序由 Maven 的 phase 保证**：`frontend-maven-plugin` 默认绑定 `generate-resources`，`maven-resources-plugin` 绑定 `process-resources`，前者先跑。

**`frontend-maven-plugin` 会自动下载指定版本的 Node**，所以构建机不需要预装 Node。这是为了可复现构建——CI 上尤其重要。

**代价是国内下载慢**。上面已经配了 npmmirror 镜像。如果还是慢，用下面的 profile 方案改成本地已装的 Node：

```xml
<profiles>
  <profile>
    <id>local-node</id>
    <properties>
      <skip.installnodenpm>true</skip.installnodenpm>
    </properties>
  </profile>
</profiles>
```

然后 `mvn package -Plocal-node` 就用系统里的 Node。

### 3.2 构建

```bash
cd D:/Project/yi-mo
mvn -f backend/pom.xml clean package
```

**产出**：`backend/target/yimo-backend-1.0.0.jar`，约 50-60MB（主要是依赖）。

**验证**：

```bash
java -jar backend/target/yimo-backend-1.0.0.jar
```

浏览器打开 `http://127.0.0.1:18080`，应该看到亿墨的界面（不是 Spring 的 Whitelabel 错误页）。

**如果看到 404**：前端产物没拷进去。检查 `backend/target/classes/static/` 里有没有 `index.html`。

## 4. 自动打开浏览器

```java
package com.yimo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class BrowserLauncher {

    private static final Logger log = LoggerFactory.getLogger(BrowserLauncher.class);

    @Value("${server.port:18080}")
    private int port;

    @Value("${yimo.open-browser:true}")
    private boolean enabled;

    @EventListener(ApplicationReadyEvent.class)
    public void open() {
        if (!enabled) return;

        final String url = "http://127.0.0.1:" + port;

        CompletableFuture.runAsync(() -> {
            try {
                // 事件触发到 Tomcat 真正接受连接之间有个极短的窗口
                TimeUnit.MILLISECONDS.sleep(800);
                if (openBrowser(url)) {
                    log.info("已在浏览器中打开 {}", url);
                } else {
                    log.info("未能自动打开浏览器，请手动访问 {}", url);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private boolean openBrowser(String url) {
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", url);
            } else {
                pb = new ProcessBuilder("xdg-open", url);
            }
            pb.redirectErrorStream(true);
            pb.start();
            return true;
        } catch (IOException e) {
            log.debug("调用系统命令打开浏览器失败", e);
            return false;
        }
    }
}
```

配置里加：

```yaml
yimo:
  open-browser: true      # 无头环境（服务器、Docker）设为 false
```

**为什么用 `ApplicationReadyEvent` 而不是 `ApplicationRunner`**：前者在 Web 容器已就绪后才触发，后者在容器启动过程中就跑了。

**为什么不用 `java.awt.Desktop.browse()`**：它依赖 AWT，在某些 JDK 构建和 Linux 发行版上会抛 `HeadlessException` 或 `UnsupportedOperationException`。直接调系统命令更可靠。

## 5. 启动器脚本

### 5.1 Windows

`scripts/启动亿墨.bat`

```bat
@echo off
chcp 65001 >nul
title 亿墨 YI-MO

set JAR=%~dp0yimo.jar
set LOG=%USERPROFILE%\.yimo\logs\yimo.log

echo.
echo   亿墨 YI-MO
echo   ========================================
echo.

if not exist "%JAR%" (
    echo   [错误] 找不到 yimo.jar
    echo          请确认它和本脚本在同一个文件夹里
    echo.
    pause
    exit /b 1
)

where java >nul 2>nul
if errorlevel 1 (
    echo   [错误] 未检测到 Java 运行环境
    echo.
    echo   亿墨需要 Java 21，请先安装：
    echo   https://adoptium.net/
    echo.
    echo   安装时请勾选 "Set JAVA_HOME variable"
    echo.
    pause
    exit /b 1
)

echo   [信息] 正在启动服务，首次启动需要十几秒...
echo.

start "" javaw -jar "%JAR%"

rem 轮询等待端口就绪
set /a tries=0
:wait
timeout /t 1 >nul
set /a tries+=1
netstat -ano | findstr ":18080" | findstr "LISTENING" >nul
if not errorlevel 1 goto ready
if %tries% lss 30 goto wait

echo   [错误] 启动超时（30 秒）
echo.
echo   可能的原因：
echo     1. 18080 端口被其他程序占用
echo     2. MySQL 未启动或连接配置错误
echo     3. Java 版本不是 21
echo.
echo   详细日志：%LOG%
echo.
pause
exit /b 1

:ready
echo   [完成] 浏览器即将自动打开
echo.
echo   如果没打开，手动访问：http://127.0.0.1:18080
echo.
echo   关闭服务：在任务管理器里结束 Java 进程
echo.
timeout /t 8 >nul
```

**`chcp 65001` 必须加**，否则中文全是乱码。

**用 `javaw` 不是 `java`**：前者不弹控制台窗口，用户看到的是干净的启动过程。

**代价是看不到日志**，所以：

1. 配置里把日志写到文件
2. 启动脚本轮询端口，失败时告诉用户去看日志

`application.yml`：

```yaml
logging:
  file:
    name: ${user.home}/.yimo/logs/yimo.log
  logback:
    rollingpolicy:
      max-file-size: 10MB
      max-history: 5
```

### 5.2 macOS / Linux

`scripts/start.sh`

```bash
#!/usr/bin/env bash
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$DIR/yimo.jar"
LOG="$HOME/.yimo/logs/yimo.log"
URL="http://127.0.0.1:18080"

if [ ! -f "$JAR" ]; then
  echo "[错误] 找不到 yimo.jar"
  exit 1
fi

if ! command -v java >/dev/null 2>&1; then
  echo "[错误] 未检测到 Java，请先安装 JDK 21：https://adoptium.net/"
  exit 1
fi

echo "正在启动亿墨，首次启动需要十几秒..."
nohup java -jar "$JAR" > "$LOG" 2>&1 &
PID=$!

for i in $(seq 1 30); do
  sleep 1
  if curl -sf "$URL/api/ping" >/dev/null 2>&1; then
    echo "服务已就绪：$URL"
    echo "进程号：$PID（结束服务：kill $PID）"
    exit 0
  fi
done

echo "[错误] 启动超时，请查看日志：$LOG"
tail -n 30 "$LOG"
exit 1
```

记得 `chmod +x scripts/start.sh`。

## 6. 打成原生可执行文件（可选，M6 再做）

Java 14+ 自带 `jpackage`，能把 JRE 和 jar 一起打包成 exe / dmg / deb。

```bash
jpackage \
  --type app-image \
  --name "YIMO" \
  --input backend/target \
  --main-jar yimo-backend-1.0.0.jar \
  --main-class org.springframework.boot.loader.launch.JarLauncher \
  --app-version 1.0.0 \
  --dest dist
```

**几个要点**：

| 项 | 说明 |
|---|---|
| `--main-class` | Spring Boot 的 fat jar 必须用 `JarLauncher`，不能用主类 |
| `--type app-image` | 产出免安装目录。要安装包用 `msi`（Windows）/ `dmg`（Mac）/ `deb`（Linux） |
| `--icon` | Windows 要 `.ico`，Mac 要 `.icns` |
| 体积 | 自带 JRE，约 150MB |
| Windows 打 msi | 需要装 WiX Toolset |

**体积换门槛的取舍**：

| 方案 | 包大小 | 用户要装什么 |
|---|---|---|
| jar + 脚本 | ~60MB | JDK 21（约 200MB 下载） |
| jpackage | ~150MB | 什么都不用装 |

**第一版用脚本方案**，验证了产品形态再上 jpackage。对一个还没人用的项目，先解决"能不能用"而不是"装起来方不方便"。

## 7. 缓存策略

亿墨需要缓存的地方只有三处。**没有第四处，所以不需要 Redis**（论证见 [技术选型](05-tech-stack.md)）。

### 7.1 AI 结果缓存（最重要，直接省钱）

这是唯一有边际成本的操作，缓存必须做在最前面，且**必须持久化**——放内存里重启就没了，重新调用是真金白银。

```sql
CREATE TABLE ai_cache (
  cache_key     CHAR(64)    NOT NULL PRIMARY KEY COMMENT 'sha256(kind|model|promptVersion|input)',
  kind          VARCHAR(30) NOT NULL,
  model         VARCHAR(100),
  prompt_version VARCHAR(20),
  result        JSON,
  input_tokens  INT DEFAULT 0,
  output_tokens INT DEFAULT 0,
  hit_count     INT DEFAULT 0,
  created_at    DATETIME,
  expires_at    DATETIME,
  KEY idx_kind (kind),
  KEY idx_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

```java
@Component
public class AiCacheService {

    private final AiCacheMapper mapper;

    public Optional<String> lookup(TaskKind kind, String model, String input) {
        String key = buildKey(kind, model, input);
        AiCache c = mapper.selectById(key);
        if (c == null || c.getExpiresAt().isBefore(LocalDateTime.now())) {
            return Optional.empty();
        }
        mapper.incrementHit(key);      // 统计命中率，用来估算省了多少钱
        return Optional.of(c.getResult());
    }

    public void store(TaskKind kind, String model, String input, String result, Usage usage) {
        AiCache c = new AiCache();
        c.setCacheKey(buildKey(kind, model, input));
        c.setKind(kind.name());
        c.setModel(model);
        c.setPromptVersion(PromptVersion.CURRENT);
        c.setResult(result);
        c.setInputTokens(usage.input());
        c.setOutputTokens(usage.output());
        c.setCreatedAt(LocalDateTime.now());
        c.setExpiresAt(LocalDateTime.now().plusDays(30));
        mapper.insertOrUpdate(c);
    }

    private String buildKey(TaskKind kind, String model, String input) {
        // 提示词版本必须进 key，否则改了提示词之后用户拿到的还是旧结果
        return DigestUtil.sha256Hex(
            kind.name() + "|" + model + "|" + PromptVersion.CURRENT + "|" + input);
    }
}
```

**`promptVersion` 进 cache key 是最容易漏掉的一点。** 你改了提示词、优化了校对效果，用户却因为命中缓存拿到旧结果，会以为改动没生效，白折腾半天。

**提示词版本号集中管理**：

```java
public final class PromptVersion {
    /** 每次修改 src/main/resources/prompts/ 下的任何文件，这个数字 +1 */
    public static final String CURRENT = "3";
}
```

**过期时间按任务类型区分**：

| 任务 | TTL | 理由 |
|---|---|---|
| 校对 | 30 天 | 内容不变则结果不变 |
| 摘要 | 永久 | 由内容唯一决定 |
| 一致性检查 | 7 天 | 依赖全局状态，会因别的章节改动而失效 |
| 对话 | 不缓存 | 交互性质，缓存没有意义 |

**设置页要显示命中率和估算节省**。让用户看到缓存真的在省钱，这是功能可信度的一部分。

### 7.2 静态资源缓存

**配反了会导致用户更新后看到旧版本**，是最常见的部署事故。

```java
@Configuration
public class WebCacheConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Vite 构建出的带 hash 的资源，可以永久缓存
        registry.addResourceHandler("/assets/**")
            .addResourceLocations("classpath:/static/assets/")
            .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS)
                .cachePublic().immutable());

        // 入口文件永不缓存，否则用户拿不到新版本
        registry.addResourceHandler("/index.html")
            .addResourceLocations("classpath:/static/index.html")
            .setCacheControl(CacheControl.noCache());
    }
}
```

| 文件 | 缓存头 | 理由 |
|---|---|---|
| `/assets/app-a1b2c3.js` | `max-age=31536000, immutable` | 文件名带内容哈希，内容变了文件名就变 |
| `/index.html` | `no-cache` | 它引用了哪些资源，必须每次检查 |

`immutable` 告诉浏览器：这个 URL 的内容永远不会变，连条件请求都不用发。Vite 默认给产物加内容哈希，所以这个假设是成立的。

### 7.3 SPA 路由回退

用户直接访问 `http://127.0.0.1:18080/books/123` 或在页面里刷新，后端会找不到这个路径。要回退到 `index.html` 让前端路由处理。

```java
@Controller
public class SpaForwardController {

    /** 匹配所有不含点的路径（含点的交给静态资源处理器） */
    @RequestMapping(value = {"/", "/{path:[^\\.]*}", "/{path:^(?!api$).*}/**"})
    public String forward() {
        return "forward:/index.html";
    }
}
```

**`/api/**` 必须排除在外**，否则接口 404 时会返回一个 HTML 页面，前端解析 JSON 失败，报出的错和真实原因完全无关，排查起来很痛苦。

### 7.4 文件扫描的增量更新

不做内存缓存，靠数据库里存的 `content_hash` 和文件 `mtime` 比对。

```java
private boolean needsReindex(Path file, Chapter existing) throws IOException {
    if (existing == null) return true;

    FileTime mtime = Files.getLastModifiedTime(file);
    if (existing.getFileMtime() != null
        && existing.getFileMtime().toInstant().equals(mtime.toInstant())) {
        return false;      // 文件没动过，跳过
    }

    // mtime 变了，再比内容哈希确认是不是真的改了
    String raw = Files.readString(file, StandardCharsets.UTF_8);
    return !existing.getContentHash().equals(HashUtil.sha256Hex(raw));
}
```

**为什么要比对两次**：`mtime` 会因为打开保存、复制粘贴、Git 切换分支等原因变化，但内容可能没变。第一次比对挡掉大部分，第二次比对挡掉剩下的误报。

**100 万字的全量扫描约 30 秒，增量扫描是毫秒级。** 这个差别决定了用户每次打开亿墨是等半分钟还是立刻就绪。

## 8. 发布清单

`scripts/release.sh`

```bash
#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."

VERSION=$(cat VERSION)
echo "构建 v$VERSION"

# 1. 检查工作区干净
if [ -n "$(git status --porcelain)" ]; then
  echo "[错误] 工作区有未提交的改动"
  exit 1
fi

# 2. 跑测试
(cd backend && mvn test)
(cd frontend && npm run type-check && npm run test:unit)

# 3. 构建
mvn -f backend/pom.xml clean package

# 4. 组装发布目录
DIST="dist/v$VERSION"
rm -rf "$DIST"
mkdir -p "$DIST"
cp backend/target/yimo-backend-$VERSION.jar "$DIST/yimo.jar"
cp scripts/启动亿墨.bat "$DIST/"
cp scripts/start.sh "$DIST/"
chmod +x "$DIST/start.sh"
cp README.md LICENSE "$DIST/"

# 5. 打 zip
(cd dist && zip -r "yimo-v$VERSION.zip" "v$VERSION")

echo "完成：dist/yimo-v$VERSION.zip"
```

**第 1 步检查工作区干净很重要**——从有未提交改动的状态打出来的包，和仓库里记录的版本对不上，出了问题查不出来。

**发布目录里必须有 README 和 LICENSE**。用户拿到 zip 解压后第一件事就是想知道这是什么、能干什么。

## 9. 数据库：发布版的障碍

**「双击即用」和「先装 MySQL」是矛盾的。**

开发阶段用 MySQL 没问题，自己机器上装一次就行。但发布给用户时，要求他先装 MySQL、建库、建用户、配密码——绝大多数人会卡在这里放弃。

三种解法：

| 方案 | 开发 | 发布 | 代价 |
|---|---|---|---|
| A | MySQL | MySQL + 安装引导 | 用户门槛高，代码只有一套 |
| B | MySQL | SQLite + FTS5 | 门槛低，要写两套全文检索实现 |
| C | MySQL | H2 内嵌 | 门槛低，但 H2 2.x 移除了内置全文检索，等于要自己实现全文索引 |

**推荐 B**。SQLite 是单文件、零配置，`sqlite-jdbc` 很成熟，FTS5 是内置能力。

差异通过接口隔离：

```java
public interface SearchProvider {
    List<SearchHit> search(String libraryId, String query, int limit);
}

@Service
@ConditionalOnProperty(name = "yimo.database", havingValue = "mysql")
public class MysqlSearchProvider implements SearchProvider { /* ngram */ }

@Service
@ConditionalOnProperty(name = "yimo.database", havingValue = "sqlite", matchIfMissing = true)
public class SqliteSearchProvider implements SearchProvider { /* FTS5 */ }
```

SQLite 的中文分词处理：FTS5 的 `unicode61` 对中文按单字切分，搜「陈平安」变成「陈 AND 平 AND 安」，召回高、精度低。检索后再用 `indexOf` 在结果里精确验证一遍即可。

**这个决定可以推到 M6**。但要知道它的存在——如果一开始就把 MySQL 的 ngram 用得太深（比如依赖 `MATCH` 的相关度打分），迁移时会很痛。**保持 `SearchProvider` 接口足够窄**，只暴露"给查询词，返回命中的章节和片段"，不要把数据库特性漏到业务层。
