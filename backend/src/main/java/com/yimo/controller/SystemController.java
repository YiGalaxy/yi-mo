package com.yimo.controller;

import com.yimo.dto.PickDirectoryRequest;
import com.yimo.dto.PickDirectoryResponse;
import com.yimo.service.DirectoryPickerService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 与操作系统交互的接口。
 *
 * <p>这些接口只在本地运行时有意义——亿墨的定位就是本机工具。
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final DirectoryPickerService pickerService;

    public SystemController(DirectoryPickerService pickerService) {
        this.pickerService = pickerService;
    }

    /**
     * 弹出原生文件夹选择窗口。
     *
     * <p>这个请求会阻塞到用户选完或取消，前端要把超时设长或干脆不设超时。
     */
    @PostMapping("/pick-directory")
    public PickDirectoryResponse pickDirectory(
            @RequestBody(required = false) PickDirectoryRequest req) {

        String initialPath = req != null ? req.initialPath() : null;
        Optional<String> picked = pickerService.pickDirectory(initialPath);

        return new PickDirectoryResponse(picked.isPresent(), picked.orElse(null));
    }
}
