package com.cosmetics.ecommerce.controller;

import com.cosmetics.ecommerce.dto.ApiResponse;
import com.cosmetics.ecommerce.dto.AuthDtos.AuthResponse;
import com.cosmetics.ecommerce.dto.AuthDtos.LoginRequest;
import com.cosmetics.ecommerce.dto.AuthDtos.RefreshTokenRequest;
import com.cosmetics.ecommerce.dto.AuthDtos.RegisterRequest;
import com.cosmetics.ecommerce.dto.GoogleAuthRequest;
import com.cosmetics.ecommerce.dto.ProfileDtos.ForgotPasswordRequest;
import com.cosmetics.ecommerce.dto.ProfileDtos.ResetPasswordRequest;
import com.cosmetics.ecommerce.dto.ProfileDtos.UpdateProfileRequest;
import com.cosmetics.ecommerce.dto.UserResponse;
import com.cosmetics.ecommerce.entity.User;
import com.cosmetics.ecommerce.exception.UnauthorizedException;
import com.cosmetics.ecommerce.security.JwtService;
import com.cosmetics.ecommerce.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    public static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";

    private final AuthService auth;
    private final JwtService jwtService;
    private final boolean cookieSecure;
    private final String cookieSameSite;
    private final String cookiePath;

    public AuthController(
            AuthService auth,
            JwtService jwtService,
            @Value("${app.jwt.cookie-secure:${JWT_COOKIE_SECURE:true}}") boolean cookieSecure,
            @Value("${app.jwt.cookie-same-site:${JWT_COOKIE_SAME_SITE:Strict}}") String cookieSameSite,
            @Value("${app.jwt.cookie-path:${JWT_COOKIE_PATH:/api/auth/refresh}}") String cookiePath) {
        this.auth = auth;
        this.jwtService = jwtService;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
        this.cookiePath = cookiePath;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = auth.register(request);
        ResponseCookie cookie = buildRefreshTokenCookie(response.refreshToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.ok("Registration successful.", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = auth.login(request);
        ResponseCookie cookie = buildRefreshTokenCookie(response.refreshToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.ok("Login successful.", response));
    }

    @PostMapping("/google")
    public ResponseEntity<ApiResponse<AuthResponse>> googleLogin(@Valid @RequestBody GoogleAuthRequest request) {
        AuthResponse response = auth.googleLogin(request.getCredential());
        ResponseCookie cookie = buildRefreshTokenCookie(response.refreshToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.ok("Google login successful.", response));
    }

    @PostMapping({"/refresh", "/refresh-token"})
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @CookieValue(name = REFRESH_TOKEN_COOKIE_NAME, required = false) String cookieToken,
            @RequestBody(required = false) RefreshTokenRequest body) {

        String rawToken = (cookieToken != null && !cookieToken.isBlank())
                ? cookieToken
                : (body != null ? body.refreshToken() : null);

        if (rawToken == null || rawToken.isBlank()) {
            throw new UnauthorizedException("Refresh token is required via HttpOnly cookie or request body.");
        }

        AuthResponse response = auth.rotateRefreshToken(rawToken);
        ResponseCookie newCookie = buildRefreshTokenCookie(response.refreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, newCookie.toString())
                .body(ApiResponse.ok("Token refreshed successfully.", response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = REFRESH_TOKEN_COOKIE_NAME, required = false) String cookieToken,
            @RequestBody(required = false) RefreshTokenRequest body,
            @AuthenticationPrincipal User user) {

        String rawToken = (cookieToken != null && !cookieToken.isBlank())
                ? cookieToken
                : (body != null ? body.refreshToken() : null);

        auth.logout(rawToken, user);
        ResponseCookie cleanCookie = buildClearCookie();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cleanCookie.toString())
                .body(ApiResponse.ok("Logged out successfully.", null));
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal User user) {
        return ApiResponse.ok("Profile fetched successfully.", auth.me(user));
    }

    @PutMapping("/me")
    public ApiResponse<UserResponse> update(@AuthenticationPrincipal User user,
                                            @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok("Profile updated successfully.", auth.updateProfile(user, request));
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgot(@Valid @RequestBody ForgotPasswordRequest request) {
        auth.forgotPassword(request);
        return ApiResponse.ok("If that email exists, reset instructions have been sent.", null);
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> reset(@Valid @RequestBody ResetPasswordRequest request) {
        auth.resetPassword(request);
        return ApiResponse.ok("Password reset successfully.", null);
    }

    private ResponseCookie buildRefreshTokenCookie(String token) {
        long maxAgeSeconds = jwtService.getRefreshTokenDays() * 24L * 60L * 60L;
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path(cookiePath)
                .maxAge(maxAgeSeconds)
                .build();
    }

    private ResponseCookie buildClearCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path(cookiePath)
                .maxAge(0)
                .build();
    }
}
