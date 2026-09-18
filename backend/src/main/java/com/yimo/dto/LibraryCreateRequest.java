package com.yimo.dto;

import jakarta.validation.constraints.NotBlank;

public record LibraryCreateRequest(
        @NotBlank(message = "路径不能为空")
        String path,
        String name) { }
