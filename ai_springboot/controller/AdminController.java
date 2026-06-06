package com.example.ai_springboot.controller;

import com.example.ai_springboot.common.Result;
import com.example.ai_springboot.entity.ExchangeOrder;
import com.example.ai_springboot.entity.MallItem;
import com.example.ai_springboot.entity.ScenicSpot;
import com.example.ai_springboot.entity.CommunityPost;
import com.example.ai_springboot.mapper.ExchangeOrderMapper;
import com.example.ai_springboot.mapper.MallItemMapper;
import com.example.ai_springboot.mapper.ScenicSpotMapper;
import com.example.ai_springboot.mapper.WxUserMapper;
import com.example.ai_springboot.mapper.CommunityPostMapper;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Resource
    private ScenicSpotMapper scenicSpotMapper;
    @Resource
    private MallItemMapper mallItemMapper;
    @Resource
    private WxUserMapper wxUserMapper;
    @Resource
    private ExchangeOrderMapper exchangeOrderMapper;
    @Resource
    private CommunityPostMapper communityPostMapper;

    // ================== 订单发货 ==================
    @GetMapping("/orders")
    public Result<List<ExchangeOrder>> getAllOrders() {
        return Result.success(exchangeOrderMapper.selectAll());
    }

    @PostMapping("/order/ship")
    public Result<?> shipOrder(Long orderId) {
        exchangeOrderMapper.updateStatus(orderId, 1); // 1 = 已发货
        return Result.success("发货成功");
    }


    // ================== 增改保存 ==================
    @PostMapping("/spot/save")
    public Result<?> saveSpot(@RequestBody ScenicSpot spot) {
        if (spot.getId() == null) {
            scenicSpotMapper.insertSpot(spot);
        } else {
            scenicSpotMapper.updateSpot(spot);
        }
        return Result.success("景点保存成功");
    }
    @PostMapping("/post/save")
    public Result<?> savePost(@RequestBody CommunityPost post) {
        if (post.getId() == null) {
            communityPostMapper.insertPost(post);
        } else {
            communityPostMapper.updatePost(post);
        }
        return Result.success("帖子保存成功");
    }

    // ================== 删除操作 ==================
    @PostMapping("/delete")
    public Result<?> deleteData(String type, Long id) {
        if ("spot".equals(type)) scenicSpotMapper.deleteById(id);
        if ("mall".equals(type)) mallItemMapper.deleteById(id);
        // 🚀 新增：支持帖子的删除
        if ("post".equals(type)) communityPostMapper.deleteById(id);

        return Result.success("删除成功");
    }
    @PostMapping("/mall/save")
    public Result<?> saveMallItem(@RequestBody MallItem item) {
        if (item.getId() == null) {
            mallItemMapper.insertItem(item);
        } else {
            mallItemMapper.updateItem(item);
        }
        return Result.success("商品保存成功");
    }


    // ================== 用户风控 ==================
    @PostMapping("/user/adjustPoints")
    public Result<?> adjustPoints(Long userId, Integer points) {
        wxUserMapper.updatePoints(userId, points);
        return Result.success("积分调整成功");
    }

    @PostMapping("/user/ban")
    public Result<?> banUser(Long userId) {
        wxUserMapper.deleteById(userId); // 直接删号处理
        return Result.success("账号冻结成功");
    }
}