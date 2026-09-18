package com.yimo.storage;

import java.nio.file.Path;
import java.util.Map;

/**
 * 推断一个 Markdown 文件是什么类型。
 *
 * <p>为什么需要推断：作者的文件夹里可能混着章节、人物卡、大纲、随手写的笔记。
 * 亿墨要能分清哪些该进章节树、哪些该进知识库、哪些不用管。
 *
 * <p>判断顺序（优先级从高到低）：
 * <ol>
 *   <li>frontmatter 里的 {@code yimo} 字段——最权威，作者明确写了</li>
 *   <li>所在目录名——"07-正文" 里的都是章节，"03-人物" 里的都是人物卡</li>
 *   <li>文件名——含「大纲」的当大纲</li>
 *   <li>兜底当普通笔记，不参与索引</li>
 * </ol>
 */
public final class TypeInferrer {

    private TypeInferrer() {
    }

    /**
     * 推断文件类型。
     *
     * @param file       文件的绝对路径
     * @param frontmatter 已解析出的元数据，可能为空 Map
     * @param bookRoot   这本书的根目录
     */
    public static DocType infer(Path file, Map<String, Object> frontmatter, Path bookRoot) {
        // ===== 1. frontmatter 的 yimo 字段最权威 =====
        Object yimo = frontmatter.get("yimo");
        if (yimo != null) {
            DocType fromField = parseYimoField(yimo.toString());
            if (fromField != null) {
                return fromField;
            }
        }

        // 算出文件相对于书库根的路径，用来判断目录
        Path relative = bookRoot.relativize(file);

        // ===== 2. 看路径上的每一层目录名 =====

        // 从文件名所在的目录开始，一层层往上找。
        //
        // 为什么不能只看第一层：书库结构是「书库根/书名/07-正文/章节.md」，
        // 相对路径的第一层是书名（剑来），第二层才是类型目录（07-正文）。
        // 只看第一层的话，一个章节都识别不出来——扫描结果会是
        // 「找到 3 个文件，索引 0 个章节」，而且不报任何错。
        //
        // 从最深层往上找，让靠内的目录优先：
        // 「书库/正文/人物/xxx.md」里的文件按「人物」算，不是「正文」
        Path current = file.getParent();
        Path stopAt = bookRoot.toAbsolutePath().normalize();

        while (current != null) {
            String dirName = stripNumberPrefix(current.getFileName().toString());

            DocType fromDir = matchDirectory(dirName);
            if (fromDir != null) {
                return fromDir;
            }

            // 找到书库根就停，不要一路找到盘符根目录去
            if (current.toAbsolutePath().normalize().equals(stopAt)) {
                break;
            }
            current = current.getParent();
        }

        // ===== 3. 看文件名 =====
        String fileName = file.getFileName().toString();
        if (fileName.contains("大纲")) {
            return DocType.OUTLINE;
        }

        // ===== 4. 兜底 =====
        return DocType.NOTE;
    }

    /** 解析 frontmatter 里的 yimo 值 */
    private static DocType parseYimoField(String value) {
        return switch (value.trim().toLowerCase()) {
            case "chapter" -> DocType.CHAPTER;
            case "outline" -> DocType.OUTLINE;
            case "note" -> DocType.NOTE;
            case "entity" -> DocType.ENTITY_TERM;   // 没写具体类型时当术语
            default -> null;                        // 不认识的值，交给后面的规则判断
        };
    }

    /** 按目录名匹配类型。目录名已经剥掉了编号前缀 */
    private static DocType matchDirectory(String dirName) {
        if (containsAny(dirName, "正文", "章节", "chapter")) {
            return DocType.CHAPTER;
        }
        if (containsAny(dirName, "人物", "角色", "character")) {
            return DocType.ENTITY_CHARACTER;
        }
        if (containsAny(dirName, "地点", "location")) {
            return DocType.ENTITY_LOCATION;
        }
        if (containsAny(dirName, "物品", "道具", "item")) {
            return DocType.ENTITY_ITEM;
        }
        if (containsAny(dirName, "组织", "势力", "org")) {
            return DocType.ENTITY_ORG;
        }
        return null;
    }

    /**
     * 剥掉目录名的编号前缀。
     *
     * <p>"07-正文" → "正文"，"01__人物" → "人物"，"3. 地点" → "地点"。
     * 为什么要剥：作者可能给目录加编号来控制排序，
     * 但加不加编号不影响它是什么目录。
     */
    private static String stripNumberPrefix(String dirName) {
        return dirName.replaceFirst("^\\d+[-_.\\s]*", "");
    }

    private static boolean containsAny(String text, String... keywords) {
        String lower = text.toLowerCase();
        for (String kw : keywords) {
            if (lower.contains(kw.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
