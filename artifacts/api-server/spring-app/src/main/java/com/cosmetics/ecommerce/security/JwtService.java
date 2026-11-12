package com.cosmetics.ecommerce.security;

import com.cosmetics.ecommerce.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private final SecretKey key;
    private final long accessTokenMinutes;
    private final long refreshTokenDays;

    public JwtService(
            @Value("${jwt.secret:${app.jwt.secret:a4bf05ff532862e43naem226f19619674f8d89a7d5442e426a6e2e170e731a66}}") String secret,
            @Value("${jwt.access-token-minutes:${app.jwt.access-token-minutes:15}}") long accessTokenMinutes,
            @Value("${jwt.refresh-token-days:${app.jwt.refresh-token-days:14}}") long refreshTokenDays) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET or SESSION_SECRET must contain at least 32 characters.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenMinutes = accessTokenMinutes;
        this.refreshTokenDays = refreshTokenDays;
    }

    public long getAccessTokenMinutes() {
        return accessTokenMinutes;
    }

    public long getRefreshTokenDays() {
        return refreshTokenDays;
    }

    public String accessToken(User user) {
        return build(user, accessTokenMinutes * 60L, "ACCESS");
    }

    public String refreshToken(User user) {
        return build(user, refreshTokenDays * 24L * 60L * 60L, "REFRESH");
    }

    private String build(User user, long seconds, String tokenType) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getUsername())
                .claim("role", user.getRole().name())
                .claim("token_type", tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(seconds)))
                .signWith(key);

        if (user.getId() != null) {
            builder.claim("user_id", user.getId());
        }

        return builder.compact();
    }

    /**
     * Single-pass parser and validator for ACCESS tokens.
     * Ensures cryptographic signature, expiry, and token_type claim in ONE operation.
     */
    public Claims parseAndValidateAccessToken(String token) {
        Claims claims = parse(token).getPayload();
        Object type = claims.get("token_type");
        if (type == null || !"ACCESS".equalsIgnoreCase(type.toString())) {
            throw new JwtException("Invalid token type. Expected ACCESS token.");
        }
        return claims;
    }

    /**
     * Single-pass parser and validator for REFRESH tokens.
     */
    public Claims parseAndValidateRefreshToken(String token) {
        Claims claims = parse(token).getPayload();
        Object type = claims.get("token_type");
        if (type == null || !"REFRESH".equalsIgnoreCase(type.toString())) {
            throw new JwtException("Invalid token type. Expected REFRESH token.");
        }
        return claims;
    }

    public String subject(String token) {
        return parse(token).getPayload().getSubject();
    }

    public String tokenType(String token) {
        Object type = parse(token).getPayload().get("token_type");
        return type != null ? type.toString() : null;
    }

    public boolean isAccessToken(String token) {
        try {
            parseAndValidateAccessToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public boolean isRefreshToken(String token) {
        try {
            parseAndValidateRefreshToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public boolean isValid(String token, User user) {
        try {
            Claims claims = parse(token).getPayload();
            return user.getUsername().equalsIgnoreCase(claims.getSubject()) && !claims.getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    private Jws<Claims> parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
    }
}
