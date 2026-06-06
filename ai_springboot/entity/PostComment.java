package com.example.ai_springboot.entity;

import lombok.Data;
import java.util.Date;

@Data
public class PostComment {
    private Long id;
    private Long postId;
    private Long userId;
    private String content;
    private Date createTime;
}