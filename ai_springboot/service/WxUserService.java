package com.example.ai_springboot.service;

import com.example.ai_springboot.entity.WxUser;

public interface WxUserService {
    // 微信用户登录或注册
    WxUser loginOrRegister(String openid, String nickname, String avatarUrl);
    // 获取用户详情
    WxUser getUserInfo(Long id);
}