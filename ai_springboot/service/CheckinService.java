package com.example.ai_springboot.service;

import com.example.ai_springboot.common.Result;
import org.springframework.web.multipart.MultipartFile;

public interface CheckinService {
    // 处理小程序发来的打卡请求（双重校验核心流）
    Result<?> processCheckin(Long userId, Long spotId, MultipartFile image, Double latitude, Double longitude);

    // 管理员处理用户的AI误判申诉
    Result<?> handleAppeal(Long recordId, Boolean isPass);
}