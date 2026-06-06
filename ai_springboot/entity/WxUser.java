package com.example.ai_springboot.entity;

import lombok.Data;
import java.util.Date;

@Data
public class WxUser {
    private Long id;
    private String openid;
    private String nickname;
    private String avatarUrl;
    private Integer pointsBalance;
    private Date createTime;
    private Date updateTime;
}