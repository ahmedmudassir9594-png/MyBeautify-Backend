package com.cosmetics.ecommerce;

import com.cosmetics.ecommerce.dto.AuthDtos;
import com.cosmetics.ecommerce.entity.RefreshToken;
import com.cosmetics.ecommerce.entity.User;
import com.cosmetics.ecommerce.enums.Role;
import com.cosmetics.ecommerce.exception.UnauthorizedException;
import com.cosmetics.ecommerce.repository.RefreshTokenRepository;
import com.cosmetics.ecommerce.repository.UserRepository;
import com.cosmetics.ecommerce.security.JwtService;
import com.cosmetics.ecommerce.service.AuthService;
import com.cosmetics.ecommerce.email.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class AuthServiceTest {
    @Mock UserRepository users;
    @Mock PasswordEncoder encoder;
    @Mock JwtService jwt;
    @Mock EmailService email;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @InjectMocks AuthService service;

    @Test
    void registerHashesPasswordAndReturnsTokens() {
        when(users.existsByEmailIgnoreCase("maya@example.com")).thenReturn(false);
        when(encoder.encode("password123")).thenReturn("hashed");
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwt.accessToken(any())).thenReturn("access");
        when(jwt.refreshToken(any())).thenReturn("refresh");
        when(jwt.getRefreshTokenDays()).thenReturn(14L);

        AuthDtos.AuthResponse response = service.register(new AuthDtos.RegisterRequest(
                "Maya", "Perera", "maya@example.com", "password123", "+9411"));

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("hashed");
        assertThat(captor.getValue().getRole()).isEqualTo(Role.CUSTOMER);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void loginRejectsWrongPasswordWithoutIssuingTokens() {
        User user = new User("Maya", "Perera", "maya@example.com", "hashed", Role.CUSTOMER);
        when(users.findByEmailIgnoreCase("maya@example.com")).thenReturn(Optional.of(user));
        when(encoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() ->
                service.login(new AuthDtos.LoginRequest("maya@example.com", "wrong")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid email or password.");
        verifyNoInteractions(jwt);
    }

    @Test
    void rotateRefreshTokenSuccess() {
        User user = new User("Maya", "Perera", "maya@example.com", "hashed", Role.CUSTOMER);
        RefreshToken existingToken = new RefreshToken("old-refresh-token", user, Instant.now().plus(7, ChronoUnit.DAYS));

        when(refreshTokenRepository.findByToken("old-refresh-token")).thenReturn(Optional.of(existingToken));
        when(jwt.isRefreshToken("old-refresh-token")).thenReturn(true);
        when(jwt.isValid("old-refresh-token", user)).thenReturn(true);
        when(jwt.accessToken(user)).thenReturn("new-access-token");
        when(jwt.refreshToken(user)).thenReturn("new-refresh-token");
        when(jwt.getRefreshTokenDays()).thenReturn(14L);

        AuthDtos.AuthResponse response = service.rotateRefreshToken("old-refresh-token");

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
        assertThat(existingToken.isRevoked()).isTrue();
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void rotateRefreshTokenDetectsReuseAndRevokesAllSessions() {
        User user = new User("Maya", "Perera", "maya@example.com", "hashed", Role.CUSTOMER);
        RefreshToken alreadyRevoked = new RefreshToken("reused-token", user, Instant.now().plus(7, ChronoUnit.DAYS));
        alreadyRevoked.setRevoked(true);

        when(refreshTokenRepository.findByToken("reused-token")).thenReturn(Optional.of(alreadyRevoked));

        assertThatThrownBy(() -> service.rotateRefreshToken("reused-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("reuse detected");

        verify(refreshTokenRepository).revokeAllActiveTokensByUser(user);
    }
}