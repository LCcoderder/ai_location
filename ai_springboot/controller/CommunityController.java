package com.example.ai_springboot.controller;

import com.example.ai_springboot.common.Result;
import com.example.ai_springboot.entity.CommunityPost;
import com.example.ai_springboot.entity.ScenicSpot;
import com.example.ai_springboot.mapper.CommunityPostMapper;
import com.example.ai_springboot.mapper.ScenicSpotMapper;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/public") // 公共展示数据的接口
public class CommunityController {

    @Resource
    private ScenicSpotMapper scenicSpotMapper;
    @Resource
    private CommunityPostMapper communityPostMapper;

    // ================= 景点相关 =================

    // 获取所有景点（小程序首页）
    @GetMapping("/spots")
    public Result<List<ScenicSpot>> getAllSpots() {
        return Result.success(scenicSpotMapper.selectAll());
    }

    // 获取单个景点详情（小程序景点详情页）
    @GetMapping("/spot")
    public Result<ScenicSpot> getSpotDetail(Long id) {
        return Result.success(scenicSpotMapper.selectById(id));
    }

    // ================= 社区帖子相关 =================

    // 获取社区所有正常帖子（小程序社区列表页）
    @GetMapping("/allPosts")
    public Result<List<CommunityPost>> getAllPosts() {
        return Result.success(communityPostMapper.selectAllNormalPosts());
    }

    // 🚀 补回：获取单个帖子详情（小程序帖子详情页，解决 404 报错）
    @GetMapping("/post")
    public Result<CommunityPost> getPostDetail(Long id) {
        return Result.success(communityPostMapper.selectById(id));
    }

    // 获取首页精选攻略游记
    @GetMapping("/featuredPosts")
    public Result<List<CommunityPost>> getFeaturedPosts() {
        return Result.success(communityPostMapper.selectFeaturedPosts());
    }

    // 发布社区帖子（小程序发帖）
    @PostMapping("/publishPost")
    public Result<?> publishPost(@RequestBody CommunityPost post) {
        communityPostMapper.insertPost(post);
        return Result.success("发布成功！等待审核精选~");
    }

    // 管理员设为精选（PC后台用）
    @PostMapping("/admin/setFeatured")
    public Result<?> setFeatured(Long postId) {
        communityPostMapper.setFeatured(postId);
        return Result.success("已成功设为精选，将在小程序首页展示");
    }
}