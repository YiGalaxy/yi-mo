package com.yimo.controller;


import com.yimo.common.BizException;
import com.yimo.common.ErrorCode;
import com.yimo.domain.Library;
import com.yimo.mapper.LibraryMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequestMapping("/api/libraries")
public class LibraryController {

    private final LibraryMapper libraryMapper;

    public LibraryController(LibraryMapper libraryMapper) {
        this.libraryMapper = libraryMapper;
    }

    @GetMapping
    public List<Library> list() {
        return libraryMapper.selectList(null);
    }

}
