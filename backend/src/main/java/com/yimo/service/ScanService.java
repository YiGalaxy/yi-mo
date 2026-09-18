package com.yimo.service;

import cn.hutool.crypto.digest.DigestUtil;
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
        ch.setContentHash(DigestUtil.sha256Hex(pm.body()));
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