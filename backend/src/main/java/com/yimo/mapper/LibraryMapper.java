package com.yimo.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yimo.domain.Library;
import org.apache.ibatis.annotations.Mapper;


/**
 * 这里这个注解是Mybatis提供的注解，作用是告诉 Spring：这个接口要交给 Spring 管理，可以被注入到其他类中使用。
 */
@Mapper
public interface LibraryMapper extends BaseMapper<Library> {
}
