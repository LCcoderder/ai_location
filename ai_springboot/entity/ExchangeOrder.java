package com.example.ai_springboot.entity;

import lombok.Data;
import java.util.Date;

@Data
public class ExchangeOrder {
    private Long id;
    private String orderNo;
    private Long userId;
    private Long itemId;
    private Integer costPoints;
    private String shippingInfo;
    private Integer status; // 0=待发货, 1=已完成
    private Date createTime;
}