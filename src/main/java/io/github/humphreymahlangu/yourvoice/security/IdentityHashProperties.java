package io.github.humphreymahlangu.yourvoice.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "yourvoice.security")
public record IdentityHashProperties(
        @NotBlank
        @Size(min = 32)
        String identityHashPepper
) {
}
