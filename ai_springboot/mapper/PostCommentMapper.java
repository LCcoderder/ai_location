package com.example.ai_springboot.mapper;

import com.example.ai_springboot.entity.PostComment;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PostCommentMapper {
    // 发表评论
    @Insert("INSERT INTO post_comment(post_id, user_id, content, create_time) VALUES(#{postId}, #{userId}, #{content}, NOW())")
    int insertComment(PostComment comment);

    // 获取某个帖子的所有评论
    @Select("SELECT * FROM post_comment WHERE post_id = #{postId} ORDER BY create_time ASC")
    List<PostComment> selectByPostId(Long postId);
}