package com.yimo.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 书库。一个书库 = 磁盘上的一个文件夹，里面装着若干本书。
 *
 * <p>这里的注解是在告诉 MyBatis-Plus 框架，在操作 Library 这个类的时候，
 * 实际是去操作数据库里名为 library 的那张表。
 * 如果不加的话，MyBatis-Plus 会自己用类名去匹配表名，对不上就会报错找不到。
 */
@Data
@TableName("library")
public class Library {

    /**
     * 这个注解告诉框架主键的生成方式：是自己给，还是数据库自增。
     * INPUT 表示由我们自己的代码赋值（这里用 ULID）。
     */
    @TableId(type = IdType.INPUT)
    private String id;

    private String name;
    private String path;
    private LocalDateTime lastOpened;
    private LocalDateTime createdAt;

    // @Data 是 Lombok 的注解，编译时自动生成：
    //   getter / setter —— Jackson 序列化读 getter，MyBatis 写回用 setter，两个都不能少
    //   toString / equals / hashCode
    //
    // 想确认它真的生成了：IDEA 里 Build → Recompile，然后
    // View → Show Bytecode，能看到 getter 的字节码
}
