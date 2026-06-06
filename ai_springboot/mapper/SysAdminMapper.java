package com.example.ai_springboot.mapper;

import com.example.ai_springboot.entity.SysAdmin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysAdminMapper {
    // 管理员登录
    @Select("SELECT * FROM sys_admin WHERE username = #{username} AND password = #{password}")
    SysAdmin login(@Param("username") String username, @Param("password") String password);
}