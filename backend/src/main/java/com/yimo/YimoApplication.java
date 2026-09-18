package com.yimo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.yimo.mapper")
public class YimoApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(YimoApplication.class);

        // Spring Boot 默认以 headless 模式启动，这个模式下 AWT/Swing 被禁用，
        // 弹文件夹选择窗口会直接抛 HeadlessException。
        // 关掉它才能用 DirectoryPickerService。
        //
        // 没有图形界面的环境（Docker、无头服务器）不受影响：
        // 里面的代码会先检测 GraphicsEnvironment.isHeadless()，检测到就降级报错，
        // 不会让服务起不来。
        app.setHeadless(false);

        app.run(args);
    }
}
