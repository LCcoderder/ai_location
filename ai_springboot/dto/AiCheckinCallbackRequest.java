package com.example.ai_springboot.dto;

import lombok.Data;

@Data
public class AiCheckinCallbackRequest {
    private Long recordId;
    private Integer code;
    private Integer status;
    private String msg;
    private String aiPredict;
    private Double distance;
}
