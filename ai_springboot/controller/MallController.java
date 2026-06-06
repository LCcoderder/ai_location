package com.example.ai_springboot.controller;

import com.example.ai_springboot.common.Result;
import com.example.ai_springboot.entity.MallItem;
import com.example.ai_springboot.mapper.MallItemMapper;
import com.example.ai_springboot.service.MallService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/mall")
public class MallController {

    @Resource
    private MallService mallService;

    @Resource
    private MallItemMapper mallItemMapper;

    // 小程序端获取可兑换的商品列表
    @GetMapping("/items")
    public Result<List<MallItem>> getAvailableItems() {
        return Result.success(mallItemMapper.selectAvailableItems());
    }

    // 小程序端发起积分兑换
    @PostMapping("/exchange")
    public Result<?> exchangeItem(Long userId, Long itemId, String shippingInfo) {
        return mallService.exchangeItem(userId, itemId, shippingInfo);
    }
}