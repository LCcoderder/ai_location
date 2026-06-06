package com.example.ai_springboot.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class ScenicSpot {
    private Long id;
    private String name;
    private String aiLabel; // AI模型的标签
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String description;
    private Integer rewardPoints; // 奖励积分
    private Integer baseHeat; // 基础热度
    private Date createTime;
    private String imageUrl; // 新增：景点封面图
}