package com.example.ai_springboot.entity;

import lombok.Data;
import java.util.Date;

@Data
public class MallItem {
    private Long id;
    private String name;
    private Integer requiredPoints;
    private Integer stock;
    private String imageUrl;
    private Date createTime;
}