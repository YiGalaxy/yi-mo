package com.yimo.dto;

import java.time.OffsetDateTime;

public record LibraryView(String id,
                          String name,
                          String path,
                          int bookCount,
                          long wordCount,
                          OffsetDateTime lastOpenedAt,
                          OffsetDateTime createdAt
){}
