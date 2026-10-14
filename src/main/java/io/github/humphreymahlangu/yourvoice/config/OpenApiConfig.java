package io.github.humphreymahlangu.votetrust.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "VoteTrust API",
                version = "v1",
                description = "Secure REST API for a South African online voting simulation with registration windows, anonymous credentials, one-vote enforcement, and SHA-256 ledger auditing.",
                contact = @Contact(name = "VoteTrust Portfolio Project"),
                license = @License(name = "MIT")
        ),
        servers = {
                @Server(url = "/", description = "Current deployment")
        }
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER
)
public class OpenApiConfig {
}
