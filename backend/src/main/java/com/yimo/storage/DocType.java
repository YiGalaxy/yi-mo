package com.yimo.storage;

/**
 * 书库里的文件类型。
 *
 * <p>判断顺序见 {@link TypeInferrer}。
 */
public enum DocType {

    /** 正文章节。会写进 chapter 表，出现在章节树里 */
    CHAPTER,

    /** 人物卡 */
    ENTITY_CHARACTER,

    /** 地点 */
    ENTITY_LOCATION,

    /** 物品 */
    ENTITY_ITEM,

    /** 组织 / 势力 */
    ENTITY_ORG,

    /** 术语 / 设定 */
    ENTITY_TERM,

    /** 大纲 */
    OUTLINE,

    /** 普通笔记。可编辑，但不参与索引 */
    NOTE;

    /** 是不是知识库实体（人物、地点、物品、组织、术语） */
    public boolean isEntity() {
        return this == ENTITY_CHARACTER
                || this == ENTITY_LOCATION
                || this == ENTITY_ITEM
                || this == ENTITY_ORG
                || this == ENTITY_TERM;
    }
}
