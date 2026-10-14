package io.github.humphreymahlangu.votetrust.security;

public record RateLimitDecision(
        boolean allowed,
        int retryAfterSeconds,
        int remainingRequests
) {

    public static RateLimitDecision allowed(int remainingRequests) {
        return new RateLimitDecision(true, 0, remainingRequests);
    }

    public static RateLimitDecision blocked(int retryAfterSeconds) {
        return new RateLimitDecision(false, retryAfterSeconds, 0);
    }
}
