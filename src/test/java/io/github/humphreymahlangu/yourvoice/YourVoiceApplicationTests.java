package io.github.humphreymahlangu.votetrust;

import io.github.humphreymahlangu.votetrust.support.PostgreSqlTestContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class VoteTrustApplicationTests extends PostgreSqlTestContainerSupport {

    @Test
    void contextLoads() {
    }
}
