package com.yimo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yimo.domain.Chapter;
import org.apache.ibatis.annotations.Mapper;

/**
 * 章节的数据访问接口。
 *
 * <p>不用写实现——MyBatis 运行时用动态代理生成。
 * 继承 BaseMapper 之后自带 insert / updateById / selectById /
 * selectList / selectCount / insertOrUpdate 等常用方法。
 */
@Mapper
public interface ChapterMapper extends BaseMapper<Chapter> {
}
