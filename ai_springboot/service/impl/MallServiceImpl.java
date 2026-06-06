package com.example.ai_springboot.service.impl;

import com.example.ai_springboot.common.Result;
import com.example.ai_springboot.concurrent.ConcurrencyGuard;
import com.example.ai_springboot.concurrent.LockToken;
import com.example.ai_springboot.entity.ExchangeOrder;
import com.example.ai_springboot.entity.MallItem;
import com.example.ai_springboot.entity.WxUser;
import com.example.ai_springboot.event.DomainEventPublisher;
import com.example.ai_springboot.mapper.ExchangeOrderMapper;
import com.example.ai_springboot.mapper.MallItemMapper;
import com.example.ai_springboot.mapper.WxUserMapper;
import com.example.ai_springboot.service.MallService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
public class MallServiceImpl implements MallService {

    @Resource
    private MallItemMapper mallItemMapper;
    @Resource
    private WxUserMapper wxUserMapper;
    @Resource
    private ExchangeOrderMapper exchangeOrderMapper;
    @Resource
    private ConcurrencyGuard concurrencyGuard;
    @Resource
    private DomainEventPublisher domainEventPublisher;

    @Value("${ai-tour.mall.lock-ttl-seconds:20}")
    private long mallLockTtlSeconds;

    @Value("${ai-tour.mall.duplicate-window-seconds:5}")
    private long duplicateWindowSeconds;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<?> exchangeItem(Long userId, Long itemId, String shippingInfo) {
        String duplicateKey = "duplicate:mall:user:" + userId + ":item:" + itemId;
        if (!concurrencyGuard.tryMark(duplicateKey, Duration.ofSeconds(duplicateWindowSeconds))) {
            return Result.error(429, "兑换请求正在处理中，请不要重复点击");
        }

        String lockKey = "lock:mall:item:" + itemId;
        Optional<LockToken> lock = concurrencyGuard.tryLock(lockKey, Duration.ofSeconds(mallLockTtlSeconds));
        if (!lock.isPresent()) {
            return Result.error(429, "该商品正在被抢兑，请稍后再试");
        }

        try {
            WxUser user = wxUserMapper.selectById(userId);
            if (user == null) return Result.error("用户不存在");

            MallItem item = mallItemMapper.selectById(itemId);
            if (item == null || item.getStock() <= 0) {
                return Result.error("商品不存在或库存不足！");
            }

            if (user.getPointsBalance() < item.getRequiredPoints()) {
                return Result.error("积分不足，兑换失败！需要积分：" + item.getRequiredPoints());
            }

            int updated = wxUserMapper.deductPoints(userId, item.getRequiredPoints());
            if (updated == 0) return Result.error("积分扣除失败！");

            int stockUpdated = mallItemMapper.decrementStock(itemId);
            if (stockUpdated == 0) {
                throw new RuntimeException("手慢了，商品刚刚被兑换完！");
            }

            ExchangeOrder order = new ExchangeOrder();
            order.setOrderNo("EX" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 5));
            order.setUserId(userId);
            order.setItemId(itemId);
            order.setCostPoints(item.getRequiredPoints());
            order.setShippingInfo(shippingInfo);
            exchangeOrderMapper.insertOrder(order);
            domainEventPublisher.publishMallExchangeEvent(order);

            return Result.success("兑换成功！订单号：" + order.getOrderNo());
        } finally {
            concurrencyGuard.unlock(lock.get());
        }
    }
}
