package com.example.ai_springboot.service.impl;

import com.example.ai_springboot.entity.WxUser;
import com.example.ai_springboot.mapper.WxUserMapper;
import com.example.ai_springboot.service.WxUserService;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;

@Service
public class WxUserServiceImpl implements WxUserService {

    @Resource
    private WxUserMapper wxUserMapper;

    @Override
    public WxUser loginOrRegister(String openid, String nickname, String avatarUrl) {
        // 先查询数据库中有无该用户
        WxUser user = wxUserMapper.selectByOpenid(openid);
        if (user == null) {
            // 没有则自动注册新用户
            user = new WxUser();
            user.setOpenid(openid);
            user.setNickname(nickname);
            user.setAvatarUrl(avatarUrl);
            wxUserMapper.insertUser(user);
        }
        return user;
    }

    @Override
    public WxUser getUserInfo(Long id) {
        return wxUserMapper.selectById(id);
    }
}