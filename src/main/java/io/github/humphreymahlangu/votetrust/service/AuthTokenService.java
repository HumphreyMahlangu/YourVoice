package io.github.humphreymahlangu.votetrust.service;

import io.github.humphreymahlangu.votetrust.dto.AuthResponse;
import io.github.humphreymahlangu.votetrust.entity.RefreshToken;
import io.github.humphreymahlangu.votetrust.entity.SecurityAuditEventType;
import io.github.humphreymahlangu.votetrust.entity.SecurityAuditOutcome;
import io.github.humphreymahlangu.votetrust.entity.UserAccount;
import io.github.humphreymahlangu.votetrust.exception.InvalidRefreshTokenException;
import io.github.humphreymahlangu.votetrust.repository.RefreshTokenRepository;
import io.github.humphreymahlangu.votetrust.security.JwtProperties;
import io.github.humphreymahlangu.votetrust.security.JwtService;
import io.github.humphreymahlangu.votetrust.security.SecurityAuditMetadata;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues short-lived access tokens with rotating refresh tokens.
 *
 * <p>Every refresh revokes the presented token and issues a new one in the same family. Presenting an
 * already-revoked token means it was copied and used elsewhere, so the whole family is revoked and both the
 * attacker and the legitimate client must sign in again.
 */
@Service
public class AuthTokenService {

    private static final int REFRESH_TOKEN_BYTES = 32;

    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SecurityAuditService securityAuditService;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthTokenService(
            JwtService jwtService,
            JwtProperties jwtProperties,
            RefreshTokenRepository refreshTokenRepository,
            SecurityAuditService securityAuditService,
            Clock clock
    ) {
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.refreshTokenRepository = refreshTokenRepository;
        this.securityAuditService = securityAuditService;
        this.clock = clock;
    }

    @Transactional
    public AuthResponse issueTokens(UserAccount userAccount) {
        return issueTokens(userAccount, UUID.randomUUID());
    }

    // Not rolled back on InvalidRefreshTokenException: revoking a family after token reuse must persist.
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthResponse refresh(String rawRefreshToken, SecurityAuditMetadata metadata) {
        Instant now = Instant.now(clock);
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHashForUpdate(hash(rawRefreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        UserAccount userAccount = refreshToken.getUserAccount();

        if (refreshToken.isRevoked()) {
            refreshTokenRepository.revokeFamily(refreshToken.getFamilyId(), now);
            securityAuditService.record(
                    SecurityAuditEventType.TOKEN_REFRESH,
                    SecurityAuditOutcome.BLOCKED,
                    userAccount,
                    metadata,
                    "Revoked refresh token reused; all sessions in the family were revoked"
            );
            throw new InvalidRefreshTokenException();
        }
        if (refreshToken.isExpired(now) || !userAccount.isEnabled()) {
            refreshToken.revoke(now);
            throw new InvalidRefreshTokenException();
        }

        refreshToken.revoke(now);
        securityAuditService.record(
                SecurityAuditEventType.TOKEN_REFRESH,
                SecurityAuditOutcome.SUCCESS,
                userAccount,
                metadata,
                "Refresh token rotated"
        );
        return issueTokens(userAccount, refreshToken.getFamilyId());
    }

    /** Revokes the session family of the presented token. Unknown tokens are ignored so logout is idempotent. */
    @Transactional
    public void logout(String rawRefreshToken, SecurityAuditMetadata metadata) {
        refreshTokenRepository.findByTokenHashForUpdate(hash(rawRefreshToken)).ifPresent(refreshToken -> {
            UserAccount userAccount = refreshToken.getUserAccount();
            refreshTokenRepository.revokeFamily(refreshToken.getFamilyId(), Instant.now(clock));
            securityAuditService.record(
                    SecurityAuditEventType.USER_LOGOUT,
                    SecurityAuditOutcome.SUCCESS,
                    userAccount,
                    metadata,
                    "Session revoked"
            );
        });
    }

    private AuthResponse issueTokens(UserAccount userAccount, UUID familyId) {
        Instant now = Instant.now(clock);
        refreshTokenRepository.deleteExpiredForUser(userAccount.getId(), now);

        String rawRefreshToken = generateRefreshToken();
        Instant refreshTokenExpiresAt = now.plus(jwtProperties.refreshTokenExpirationHours(), ChronoUnit.HOURS);
        refreshTokenRepository.save(new RefreshToken(
                userAccount,
                familyId,
                hash(rawRefreshToken),
                refreshTokenExpiresAt,
                now
        ));

        JwtService.TokenResult accessToken = jwtService.generateAccessToken(userAccount);
        return new AuthResponse(
                accessToken.token(),
                "Bearer",
                accessToken.expiresAt(),
                userAccount.getId(),
                userAccount.getEmail(),
                userAccount.getRole().name(),
                rawRefreshToken,
                refreshTokenExpiresAt
        );
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // The token is 256 random bits, so a plain SHA-256 is enough to make a database leak useless.
    private String hash(String rawRefreshToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawRefreshToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
