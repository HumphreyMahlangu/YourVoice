package io.github.humphreymahlangu.yourvoice.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginThrottleServiceTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-07T10:00:00Z"));
    private final LoginThrottleService service = new LoginThrottleService(new LoginThrottleProperties(3, 900), clock);

    @Test
    void locksAfterTheConfiguredNumberOfFailures() {
        service.recordFailure("voter@example.com");
        service.recordFailure("voter@example.com");
        assertThat(service.lockedForSeconds("voter@example.com")).isEmpty();

        service.recordFailure("voter@example.com");

        assertThat(service.lockedForSeconds("voter@example.com")).contains(900);
        assertThat(service.lockedForSeconds("other@example.com")).isEmpty();
    }

    @Test
    void lockExpiresAndTheFailureCountStartsAgain() {
        for (int attempt = 0; attempt < 3; attempt++) {
            service.recordFailure("voter@example.com");
        }

        clock.advanceSeconds(901);
        assertThat(service.lockedForSeconds("voter@example.com")).isEmpty();

        service.recordFailure("voter@example.com");
        assertThat(service.lockedForSeconds("voter@example.com")).isEmpty();
    }

    @Test
    void successfulLoginClearsEarlierFailures() {
        service.recordFailure("voter@example.com");
        service.recordFailure("voter@example.com");
        service.recordSuccess("voter@example.com");

        service.recordFailure("voter@example.com");
        service.recordFailure("voter@example.com");

        assertThat(service.lockedForSeconds("voter@example.com")).isEmpty();
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
