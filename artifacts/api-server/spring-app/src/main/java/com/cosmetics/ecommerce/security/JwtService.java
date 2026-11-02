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

@Service
public class JwtService {
    private final SecretKey key;
    private final long accessTokenMinutes;
    private final long refreshTokenDays;

    public JwtService(
            @Value("${jwt.secret:${app.jwt.secret:a4bf05ff532862e43naem226f19619674f8d89a7d5442e426a6e2e170e731a66}}") String secret,
            @Value("${jwt.access-token-minutes:${app.jwt.access-token-minutes:30}}") long accessTokenMinutes,
            @Value("${jwt.refresh-token-days:${app.jwt.refresh-token-days:14}}") long refreshTokenDays) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET or SESSION_SECRET must contain at least 32 characters.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenMinutes = accessTokenMinutes;
        this.refreshTokenDays = refreshTokenDays;
    }

    public String accessToken(User user) {return build(user, accessTokenMinutes * 60, "ACCESS");}

    public String refreshToken(User user) {return build(user, refreshTokenDays * 24 * 60 * 60, "REFRESH");}
    private String build(User user, long seconds, String tokenType) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("role", user.getRole().name())
                .claim("token_type",tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(seconds)))
                .signWith(key)
                .compact();
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
            String type = tokenType(token);
            return type == null || "ACCESS".equalsIgnoreCase(type);
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public boolean isRefreshToken(String token) {
        try {
            return "REFRESH".equalsIgnoreCase(tokenType(token));
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public boolean isValid(String token, User user) {
        try {
            Claims claims = parse(token).getPayload();
            return user.getUsername().equals(claims.getSubject()) && !claims.getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    private Jws<Claims> parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
    }
}