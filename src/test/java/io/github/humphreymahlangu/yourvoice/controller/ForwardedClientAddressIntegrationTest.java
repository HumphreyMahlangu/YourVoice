package io.github.humphreymahlangu.votetrust.controller;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.humphreymahlangu.votetrust.entity.SecurityAuditEvent;
import io.github.humphreymahlangu.votetrust.entity.SecurityAuditEventType;
import io.github.humphreymahlangu.votetrust.repository.SecurityAuditEventRepository;
import io.github.humphreymahlangu.votetrust.support.DatabaseCleaner;
import io.github.humphreymahlangu.votetrust.support.PostgreSqlTestContainerSupport;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs against a real Tomcat port because forwarded-header handling happens in Tomcat's RemoteIpValve,
 * which MockMvc bypasses. Requests arrive from 127.0.0.1, which Tomcat treats as a trusted internal proxy,
 * the same way Render's proxy forwards traffic from a private network address.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "votetrust.security.rate-limit.auth-limit=1",
                // A long fixed window keeps the two same-client requests from straddling a window boundary.
                "votetrust.security.rate-limit.window-seconds=3600"
        }
)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ForwardedClientAddressIntegrationTest extends PostgreSqlTestContainerSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private SecurityAuditEventRepository securityAuditEventRepository;

    @BeforeEach
    void cleanDatabase() {
        DatabaseCleaner.clean(jdbcTemplate);
    }

    @Test
    void securityAuditRecordsTheClientAddressForwardedByTheProxy() throws Exception {
        HttpResponse<String> response = loginFrom("203.0.113.10");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(securityAuditEventRepository.findAll())
                .filteredOn(event -> event.getEventType() == SecurityAuditEventType.USER_LOGIN)
                .singleElement()
                .extracting(SecurityAuditEvent::getClientIp)
                .isEqualTo("203.0.113.10");
    }

    @Test
    void rateLimitBucketsAreKeptPerForwardedClientAddress() throws Exception {
        assertThat(loginFrom("198.51.100.1").statusCode()).isEqualTo(401);
        assertThat(loginFrom("198.51.100.1").statusCode()).isEqualTo(429);

        assertThat(loginFrom("198.51.100.2").statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> loginFrom(String clientAddress) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", clientAddress)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {
                          "email": "nobody@example.com",
                          "password": "WrongPassword1"
                        }
                        """))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
