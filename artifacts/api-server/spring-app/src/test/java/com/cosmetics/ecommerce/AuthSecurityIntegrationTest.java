package com.cosmetics.ecommerce;

import com.cosmetics.ecommerce.dto.AuthDtos.LoginRequest;
import com.cosmetics.ecommerce.dto.AuthDtos.RegisterRequest;
import com.cosmetics.ecommerce.dto.ProfileDtos.ResetPasswordRequest;
import com.cosmetics.ecommerce.entity.PasswordResetToken;
import com.cosmetics.ecommerce.entity.User;
import com.cosmetics.ecommerce.enums.Role;
import com.cosmetics.ecommerce.entity.RefreshToken;
import com.cosmetics.ecommerce.repository.PasswordResetTokenRepository;
import com.cosmetics.ecommerce.repository.RefreshTokenRepository;
import com.cosmetics.ecommerce.repository.UserRepository;
import com.cosmetics.ecommerce.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordResetTokenRepository resetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        resetTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Registration assigns CUSTOMER role and hashes password with BCrypt")
    void registrationAssignsCustomerRoleAndHashesPassword() throws Exception {
        RegisterRequest request = new RegisterRequest("Sarah", "Connor", "sarah@example.com", "SecureP@ss123", "+1234567890");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andExpect(jsonPath("$.data.user.role").value("CUSTOMER"));

        User savedUser = userRepository.findByEmailIgnoreCase("sarah@example.com").orElseThrow();
        assertThat(savedUser.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(savedUser.getPassword()).startsWith("$2a$");
        assertThat(passwordEncoder.matches("SecureP@ss123", savedUser.getPassword())).isTrue();
    }

    @Test
    @DisplayName("Login with incorrect password returns 401 Unauthorized")
    void loginWithIncorrectPasswordReturns401() throws Exception {
        User user = new User("John", "Doe", "john@example.com", passwordEncoder.encode("CorrectPassword1"), Role.CUSTOMER);
        userRepository.save(user);

        LoginRequest request = new LoginRequest("john@example.com", "WrongPassword999");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    @DisplayName("Login for deactivated user returns 401 Unauthorized")
    void loginForDeactivatedUserReturns401() throws Exception {
        User user = new User("Disabled", "User", "disabled@example.com", passwordEncoder.encode("Password123"), Role.CUSTOMER);
        user.setEnabled(false);
        userRepository.save(user);

        LoginRequest request = new LoginRequest("disabled@example.com", "Password123");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("This account has been deactivated."));
    }

    @Test
    @DisplayName("Protected endpoint accepts valid access token")
    void protectedEndpointAcceptsValidAccessToken() throws Exception {
        User user = new User("Alice", "Smith", "alice@example.com", passwordEncoder.encode("Password123"), Role.CUSTOMER);
        userRepository.save(user);

        String accessToken = jwtService.accessToken(user);

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("alice@example.com"));
    }

    @Test
    @DisplayName("Protected endpoint rejects refresh token when used as access token (Token Confusion Prevention)")
    void protectedEndpointRejectsRefreshTokenAsAccessToken() throws Exception {
        User user = new User("Bob", "Brown", "bob@example.com", passwordEncoder.encode("Password123"), Role.CUSTOMER);
        userRepository.save(user);

        String refreshToken = jwtService.refreshToken(user);

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Protected endpoint rejects deactivated user even with valid access token")
    void protectedEndpointRejectsDeactivatedUserWithValidAccessToken() throws Exception {
        User user = new User("Charlie", "Green", "charlie@example.com", passwordEncoder.encode("Password123"), Role.CUSTOMER);
        userRepository.save(user);

        String accessToken = jwtService.accessToken(user);

        // Deactivate user after token creation
        user.setEnabled(false);
        userRepository.save(user);

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Password reset flow successfully updates password and invalidates token")
    void passwordResetUpdatesPasswordAndInvalidatesToken() throws Exception {
        User user = new User("Reset", "User", "reset@example.com", passwordEncoder.encode("OldPassword123"), Role.CUSTOMER);
        userRepository.save(user);

        String token = UUID.randomUUID().toString();
        resetTokenRepository.save(new PasswordResetToken(token, user, Instant.now().plus(30, ChronoUnit.MINUTES)));

        ResetPasswordRequest request = new ResetPasswordRequest(token, "BrandNewPassword456");

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        User updatedUser = userRepository.findByEmailIgnoreCase("reset@example.com").orElseThrow();
        assertThat(passwordEncoder.matches("BrandNewPassword456", updatedUser.getPassword())).isTrue();

        // Ensure token was deleted so it cannot be re-used
        assertThat(resetTokenRepository.findByToken(token)).isEmpty();
    }

    @Test
    @DisplayName("Login sets HttpOnly Secure refresh_token cookie and persists token in database")
    void loginSetsHttpOnlySecureRefreshCookieAndPersistsToken() throws Exception {
        User user = new User("Jane", "Doe", "jane@example.com", passwordEncoder.encode("Password123"), Role.CUSTOMER);
        userRepository.save(user);

        LoginRequest request = new LoginRequest("jane@example.com", "Password123");

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).contains("refresh_token=");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("Path=/api/auth/refresh");
        assertThat(setCookie).contains("SameSite=Strict");

        List<RefreshToken> tokens = refreshTokenRepository.findByUserAndRevokedFalse(user);
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).isActive()).isTrue();
    }

    @Test
    @DisplayName("Refresh endpoint with HttpOnly cookie rotates tokens and revokes old token")
    void refreshUsingCookieRotatesTokensAndUpdatesDatabase() throws Exception {
        User user = new User("Refresh", "Tester", "refreshtest@example.com", passwordEncoder.encode("Password123"), Role.CUSTOMER);
        userRepository.save(user);

        LoginRequest loginRequest = new LoginRequest("refreshtest@example.com", "Password123");
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie refreshCookie = loginResult.getResponse().getCookie("refresh_token");
        assertThat(refreshCookie).isNotNull();
        String initialToken = refreshCookie.getValue();

        // Perform rotation via cookie
        MvcResult refreshResult = mockMvc.perform(post("/auth/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andReturn();

        Cookie rotatedCookie = refreshResult.getResponse().getCookie("refresh_token");
        assertThat(rotatedCookie).isNotNull();
        String rotatedToken = rotatedCookie.getValue();
        assertThat(rotatedToken).isNotEqualTo(initialToken);

        // Old token must be revoked
        RefreshToken oldEntity = refreshTokenRepository.findByToken(initialToken).orElseThrow();
        assertThat(oldEntity.isRevoked()).isTrue();

        // New token must be active
        RefreshToken newEntity = refreshTokenRepository.findByToken(rotatedToken).orElseThrow();
        assertThat(newEntity.isRevoked()).isFalse();
        assertThat(newEntity.getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("Replaying revoked refresh token triggers reuse detection and revokes all user sessions")
    void replayingRevokedRefreshTokenTriggersReuseDetectionAndRevokesAllSessions() throws Exception {
        User user = new User("Victim", "User", "victim@example.com", passwordEncoder.encode("Password123"), Role.CUSTOMER);
        userRepository.save(user);

        // First login -> token 1
        MvcResult loginResult1 = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("victim@example.com", "Password123"))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie tokenCookie1 = loginResult1.getResponse().getCookie("refresh_token");

        // Rotate token 1 -> token 2 (token 1 is now revoked)
        mockMvc.perform(post("/auth/refresh").cookie(tokenCookie1))
                .andExpect(status().isOk());

        // Also simulate another active session (e.g. tablet login -> token 3)
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("victim@example.com", "Password123"))))
                .andExpect(status().isOk());

        // Currently, active tokens exist
        assertThat(refreshTokenRepository.findByUserAndRevokedFalse(user)).isNotEmpty();

        // Replay attack: Attacker attempts to use revoked tokenCookie1
        mockMvc.perform(post("/auth/refresh").cookie(tokenCookie1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Security alert: Refresh token reuse detected. All sessions terminated."));

        // Reuse detection check: ALL active tokens for this user must now be revoked
        List<RefreshToken> activeTokensAfterBreach = refreshTokenRepository.findByUserAndRevokedFalse(user);
        assertThat(activeTokensAfterBreach).isEmpty();
    }

    @Test
    @DisplayName("Logout revokes refresh token and clears client cookie")
    void logoutRevokesRefreshTokenAndClearsCookie() throws Exception {
        User user = new User("Logout", "User", "logout@example.com", passwordEncoder.encode("Password123"), Role.CUSTOMER);
        userRepository.save(user);

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("logout@example.com", "Password123"))))
                .andExpect(status().isOk())
                .andReturn();

        Cookie refreshCookie = loginResult.getResponse().getCookie("refresh_token");
        assertThat(refreshCookie).isNotNull();

        MvcResult logoutResult = mockMvc.perform(post("/auth/logout").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully."))
                .andReturn();

        String clearCookie = logoutResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(clearCookie).isNotNull();
        assertThat(clearCookie).contains("Max-Age=0");

        RefreshToken storedToken = refreshTokenRepository.findByToken(refreshCookie.getValue()).orElseThrow();
        assertThat(storedToken.isRevoked()).isTrue();
    }
}
