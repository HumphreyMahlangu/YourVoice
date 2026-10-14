package io.github.humphreymahlangu.votetrust.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "votetrust.security")
public record IdentityHashProperties(
        @NotBlank
        @Size(min = 32)
        String identityHashPepper
) {
}
