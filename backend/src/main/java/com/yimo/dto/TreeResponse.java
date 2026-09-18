package com.yimo.dto;

import java.util.List;

/**
 * 书库的完整卷章结构。
 *
 * <p>三层：书 → 卷 → 章。
 */
public record TreeResponse(List<BookNode> books) {
}
