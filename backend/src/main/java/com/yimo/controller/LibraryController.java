package com.yimo.controller;


import com.yimo.common.BizException;
import com.yimo.common.ErrorCode;
import com.yimo.domain.Library;
import com.yimo.dto.LibraryCreateRequest;
import com.yimo.dto.LibraryView;
import com.yimo.mapper.LibraryMapper;
import com.yimo.service.LibraryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/libraries")
public class LibraryController {

    private final LibraryMapper libraryMapper;
    private final LibraryService libraryService;

    public LibraryController(LibraryMapper libraryMapper, LibraryService libraryService) {
        this.libraryMapper = libraryMapper;
        this.libraryService = libraryService;
    }

    @GetMapping
    public List<Library> list() {
        return libraryMapper.selectList(null);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LibraryView create(@RequestBody @Valid LibraryCreateRequest req) {
        return libraryService.create(req);
    }

    @DeleteMapping("/{id}")
    public Map<String,Object> remove(@PathVariable String id){
        libraryService.remove(id);
        return Map.of("id",id,"removed",true,"fileDeleted",false);
    }

}
