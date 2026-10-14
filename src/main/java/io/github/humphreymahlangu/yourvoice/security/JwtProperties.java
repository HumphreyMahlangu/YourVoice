package io.github.humphreymahlangu.votetrust.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "votetrust.security.jwt")
public record JwtProperties(
        @NotBlank
        @Size(min = 32)
        String secret,

        @NotBlank
        String issuer,

        @Min(1)
        long accessTokenExpirationMinutes,

        @Min(1)
        long refreshTokenExpirationHours
) {
}
