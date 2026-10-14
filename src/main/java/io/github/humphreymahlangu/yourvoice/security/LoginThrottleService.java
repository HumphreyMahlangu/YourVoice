package io.github.humphreymahlangu.votetrust.security;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Locks sign-in for an email address after repeated failures, independent of the client IP, so a password
 * guessing attack spread across many IPs is still slowed down.
 *
 * <p>The throttle is keyed by the normalized email whether or not an account exists, so its behaviour does not
 * reveal which emails are registered. Like the IP rate limiter it is in-memory and per instance.
 */
@Service
public class LoginThrottleService {

    private static final int CLEANUP_INTERVAL = 1024;

    private final LoginThrottleProperties properties;
    private final Clock clock;
    private final Map<String, FailureState> states = new ConcurrentHashMap<>();
    private final AtomicLong operations = new AtomicLong();

    public LoginThrottleService(LoginThrottleProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Seconds until sign-in is allowed again for this email, or empty when it is not locked. */
    public Optional<Integer> lockedForSeconds(String normalizedEmail) {
        FailureState state = states.get(normalizedEmail);
        long now = clock.instant().getEpochSecond();
        if (state == null || state.lockedUntil() <= now) {
            return Optional.empty();
        }
        return Optional.of((int) (state.lockedUntil() - now));
    }

    public void recordFailure(String normalizedEmail) {
        long now = clock.instant().getEpochSecond();
        states.compute(normalizedEmail, (ignored, current) -> {
            int failures = current == null || current.lockedUntil() > 0 && current.lockedUntil() <= now
                    ? 1
                    : current.failures() + 1;
            if (failures >= properties.maxFailures()) {
                return new FailureState(0, now + properties.lockSeconds());
            }
            return new FailureState(failures, 0);
        });
        maybeCleanUp(now);
    }

    public void recordSuccess(String normalizedEmail) {
        states.remove(normalizedEmail);
    }

    private void maybeCleanUp(long now) {
        if (operations.incrementAndGet() % CLEANUP_INTERVAL != 0) {
            return;
        }
        Iterator<Map.Entry<String, FailureState>> iterator = states.entrySet().iterator();
        while (iterator.hasNext()) {
            FailureState state = iterator.next().getValue();
            if (state.lockedUntil() > 0 && state.lockedUntil() <= now) {
                iterator.remove();
            }
        }
    }

    private record FailureState(int failures, long lockedUntil) {
    }
}
