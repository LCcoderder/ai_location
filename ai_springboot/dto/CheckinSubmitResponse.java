package com.example.ai_springboot.dto;

import lombok.Data;

@Data
public class CheckinSubmitResponse {
    private Long recordId;
    private Integer status;
    private String message;

    public CheckinSubmitResponse(Long recordId, Integer status, String message) {
        this.recordId = recordId;
        this.status = status;
        this.message = message;
    }
}
