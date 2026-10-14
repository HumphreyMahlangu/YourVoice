package io.github.humphreymahlangu.yourvoice.config;

import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Validates the log format read by logback-spring.xml, so a typo fails startup instead of leaving the application
 * without a console appender.
 */
@Validated
@ConfigurationProperties(prefix = "yourvoice.logging")
public record LoggingProperties(
        @Pattern(regexp = "text|json", message = "must be text or json")
        String format
) {
}
