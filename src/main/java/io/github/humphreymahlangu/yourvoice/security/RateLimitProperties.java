package io.github.humphreymahlangu.votetrust.security;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "votetrust.security.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;

    @Min(1)
    private int windowSeconds = 60;

    @Min(1)
    private int authLimit = 100;

    @Min(1)
    private int bootstrapLimit = 20;

    @Min(1)
    private int credentialLimit = 100;

    @Min(1)
    private int ballotLimit = 100;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public int getAuthLimit() {
        return authLimit;
    }

    public void setAuthLimit(int authLimit) {
        this.authLimit = authLimit;
    }

    public int getBootstrapLimit() {
        return bootstrapLimit;
    }

    public void setBootstrapLimit(int bootstrapLimit) {
        this.bootstrapLimit = bootstrapLimit;
    }

    public int getCredentialLimit() {
        return credentialLimit;
    }

    public void setCredentialLimit(int credentialLimit) {
        this.credentialLimit = credentialLimit;
    }

    public int getBallotLimit() {
        return ballotLimit;
    }

    public void setBallotLimit(int ballotLimit) {
        this.ballotLimit = ballotLimit;
    }
}
