package com.example.ai_springboot.service;

import com.example.ai_springboot.common.Result;

public interface MallService {
    // 兑换商品
    Result<?> exchangeItem(Long userId, Long itemId, String shippingInfo);
}