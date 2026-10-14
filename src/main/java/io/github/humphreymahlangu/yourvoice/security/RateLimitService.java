package io.github.humphreymahlangu.votetrust.security;

import java.time.Clock;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {

    private static final int CLEANUP_INTERVAL = 1024;

    private final RateLimitProperties rateLimitProperties;
    private final Clock clock;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final AtomicLong attempts = new AtomicLong();

    public RateLimitService(RateLimitProperties rateLimitProperties, Clock clock) {
        this.rateLimitProperties = rateLimitProperties;
        this.clock = clock;
    }

    public RateLimitDecision consume(String bucket, String key, int limit) {
        if (!rateLimitProperties.isEnabled()) {
            return RateLimitDecision.allowed(Integer.MAX_VALUE);
        }

        long nowEpochSecond = clock.instant().getEpochSecond();
        int windowSeconds = rateLimitProperties.getWindowSeconds();
        long windowStartedAt = nowEpochSecond - (nowEpochSecond % windowSeconds);
        String counterKey = bucket + ":" + key;
        AtomicBoolean allowed = new AtomicBoolean(true);

        WindowCounter updatedCounter = counters.compute(counterKey, (ignored, current) -> {
            if (current == null || current.windowStartedAt() != windowStartedAt) {
                allowed.set(true);
                return new WindowCounter(windowStartedAt, 1);
            }

            if (current.requestCount() >= limit) {
                allowed.set(false);
                return current;
            }

            allowed.set(true);
            return new WindowCounter(windowStartedAt, current.requestCount() + 1);
        });

        maybeCleanUp(nowEpochSecond, windowSeconds);

        if (allowed.get()) {
            int remainingRequests = Math.max(0, limit - updatedCounter.requestCount());
            return RateLimitDecision.allowed(remainingRequests);
        }

        int retryAfterSeconds = (int) Math.max(1, updatedCounter.windowStartedAt() + windowSeconds - nowEpochSecond);
        return RateLimitDecision.blocked(retryAfterSeconds);
    }

    private void maybeCleanUp(long nowEpochSecond, int windowSeconds) {
        if (attempts.incrementAndGet() % CLEANUP_INTERVAL != 0) {
            return;
        }

        long oldestActiveWindow = nowEpochSecond - windowSeconds;
        Iterator<Map.Entry<String, WindowCounter>> iterator = counters.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, WindowCounter> entry = iterator.next();
            if (entry.getValue().windowStartedAt() < oldestActiveWindow) {
                iterator.remove();
            }
        }
    }

    private record WindowCounter(long windowStartedAt, int requestCount) {
    }
}
