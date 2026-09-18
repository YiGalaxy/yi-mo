package com.yimo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yimo.common.BizException;
import com.yimo.common.ErrorCode;
import com.yimo.common.Ids;
import com.yimo.domain.Library;
import com.yimo.dto.LibraryCreateRequest;
import com.yimo.dto.LibraryView;
import com.yimo.mapper.LibraryMapper;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class LibraryService {
    private final LibraryMapper libraryMapper;

    public LibraryService(LibraryMapper libraryMapper) {
        this.libraryMapper = libraryMapper;
    }

    public LibraryView create(LibraryCreateRequest req) {
        Path path = Path.of(req.path()).toAbsolutePath().normalize();

        if(!Files.isDirectory(path)){
            throw new BizException(ErrorCode.LIBRARY_PATH_INVALID,"目录不存在: "+ path.toString());
        }
        // 注意是 isReadable，不是 isRegularFile。
        // isRegularFile 判断的是「是不是普通文件」，对目录永远返回 false，
        // 写成 !isRegularFile 会导致每次添加书库都误报「目录不可读」
        if(!Files.isReadable(path)){
            throw new BizException(ErrorCode.LIBRARY_PATH_INVALID,"目录不可读: " + path);
        }
        Long exists =libraryMapper.selectCount(
                new LambdaQueryWrapper<Library>().eq(Library::getPath,path.toString())
        );
        if(exists > 0){
            throw new BizException(ErrorCode.LIBRARY_PATH_DUPLICATE);
        }

        Library lib = new Library();
        lib.setId(Ids.library());
        lib.setPath(path.toString());
        lib.setName(req.name() != null && !req.name().isBlank() ? req.name() : path.getFileName().toString());
        lib.setCreatedAt(LocalDateTime.now());
        libraryMapper.insert(lib);

        return  toView(lib);
    }

    public List<LibraryView> list(){
        return libraryMapper.selectList(
                new LambdaQueryWrapper<Library>().orderByDesc(Library::getLastOpened)).stream().map(this::toView).toList();

    }

    public void remove(String id){
        Library lib = libraryMapper.selectById(id);
        if (lib == null) {
            throw new BizException(ErrorCode.LIBRARY_NOT_FOUND);
        }
        libraryMapper.deleteById(id);
    }

    private LibraryView toView(Library lib){
        return  new LibraryView(
                lib.getId(),
                lib.getName(),
                lib.getPath(),
                0,
                0,
                lib.getLastOpened() != null ? lib.getLastOpened().atZone(ZoneId.systemDefault()).toOffsetDateTime() : null,
                lib.getCreatedAt() != null ? lib.getCreatedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime() : null);
    }
}
