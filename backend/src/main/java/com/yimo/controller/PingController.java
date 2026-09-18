package com.yimo.controller;

import com.yimo.common.Ids;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查。用来验证服务是否正常启动。
 */
@RestController
@RequestMapping("/api/ping")
public class PingController {

    @GetMapping
    public Map<String, Object> ping() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("service", "yimo");
        r.put("time", OffsetDateTime.now().toString());
        r.put("sampleId", Ids.chapter());
        return r;
    }
}
