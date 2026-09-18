package com.yimo.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 章节。
 *
 * <p>注意这里存的只是「索引信息」——标题、序号、卷名、字数这些用来
 * 显示章节树的数据。**正文不在这里**，正文永远在磁盘的 .md 文件里。
 *
 * <p>这样设计的好处：数据库丢了可以从文件重建，而文件丢了才是真丢了。
 */
@Data
@TableName("chapter")
public class Chapter {

    /** 由代码生成的 ULID（不含 ch_ 前缀），不是数据库自增 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 属于哪个书库 */
    private String libraryId;

    /** 书名，取自书库根下的第一层目录名 */
    private String bookName;

    /** 相对书库根的路径，统一用正斜杠 */
    private String relPath;

    // ===== 下面这些来自 frontmatter，可能为空 =====

    private String title;
    private String volume;

    /** 卷内序号，用于排序 */
    private Integer sortOrder;

    /** draft / revising / done */
    private String status;

    private Integer wordCount;

    /**
     * 正文的 SHA-256 哈希。
     *
     * <p>两个用途：重新扫描时比对，内容没变就跳过解析；
     * 保存正文时检测文件是否被外部程序改过。
     */
    private String contentHash;

    /** 视角人物 */
    private String pov;

    /** 故事内时间，作者自由填写 */
    private String storyTime;

    private LocalDateTime updatedAt;
}
