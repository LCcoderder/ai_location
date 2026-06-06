package com.example.ai_springboot.entity;

import lombok.Data;
import java.util.Date;

@Data
public class CheckinRecord {
    private Long id;
    private Long userId;
    private Long spotId;
    private String userImageUrl; // 用户上传的照片
    private Integer status; // 0=AI识别中, 1=成功, 2=申诉中, 3=申诉通过, 4=驳回
    private Boolean isAddedToDataset; // 是否加入训练集
    private String postcardUrl; // 明信片地址
    private Date createTime;
    private String spotName;
}
