package io.github.humphreymahlangu.votetrust.security;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.util.StringUtils;

@Validated
@ConfigurationProperties(prefix = "votetrust.admin.bootstrap")
public record AdminBootstrapProperties(
        boolean enabled,
        String token
) {

    @AssertTrue(message = "bootstrap token must be at least 32 characters when admin bootstrap is enabled")
    public boolean isTokenValidWhenEnabled() {
        return !enabled || (StringUtils.hasText(token) && token.length() >= 32);
    }
}
