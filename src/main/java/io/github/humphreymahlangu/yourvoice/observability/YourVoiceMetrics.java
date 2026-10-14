package io.github.humphreymahlangu.yourvoice.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Application counters exported at /actuator/prometheus (admin only).
 *
 * <p>No metric is tagged with a contest option: a time series of counts per option would let anyone who knows when
 * a voter cast their ballot recover their choice.
 */
@Component
public class YourVoiceMetrics {

    private final Counter ballotsCast;
    private final Counter credentialsIssued;
    private final Counter loginsThrottled;
    private final Counter refreshTokenReuse;
    private final Counter lifecycleFailures;
    private final MeterRegistry meterRegistry;

    public YourVoiceMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.ballotsCast = Counter.builder("yourvoice.ballots.cast")
                .description("Ballots accepted into a contest ledger")
                .register(meterRegistry);
        this.credentialsIssued = Counter.builder("yourvoice.credentials.issued")
                .description("Ballot credentials signed for the first time")
                .register(meterRegistry);
        this.loginsThrottled = Counter.builder("yourvoice.auth.logins.throttled")
                .description("Sign-in attempts rejected because the email is temporarily locked")
                .register(meterRegistry);
        this.refreshTokenReuse = Counter.builder("yourvoice.auth.refresh.token.reuse")
                .description("Revoked refresh tokens presented again (possible token theft)")
                .register(meterRegistry);
        this.lifecycleFailures = Counter.builder("yourvoice.lifecycle.failures")
                .description("Automatic election transitions that failed closed")
                .register(meterRegistry);
    }

    public void ballotCast() {
        ballotsCast.increment();
    }

    public void credentialIssued() {
        credentialsIssued.increment();
    }

    public void loginThrottled() {
        loginsThrottled.increment();
    }

    public void refreshTokenReused() {
        refreshTokenReuse.increment();
    }

    public void lifecycleFailure() {
        lifecycleFailures.increment();
    }

    public void rateLimitBlocked(String bucket) {
        Counter.builder("yourvoice.ratelimit.blocked")
                .description("Requests rejected by the IP rate limiter")
                .tag("bucket", bucket)
                .register(meterRegistry)
                .increment();
    }
}
