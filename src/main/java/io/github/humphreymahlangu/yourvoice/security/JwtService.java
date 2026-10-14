package io.github.humphreymahlangu.votetrust.security;

import io.github.humphreymahlangu.votetrust.entity.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final SecretKey signingKey;

    @Autowired
    public JwtService(JwtProperties jwtProperties) {
        this(jwtProperties, Clock.systemUTC());
    }

    JwtService(JwtProperties jwtProperties, Clock clock) {
        this.jwtProperties = jwtProperties;
        this.clock = clock;
        this.signingKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public TokenResult generateAccessToken(UserAccount userAccount) {
        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plus(jwtProperties.accessTokenExpirationMinutes(), ChronoUnit.MINUTES);

        String token = Jwts.builder()
                .issuer(jwtProperties.issuer())
                .subject(userAccount.getId().toString())
                .claim("email", userAccount.getEmail())
                .claim("role", userAccount.getRole().name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();

        return new TokenResult(token, expiresAt);
    }

    public UUID extractUserId(String token) {
        Claims claims = Jwts.parser()
                .clock(() -> Date.from(Instant.now(clock)))
                .verifyWith(signingKey)
                .requireIssuer(jwtProperties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return UUID.fromString(claims.getSubject());
    }

    public record TokenResult(String token, Instant expiresAt) {
    }
}
