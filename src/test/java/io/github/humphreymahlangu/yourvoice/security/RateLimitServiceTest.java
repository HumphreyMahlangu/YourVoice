package io.github.humphreymahlangu.votetrust.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RateLimitServiceTest {

    @Test
    void blocksRequestsAfterConfiguredLimitInCurrentWindow() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setWindowSeconds(60);
        RateLimitService service = new RateLimitService(
                properties,
                Clock.fixed(Instant.parse("2026-08-07T10:15:30Z"), ZoneOffset.UTC)
        );

        RateLimitDecision first = service.consume("auth", "127.0.0.1", 2);
        RateLimitDecision second = service.consume("auth", "127.0.0.1", 2);
        RateLimitDecision third = service.consume("auth", "127.0.0.1", 2);

        assertThat(first.allowed()).isTrue();
        assertThat(first.remainingRequests()).isEqualTo(1);
        assertThat(second.allowed()).isTrue();
        assertThat(second.remainingRequests()).isZero();
        assertThat(third.allowed()).isFalse();
        assertThat(third.retryAfterSeconds()).isEqualTo(30);
    }

    @Test
    void tracksBucketsAndKeysIndependently() {
        RateLimitProperties properties = new RateLimitProperties();
        RateLimitService service = new RateLimitService(
                properties,
                Clock.fixed(Instant.parse("2026-08-07T10:15:00Z"), ZoneOffset.UTC)
        );

        service.consume("auth", "127.0.0.1", 1);

        assertThat(service.consume("auth", "127.0.0.2", 1).allowed()).isTrue();
        assertThat(service.consume("ballot-submission", "127.0.0.1", 1).allowed()).isTrue();
        assertThat(service.consume("auth", "127.0.0.1", 1).allowed()).isFalse();
    }

    @Test
    void allowsRequestsWhenRateLimitingIsDisabled() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setEnabled(false);
        RateLimitService service = new RateLimitService(
                properties,
                Clock.fixed(Instant.parse("2026-08-07T10:15:00Z"), ZoneOffset.UTC)
        );

        assertThat(service.consume("auth", "127.0.0.1", 1).allowed()).isTrue();
        assertThat(service.consume("auth", "127.0.0.1", 1).allowed()).isTrue();
    }
}
