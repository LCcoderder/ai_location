package com.example.ai_springboot.concurrent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class ConcurrencyGuard {
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setResultType(Long.class);
        UNLOCK_SCRIPT.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                        "return redis.call('del', KEYS[1]) " +
                        "else return 0 end"
        );
    }

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Value("${ai-tour.redis.lock-enabled:false}")
    private boolean redisLockEnabled;

    private final ConcurrentHashMap<String, LocalEntry> localLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> localMarks = new ConcurrentHashMap<>();

    public Optional<LockToken> tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        if (redisLockEnabled && redisTemplate != null) {
            try {
                Boolean ok = redisTemplate.opsForValue()
                        .setIfAbsent(key, token, ttl.toMillis(), TimeUnit.MILLISECONDS);
                if (Boolean.TRUE.equals(ok)) {
                    return Optional.of(new LockToken(key, token, true));
                }
                if (Boolean.FALSE.equals(ok)) {
                    return Optional.empty();
                }
            } catch (Exception ignored) {
                // Redis is optional in local demo. Fall back to an in-process lock.
            }
        }
        return tryLocalLock(key, token, ttl);
    }

    public void unlock(LockToken lockToken) {
        if (lockToken == null) {
            return;
        }
        if (lockToken.isRedisLock() && redisTemplate != null) {
            try {
                redisTemplate.execute(
                        UNLOCK_SCRIPT,
                        Collections.singletonList(lockToken.getKey()),
                        lockToken.getToken()
                );
                return;
            } catch (Exception ignored) {
                // If Redis is temporarily unavailable, also clear the local fallback if present.
            }
        }
        localLocks.compute(lockToken.getKey(), (key, entry) -> {
            if (entry != null && lockToken.getToken().equals(entry.token)) {
                return null;
            }
            return entry;
        });
    }

    public boolean tryMark(String key, Duration ttl) {
        if (redisLockEnabled && redisTemplate != null) {
            try {
                Boolean ok = redisTemplate.opsForValue()
                        .setIfAbsent(key, "1", ttl.toMillis(), TimeUnit.MILLISECONDS);
                if (ok != null) {
                    return ok;
                }
            } catch (Exception ignored) {
                // Redis is optional in local demo. Fall back to an in-process mark.
            }
        }
        return tryLocalMark(key, ttl);
    }

    private Optional<LockToken> tryLocalLock(String key, String token, Duration ttl) {
        long now = System.currentTimeMillis();
        long expiresAt = now + ttl.toMillis();
        while (true) {
            LocalEntry current = localLocks.get(key);
            if (current != null && current.expiresAt > now) {
                return Optional.empty();
            }
            if (current != null) {
                localLocks.remove(key, current);
                continue;
            }
            LocalEntry next = new LocalEntry(token, expiresAt);
            if (localLocks.putIfAbsent(key, next) == null) {
                return Optional.of(new LockToken(key, token, false));
            }
        }
    }

    private boolean tryLocalMark(String key, Duration ttl) {
        long now = System.currentTimeMillis();
        long expiresAt = now + ttl.toMillis();
        while (true) {
            Long current = localMarks.get(key);
            if (current != null && current > now) {
                return false;
            }
            if (current != null) {
                localMarks.remove(key, current);
                continue;
            }
            if (localMarks.putIfAbsent(key, expiresAt) == null) {
                return true;
            }
        }
    }

    private static class LocalEntry {
        private final String token;
        private final long expiresAt;

        private LocalEntry(String token, long expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }
    }
}
