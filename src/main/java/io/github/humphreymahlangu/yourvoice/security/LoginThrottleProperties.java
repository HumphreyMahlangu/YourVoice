package io.github.humphreymahlangu.yourvoice.security;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "yourvoice.security.login-throttle")
public record LoginThrottleProperties(
        @Min(1)
        int maxFailures,

        @Min(1)
        int lockSeconds
) {
}
