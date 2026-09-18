package com.yimo;

import com.yimo.common.BizException;
import com.yimo.common.Ids;
import com.yimo.storage.PathGuard;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;


/**
 * 上下文加载测试。能跑起来说明 Spring 配置、数据库连接、Bean 装配都没问题。
 */
@SpringBootTest
class YimoApplicationTests {

    @Test
    void contextLoads() {
    }
    @Test
    void ulidIsMonotonic() {


        String a = Ids.chapter();
        String b = Ids.chapter();
        assertThat(a).startsWith("ch_").hasSize(29);
        assertThat(a.compareTo(b)).isLessThan(0);   // 单调递增，可按创建时间排序
    }
    @Test
    void blocksPathTraversal() {
        Path root = Path.of("D:/Writing/小说");

        assertThatThrownBy(() -> PathGuard.resolve(root, "../../Windows/System32/config"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("路径越界");

        assertThat(PathGuard.resolve(root, "剑来/07-正文/第001章.md"))
                .isEqualTo(Path.of("D:/Writing/小说/剑来/07-正文/第001章.md"));
    }
}
