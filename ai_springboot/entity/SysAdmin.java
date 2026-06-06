package com.example.ai_springboot.entity;

import lombok.Data;
import java.util.Date;

@Data
public class SysAdmin {
    private Integer id;
    private String username;
    private String password;
    private String role;
    private Date createTime;
}