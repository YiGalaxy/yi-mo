package com.yimo.dto;

import java.util.List;

/**
 * 树里的一本书。
 *
 * @param relPath    相对书库根的路径，也就是书名的目录名
 * @param volumes    卷列表。没分卷时这里只有一个 name 为空串的「卷」
 * @param totalWords 全书字数，树上显示用
 */
public record BookNode(
        String name,
        String relPath,
        List<VolumeNode> volumes,
        int totalWords,
        int chapterCount
) {
}
