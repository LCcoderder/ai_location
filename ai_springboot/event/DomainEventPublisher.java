package com.example.ai_springboot.event;

import com.example.ai_springboot.entity.CheckinRecord;
import com.example.ai_springboot.entity.ExchangeOrder;
import com.example.ai_springboot.entity.ScenicSpot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class DomainEventPublisher {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    private KafkaTemplate<String, String> kafkaTemplate;

    @Value("${ai-tour.kafka.enabled:false}")
    private boolean kafkaEnabled;

    @Value("${ai-tour.kafka.topics.checkin:ai-tour-checkin-events}")
    private String checkinTopic;

    @Value("${ai-tour.kafka.topics.ai-checkin-request:ai-tour-ai-checkin-requests}")
    private String aiCheckinRequestTopic;

    @Value("${ai-tour.kafka.topics.mall:ai-tour-mall-events}")
    private String mallTopic;

    @Value("${ai-tour.backend-public-url:http://127.0.0.1:8080}")
    private String backendPublicUrl;

    public void publishCheckinEvent(CheckinRecord record, ScenicSpot spot, String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "CHECKIN_RESULT");
        payload.put("recordId", record.getId());
        payload.put("userId", record.getUserId());
        payload.put("spotId", record.getSpotId());
        payload.put("spotName", spot == null ? null : spot.getName());
        payload.put("status", record.getStatus());
        payload.put("imageUrl", record.getUserImageUrl());
        payload.put("message", message);
        payload.put("occurredAt", System.currentTimeMillis());
        publish(checkinTopic, String.valueOf(record.getUserId()), payload);
    }

    public void publishMallExchangeEvent(ExchangeOrder order) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "MALL_EXCHANGE_CREATED");
        payload.put("orderId", order.getId());
        payload.put("orderNo", order.getOrderNo());
        payload.put("userId", order.getUserId());
        payload.put("itemId", order.getItemId());
        payload.put("costPoints", order.getCostPoints());
        payload.put("occurredAt", System.currentTimeMillis());
        publish(mallTopic, String.valueOf(order.getUserId()), payload);
    }

    public boolean publishAiCheckinRequest(CheckinRecord record,
                                           ScenicSpot spot,
                                           Double latitude,
                                           Double longitude) {
        if (!kafkaEnabled || kafkaTemplate == null) {
            return false;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "CHECKIN_VERIFY_REQUESTED");
        payload.put("recordId", record.getId());
        payload.put("userId", record.getUserId());
        payload.put("spotId", record.getSpotId());
        payload.put("targetSpot", spot.getAiLabel());
        payload.put("latitude", latitude);
        payload.put("longitude", longitude);
        payload.put("allowableRadius", 1000);
        payload.put("imageUrl", record.getUserImageUrl());
        payload.put("imageDownloadUrl", normalizeBaseUrl(backendPublicUrl) + record.getUserImageUrl());
        payload.put("callbackUrl", normalizeBaseUrl(backendPublicUrl) + "/api/checkin/ai/callback");
        payload.put("occurredAt", System.currentTimeMillis());
        return publishRequired(aiCheckinRequestTopic, String.valueOf(record.getUserId()), payload);
    }

    private void publish(String topic, String key, Map<String, Object> payload) {
        if (!kafkaEnabled || kafkaTemplate == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            ListenableFuture<?> future = kafkaTemplate.send(topic, key, json);
            future.addCallback(new ListenableFutureCallback<Object>() {
                @Override
                public void onFailure(Throwable ex) {
                    System.out.println("Kafka event publish failed: " + ex.getMessage());
                }

                @Override
                public void onSuccess(Object result) {
                    // Best-effort event publish; business transaction is already complete.
                }
            });
        } catch (Exception e) {
            System.out.println("Kafka event build failed: " + e.getMessage());
        }
    }

    private boolean publishRequired(String topic, String key, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(topic, key, json).get(3, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            System.out.println("Kafka required event publish failed: " + e.getMessage());
            return false;
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            return "http://127.0.0.1:8080";
        }
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }
}
