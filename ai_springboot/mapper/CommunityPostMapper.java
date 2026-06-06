package com.example.ai_springboot.mapper;

import com.example.ai_springboot.entity.CommunityPost;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CommunityPostMapper {
    // 发布新帖子


    // 查询精选帖子（推送给小程序首页）
    @Select("SELECT * FROM community_post WHERE is_featured = 1 AND status = 1 ORDER BY create_time DESC LIMIT 10")
    List<CommunityPost> selectFeaturedPosts();

    // 查询所有正常帖子（社区列表页展示）
    @Select("SELECT * FROM community_post WHERE status = 1 ORDER BY create_time DESC")
    List<CommunityPost> selectAllNormalPosts();

    // 管理员设为精选
    @Update("UPDATE community_post SET is_featured = 1 WHERE id = #{id}")
    int setFeatured(Long id);
    @Select("SELECT * FROM community_post WHERE user_id = #{userId} ORDER BY create_time DESC")
     List<CommunityPost> selectByUserId(Long userId);


    @Insert("INSERT INTO community_post(user_id, spot_id, content, images_json, is_featured, status, create_time) " +
            "VALUES(#{userId}, #{spotId}, #{content}, #{imagesJson}, #{isFeatured}, 1, NOW())")
    int insertPost(CommunityPost post);
    @Select("SELECT * FROM community_post WHERE id = #{id}")
    CommunityPost selectById(Long id);
    // 🚀 新增：更新已有帖子
    @Update("UPDATE community_post SET user_id=#{userId}, content=#{content}, images_json=#{imagesJson}, is_featured=#{isFeatured} WHERE id=#{id}")
    int updatePost(CommunityPost post);

    // 🚀 新增：删除帖子
    @Delete("DELETE FROM community_post WHERE id = #{id}")
    int deleteById(Long id);
}