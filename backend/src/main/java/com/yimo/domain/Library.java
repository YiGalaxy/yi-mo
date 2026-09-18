package com.yimo.domain;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;


/**
 * 这里的注解是在告诉Mybatis-Plus框架，在操作Library这个类的时候，实际是去操作数据库里名为library的那张表
 * 如果不加的话，那Mybatis-Plus会自己识别类这个名字来匹配数据库里面的表，如果表不叫library的话，就会报错找不到
 */
@TableName("library")
public class Library {

    /**
     * 这里这个注解就是告诉这个这个主键的设置方式，是自己给，还是自增之类的
     */
    @TableId(type = IdType.INPUT)
    private String id;

    private String name;
    private String path;
    private LocalDateTime lastOpened;
    private LocalDateTime createdAt;

    // ===== getter / setter =====

    // 这两个不能省，否则会出现「接口返回 200 但内容是 [{}]」这种诡异现象：
    //   - Jackson 序列化成 JSON 时只认 getter，没 getter 的字段根本不会出现在响应里
    //   - MyBatis 从数据库读出结果时用 setter 写进对象
    //
    // 在 IDEA 里不用手敲：光标放在类里，按 Alt + Insert → Getter and Setter

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public LocalDateTime getLastOpened() {
        return lastOpened;
    }

    public void setLastOpened(LocalDateTime lastOpened) {
        this.lastOpened = lastOpened;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
