package com.example.ai_springboot.controller;

import com.example.ai_springboot.common.Result;
import com.example.ai_springboot.mapper.CheckinRecordMapper;
import com.example.ai_springboot.mapper.ExchangeOrderMapper;
import com.example.ai_springboot.mapper.WxUserMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/stats")
public class StatsController {

    @Resource
    private WxUserMapper wxUserMapper;
    @Resource
    private CheckinRecordMapper checkinRecordMapper;
    @Resource
    private ExchangeOrderMapper exchangeOrderMapper;

    // 获取控制台大盘的 4 个核心真实数据
    @GetMapping("/dashboard")
    public Result<Map<String, Integer>> getDashboardStats() {
        Map<String, Integer> stats = new HashMap<>();

        // 真实调用数据库 COUNT(*)
        stats.put("userCount", wxUserMapper.countAll());
        stats.put("checkinCount", checkinRecordMapper.countAll());
        stats.put("pendingAppeals", checkinRecordMapper.countPending());
        stats.put("orderCount", exchangeOrderMapper.countAll());

        return Result.success(stats);
    }
    // 网页端获取所有用户
    @GetMapping("/users")
    public Result<java.util.List<com.example.ai_springboot.entity.WxUser>> getAllUsers() {
        return Result.success(wxUserMapper.selectAll());
    }

    // 网页端获取所有打卡记录
    @GetMapping("/records")
    public Result<java.util.List<com.example.ai_springboot.entity.CheckinRecord>> getAllRecords() {
        return Result.success(checkinRecordMapper.selectAll());
    }
}