package com.yimo.dto;

import java.util.List;

/**
 * 树里的一卷。
 *
 * @param name 卷名。空串表示「未分卷」
 */
public record VolumeNode(String name, List<ChapterBrief> chapters) {
}
