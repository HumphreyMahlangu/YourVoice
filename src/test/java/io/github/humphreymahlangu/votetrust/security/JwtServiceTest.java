package io.github.humphreymahlangu.votetrust.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.humphreymahlangu.votetrust.entity.AccountRole;
import io.github.humphreymahlangu.votetrust.entity.UserAccount;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-08-07T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void generateAccessTokenCreatesTokenThatCanBeParsedBackToUserId() throws Exception {
        JwtProperties properties = new JwtProperties(
                "unit-test-secret-value-that-is-long-enough",
                "votetrust-test",
                15,
                12
        );
        JwtService jwtService = new JwtService(properties, FIXED_CLOCK);
        UUID userId = UUID.randomUUID();
        UserAccount userAccount = new UserAccount("voter@example.com", "hash", AccountRole.VOTER, true);
        setId(userAccount, userId);

        JwtService.TokenResult result = jwtService.generateAccessToken(userAccount);

        assertThat(result.token()).isNotBlank();
        assertThat(result.expiresAt()).isEqualTo(Instant.parse("2026-08-07T10:15:00Z"));
        assertThat(jwtService.extractUserId(result.token())).isEqualTo(userId);
    }

    private void setId(UserAccount userAccount, UUID userId) throws Exception {
        Field idField = UserAccount.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(userAccount, userId);
    }
}
