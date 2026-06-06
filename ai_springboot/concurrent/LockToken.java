package com.example.ai_springboot.concurrent;

import lombok.Getter;

@Getter
public class LockToken {
    private final String key;
    private final String token;
    private final boolean redisLock;

    public LockToken(String key, String token, boolean redisLock) {
        this.key = key;
        this.token = token;
        this.redisLock = redisLock;
    }
}
