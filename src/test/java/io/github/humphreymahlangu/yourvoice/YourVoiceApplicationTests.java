package io.github.humphreymahlangu.yourvoice;

import io.github.humphreymahlangu.yourvoice.support.PostgreSqlTestContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class YourVoiceApplicationTests extends PostgreSqlTestContainerSupport {

    @Test
    void contextLoads() {
    }
}
