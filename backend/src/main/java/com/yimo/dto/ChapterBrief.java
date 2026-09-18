package com.yimo.dto;

/**
 * 章节在树上的简要信息。
 *
 * <p>注意这里没有 content —— 树只显示目录，不需要正文。
 * 正文等点开某一章时再单独请求。
 *
 * <p>为什么不直接用 Chapter 实体：entities 里有 contentHash、pov、
 * storyTime 等一堆树上根本用不到的字段。一个书库几百章，
 * 多余的字段会白白撑大响应体。
 *
 * @param pendingReviewCount 待处理的批注数，前端用它显示红色角标
 */
public record ChapterBrief(
        String id,
        String title,
        String relPath,
        Integer order,
        String status,
        int wordCount,
        int pendingReviewCount
) {
}
