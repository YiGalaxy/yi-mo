package com.yimo.storage;

import com.yimo.common.BizException;
import com.yimo.common.ErrorCode;
import com.yimo.domain.Library;
import com.yimo.mapper.LibraryMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 书库文件的读写。
 *
 * <p>所有碰磁盘的操作都从这里走，别的地方不直接调 Files。
 * 这样「指定 UTF-8」「处理异常」「校验路径」这些事只有一份实现，
 * 改的时候不会漏掉某处。
 */
@Component
public class LibraryStorage {

    private final LibraryMapper libraryMapper;

    public LibraryStorage(LibraryMapper libraryMapper) {
        this.libraryMapper = libraryMapper;
    }

    /** 取书库的根目录 */
    public Path rootOf(String libraryId) {
        Library lib = libraryMapper.selectById(libraryId);
        if (lib == null) {
            throw new BizException(ErrorCode.LIBRARY_NOT_FOUND);
        }
        return Path.of(lib.getPath());
    }

    /**
     * 读书库里的文件。
     *
     * <p>强制 UTF-8：不指定的话用系统默认编码，Windows 上可能是 GBK，
     * 读 UTF-8 的稿子会全是乱码。
     */
    public String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BizException(ErrorCode.FILE_READ_FAILED, e.getMessage());
        }
    }

    /**
     * 原子写入。
     *
     * <p>先写临时文件再重命名，这是为了防「写到一半断电」——
     * 直接覆盖原文件的话，中途崩溃会留下一个半截的稿子。
     * 重命名是操作系统级的原子操作，要么完成要么没发生。
     */
    public void writeAtomic(Path target, String content) {
        try {
            Path dir = target.getParent();
            Files.createDirectories(dir);

            // 临时文件必须和目标在同一个目录（同一个磁盘分区），
            // 否则 ATOMIC_MOVE 会降级成「复制 + 删除」，就不再是原子的了
            Path tmp = dir.resolve(target.getFileName() + ".tmp");
            Files.writeString(tmp, content, StandardCharsets.UTF_8);

            Files.move(tmp, target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

        } catch (IOException e) {
            throw new BizException(ErrorCode.FILE_WRITE_FAILED, e.getMessage());
        }
    }
}
