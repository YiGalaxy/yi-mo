package com.yimo.controller;

import com.yimo.dto.LibraryCreateRequest;
import com.yimo.dto.LibraryView;
import com.yimo.service.LibraryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/libraries")
public class LibraryController {

    private final LibraryService libraryService;

    public LibraryController(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    /**
     * 走 Service 而不是直接调 Mapper：
     * Service 里做了排序（按最近打开）和实体到 DTO 的转换。
     */
    @GetMapping
    public List<LibraryView> list() {
        return libraryService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LibraryView create(@RequestBody @Valid LibraryCreateRequest req) {
        return libraryService.create(req);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> remove(@PathVariable String id) {
        libraryService.remove(id);
        // 字段名是 filesDeleted（复数），要和 06-api.md 及前端 library.ts 对齐
        return Map.of("id", id, "removed", true, "filesDeleted", false);
    }
}
