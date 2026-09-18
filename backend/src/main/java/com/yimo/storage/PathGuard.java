package com.yimo.storage;

import com.yimo.common.BizException;
import com.yimo.common.ErrorCode;

import java.nio.file.Path;

/**
 * 路径安全守卫。
 *
 * <p>所有由用户输入拼出来的路径都必须经过这里，防止 {@code ../../} 穿越到书库目录之外。
 * 这是整个项目最不能出错的一处——它守住的是用户磁盘上其他文件的安全。
 */
public final class PathGuard {

    private PathGuard() {
    }

    /**
     * 把相对路径解析成书库内的安全绝对路径。越界直接抛异常。
     *
     * @param root     书库根目录
     * @param relative 相对书库根的路径
     * @return 规范化后的绝对路径
     * @throws BizException 路径越界时
     */
    public static Path resolve(Path root, String relative) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path target = normalizedRoot.resolve(relative).normalize();

        if (!target.startsWith(normalizedRoot)) {
            throw new BizException(ErrorCode.PATH_OUT_OF_BOUNDS, "路径越界: " + relative);
        }
        return target;
    }
}
