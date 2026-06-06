package com.example.ai_springboot.entity;

import lombok.Data;
import java.util.Date;

@Data
public class CommunityPost {
    private Long id;
    private Long userId;
    private Long spotId;
    private String content;
    private String imagesJson; // JSON格式的图片数组
    private Boolean isFeatured; // 是否精选
    private Integer status; // 0=待审, 1=正常, 2=违规下架
    private Date createTime;
}