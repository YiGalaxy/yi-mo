package com.yimo.controller;

import com.yimo.dto.TreeResponse;
import com.yimo.service.ScanResult;
import com.yimo.service.ScanService;
import com.yimo.storage.LibraryStorage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

/**
 * 书库结构相关的接口：扫出章节、取卷章树。
 */
@RestController
@RequestMapping("/api/libraries")
public class TreeController {

    private final ScanService scanService;
    private final LibraryStorage storage;

    public TreeController(ScanService scanService, LibraryStorage storage) {
        this.scanService = scanService;
        this.storage = storage;
    }

    /** GET /api/libraries/{id}/tree —— 取整个书库的卷章树 */
    @GetMapping("/{libraryId}/tree")
    public TreeResponse tree(@PathVariable String libraryId) {
        return scanService.buildTree(libraryId);
    }

    /**
     * POST /api/libraries/{id}/rescan —— 重新扫描并重建索引。
     *
     * <p><b>这个接口是同步的</b>，100 万字的书库大约要 30 秒，请求会一直挂着。
     * 本地应用可以接受；真嫌慢的话后面可以改成「返回 taskId + SSE 推进度」，
     * 但那需要任务队列，现在不做。
     */
    @PostMapping("/{libraryId}/rescan")
    public ScanResult rescan(@PathVariable String libraryId) {
        Path root = storage.rootOf(libraryId);
        return scanService.scan(libraryId, root);
    }
}
