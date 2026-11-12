package com.cosmetics.ecommerce.service;

import com.cosmetics.ecommerce.dto.AuthDtos;
import com.cosmetics.ecommerce.dto.ProfileDtos.ForgotPasswordRequest;
import com.cosmetics.ecommerce.dto.ProfileDtos.ResetPasswordRequest;
import com.cosmetics.ecommerce.dto.ProfileDtos.UpdateProfileRequest;
import com.cosmetics.ecommerce.dto.UserResponse;
import com.cosmetics.ecommerce.email.EmailService;
import com.cosmetics.ecommerce.entity.RefreshToken;
import com.cosmetics.ecommerce.entity.User;
import com.cosmetics.ecommerce.enums.AuditAction;
import com.cosmetics.ecommerce.enums.AuditResult;
import com.cosmetics.ecommerce.enums.Role;
import com.cosmetics.ecommerce.exception.DuplicateResourceException;
import com.cosmetics.ecommerce.exception.ResourceNotFoundException;
import com.cosmetics.ecommerce.exception.UnauthorizedException;
import com.cosmetics.ecommerce.repository.PasswordResetTokenRepository;
import com.cosmetics.ecommerce.repository.RefreshTokenRepository;
import com.cosmetics.ecommerce.repository.UserRepository;
import com.cosmetics.ecommerce.security.JwtService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {
    private final GoogleTokenService googleTokenService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PasswordResetTokenRepository resetTokens;
    private final EmailService emailService;
    private final ReferralService referralService;
    private final AuditLogService auditLogService;
    private final RefreshTokenRepository refreshTokenRepository;

    @Autowired
    public AuthService(GoogleTokenService googleTokenService,
                       UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       PasswordResetTokenRepository resetTokens,
                       EmailService emailService,
                       ReferralService referralService,
                       @Autowired(required = false) AuditLogService auditLogService,
                       @Autowired(required = false) RefreshTokenRepository refreshTokenRepository) {
        this.googleTokenService = googleTokenService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.resetTokens = resetTokens;
        this.emailService = emailService;
        this.referralService = referralService;
        this.auditLogService = auditLogService;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    public AuthService(GoogleTokenService googleTokenService, UserRepository userRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       PasswordResetTokenRepository resetTokens, EmailService emailService,
                       ReferralService referralService) {
        this(googleTokenService, userRepository, passwordEncoder, jwtService, resetTokens, emailService, referralService, null, null);
    }

    public AuthService(GoogleTokenService googleTokenService, UserRepository userRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       PasswordResetTokenRepository resetTokens, EmailService emailService,
                       ReferralService referralService, RefreshTokenRepository refreshTokenRepository) {
        this(googleTokenService, userRepository, passwordEncoder, jwtService, resetTokens, emailService, referralService, null, refreshTokenRepository);
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("Email is already registered.");
        }
        String firstName = request.effectiveFirstName();
        String lastName = request.effectiveLastName();
        String fullName = request.effectiveFullName();
        User user = new User(firstName, lastName, email, passwordEncoder.encode(request.password()), Role.CUSTOMER);
        user.setFullName(fullName);
        user.setPhoneNumber(request.phoneNumber());
        userRepository.save(user);

        if (request.referralCode() != null && !request.referralCode().isBlank()) {
            referralService.linkReferralOnRegister(user, request.referralCode());
        }

        emailService.sendWelcome(user.getEmail(), user.getFirstName());

        if (auditLogService != null) {
            auditLogService.logWithActor(user.getId(), user.getEmail(), user.getRole().name(),
                    AuditAction.REGISTRATION_SUCCESS, AuditResult.SUCCESS, "USER", String.valueOf(user.getId()),
                    "Customer registration successful", null, "{\"email\":\"" + user.getEmail() + "\",\"role\":\"" + user.getRole() + "\"}");
        }

        return issueTokens(user);
    }

    @Transactional
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        String email = request.email() != null ? request.email().trim() : "";
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);
        if (userOpt.isEmpty()) {
            if (auditLogService != null) {
                auditLogService.logWithActor(null, email, null,
                        AuditAction.LOGIN_FAILURE, AuditResult.FAILURE, "USER", null,
                        "Login failed: User not found", null, null);
            }
            throw new UnauthorizedException("Invalid email or password.");
        }
        User user = userOpt.get();
        if (!user.isEnabled()) {
            if (auditLogService != null) {
                auditLogService.logWithActor(user.getId(), user.getEmail(), user.getRole().name(),
                        AuditAction.LOGIN_FAILURE, AuditResult.FAILURE, "USER", String.valueOf(user.getId()),
                        "Login failed: Account deactivated", null, null);
            }
            throw new UnauthorizedException("This account has been deactivated.");
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            if (auditLogService != null) {
                auditLogService.logWithActor(user.getId(), user.getEmail(), user.getRole().name(),
                        AuditAction.LOGIN_FAILURE, AuditResult.FAILURE, "USER", String.valueOf(user.getId()),
                        "Login failed: Incorrect password", null, null);
            }
            throw new UnauthorizedException("Invalid email or password.");
        }

        if (auditLogService != null) {
            auditLogService.logWithActor(user.getId(), user.getEmail(), user.getRole().name(),
                    AuditAction.LOGIN_SUCCESS, AuditResult.SUCCESS, "USER", String.valueOf(user.getId()),
                    "User logged in successfully", null, null);
        }

        return issueTokens(user);
    }

    @Transactional
    public AuthDtos.AuthResponse googleLogin(String credential) {
        if (credential == null || credential.isBlank()) {
            if (auditLogService != null) {
                auditLogService.logSecurityFailure(AuditAction.LOGIN_FAILURE, null, "Google login failed: Credential is required");
            }
            throw new UnauthorizedException("Google credential is required.");
        }

        final GoogleIdToken.Payload payload;

        try {
            payload = googleTokenService.verify(credential);
        } catch (GeneralSecurityException | IOException | IllegalArgumentException ex) {
            if (auditLogService != null) {
                auditLogService.logSecurityFailure(AuditAction.LOGIN_FAILURE, null, "Google login failed: Invalid Google token");
            }
            throw new UnauthorizedException("Invalid Google credential.");
        }

        String googleId = payload.getSubject();
        String email = payload.getEmail().trim().toLowerCase();

        Optional<User> linkedUser = userRepository.findByGoogleId(googleId);

        if (linkedUser.isPresent()) {
            User user = linkedUser.get();

            if (!user.isEnabled()) {
                if (auditLogService != null) {
                    auditLogService.logWithActor(user.getId(), user.getEmail(), user.getRole().name(),
                            AuditAction.LOGIN_FAILURE, AuditResult.FAILURE, "USER", String.valueOf(user.getId()),
                            "Google login failed: Account is deactivated", null, null);
                }
                throw new UnauthorizedException("This account has been deactivated.");
            }

            if (auditLogService != null) {
                auditLogService.logWithActor(user.getId(), user.getEmail(), user.getRole().name(),
                        AuditAction.LOGIN_SUCCESS, AuditResult.SUCCESS, "USER", String.valueOf(user.getId()),
                        "Google login successful", null, null);
            }
            return issueTokens(user);
        }

        Optional<User> existingUser = userRepository.findByEmailIgnoreCase(email);

        if (existingUser.isPresent()) {
            if (auditLogService != null) {
                auditLogService.logWithActor(existingUser.get().getId(), email, existingUser.get().getRole().name(),
                        AuditAction.LOGIN_FAILURE, AuditResult.FAILURE, "USER", String.valueOf(existingUser.get().getId()),
                        "Google login failed: Account already exists with password", null, null);
            }
            throw new UnauthorizedException(
                    "An account already exists with this email. Please sign in using your existing login method."
            );
        }

        String firstName = googleName(payload, "given_name");
        String lastName = googleName(payload, "family_name");

        if (firstName.isBlank()) firstName = "Google";
        if (lastName.isBlank()) lastName = "User";

        User user = new User(
                firstName,
                lastName,
                email,
                passwordEncoder.encode(UUID.randomUUID().toString()),
                Role.CUSTOMER
        );

        user.setGoogleId(googleId);
        user.setFullName((firstName + " " + lastName).trim());

        String picture = googleName(payload, "picture");
        if (!picture.isBlank()) {
            user.setProfileImage(picture);
        }

        userRepository.save(user);

        if (auditLogService != null) {
            auditLogService.logWithActor(user.getId(), user.getEmail(), user.getRole().name(),
                    AuditAction.REGISTRATION_SUCCESS, AuditResult.SUCCESS, "USER", String.valueOf(user.getId()),
                    "Google registration and login successful", null, "{\"email\":\"" + user.getEmail() + "\"}");
        }

        return issueTokens(user);
    }

    private String googleName(GoogleIdToken.Payload payload, String key) {
        Object value = payload.get(key);
        return value instanceof String text ? text.trim() : "";
    }

    public UserResponse me(User user) {
        if (user == null) {
            throw new UnauthorizedException("Authentication required.");
        }
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateProfile(User user, UpdateProfileRequest request) {
        if (user == null) {
            throw new UnauthorizedException("Authentication required.");
        }
        User managed = userRepository.findById(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        String prev = "{\"firstName\":\"" + managed.getFirstName() + "\",\"lastName\":\"" + managed.getLastName() + "\"}";

        if (request.firstName() != null && !request.firstName().isBlank()) {
            managed.setFirstName(request.firstName().trim());
        }
        if (request.lastName() != null && !request.lastName().isBlank()) {
            managed.setLastName(request.lastName().trim());
        }
        if (request.fullName() != null && !request.fullName().isBlank()) {
            managed.setFullName(request.fullName().trim());
        }
        if (request.phoneNumber() != null) {
            managed.setPhoneNumber(request.phoneNumber());
        }
        if (request.profileImage() != null) {
            managed.setProfileImage(request.profileImage());
        }

        String next = "{\"firstName\":\"" + managed.getFirstName() + "\",\"lastName\":\"" + managed.getLastName() + "\"}";

        if (auditLogService != null) {
            auditLogService.logWithActor(managed.getId(), managed.getEmail(), managed.getRole().name(),
                    AuditAction.USER_PROFILE_UPDATE, AuditResult.SUCCESS, "USER", String.valueOf(managed.getId()),
                    "User profile updated", prev, next);
        }

        return UserResponse.from(managed);
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = request.email() != null ? request.email().trim() : "";
        if (auditLogService != null) {
            auditLogService.logEvent(AuditAction.PASSWORD_RESET_REQUEST, AuditResult.SUCCESS, "USER", null,
                    "Password reset requested for email: " + email.toLowerCase(), null, null);
        }
        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            resetTokens.deleteByUserId(user.getId());
            String token = UUID.randomUUID().toString();
            resetTokens.save(new com.cosmetics.ecommerce.entity.PasswordResetToken(token, user, Instant.now().plus(30, ChronoUnit.MINUTES)));
            emailService.sendPasswordReset(user.getEmail(), token);
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        var tokenOpt = resetTokens.findByToken(request.token());
        if (tokenOpt.isEmpty() || tokenOpt.get().expired()) {
            if (auditLogService != null) {
                auditLogService.logSecurityFailure(AuditAction.PASSWORD_RESET_FAILURE, null,
                        "Password reset failed: Invalid or expired token");
            }
            throw new UnauthorizedException("Reset token is invalid or expired.");
        }
        var token = tokenOpt.get();
        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        resetTokens.delete(token);

        if (auditLogService != null) {
            auditLogService.logWithActor(user.getId(), user.getEmail(), user.getRole().name(),
                    AuditAction.PASSWORD_RESET_SUCCESS, AuditResult.SUCCESS, "USER", String.valueOf(user.getId()),
                    "Password reset completed successfully", null, null);
        }
    }

    /**
     * Strict Refresh Token Rotation (RTR) with Reuse Detection.
     * Validates the presented refresh token against TiDB.
     * If an already revoked token is used, invalidates ALL active tokens for that user (Replay attack detection).
     * On success, revokes the old token, issues a brand-new token pair, and persists the new token in TiDB.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthDtos.AuthResponse rotateRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new UnauthorizedException("Refresh token is required.");
        }

        if (refreshTokenRepository != null) {
            Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByToken(rawRefreshToken);

            if (tokenOpt.isPresent()) {
                RefreshToken existingToken = tokenOpt.get();
                User user = existingToken.getUser();
                Long userId = user.getId();
                String userEmail = user.getEmail();

                // 1. REUSE DETECTION (Replay Attack Prevention)
                if (existingToken.isRevoked()) {
                    refreshTokenRepository.revokeAllActiveTokensByUser(user);
                    if (userId != null) {
                        refreshTokenRepository.revokeAllActiveTokensByUserId(userId);
                    }
                    if (auditLogService != null) {
                        auditLogService.logSecurityFailure(
                                AuditAction.SECURITY_AUTHENTICATION_FAILURE,
                                String.valueOf(userId),
                                "CRITICAL: Refresh token reuse detected! Revoked all sessions for user: " + userEmail
                        );
                    }
                    throw new UnauthorizedException("Security alert: Refresh token reuse detected. All sessions terminated.");
                }

                // 2. Expiry check
                if (existingToken.isExpired()) {
                    existingToken.setRevoked(true);
                    refreshTokenRepository.save(existingToken);
                    throw new UnauthorizedException("Refresh token has expired. Please log in again.");
                }

                // 3. User account state check
                if (!user.isEnabled()) {
                    existingToken.setRevoked(true);
                    refreshTokenRepository.save(existingToken);
                    throw new UnauthorizedException("This account has been deactivated.");
                }

                // 4. Cryptographic JWT check
                if (!jwtService.isRefreshToken(rawRefreshToken) || !jwtService.isValid(rawRefreshToken, user)) {
                    existingToken.setRevoked(true);
                    refreshTokenRepository.save(existingToken);
                    throw new UnauthorizedException("Refresh token cryptographic validation failed.");
                }

                // 5. ROTATE: Revoke old token
                existingToken.setRevoked(true);
                refreshTokenRepository.save(existingToken);

                // 6. Issue brand-new token pair
                String newAccessToken = jwtService.accessToken(user);
                String newRefreshToken = jwtService.refreshToken(user);

                RefreshToken newEntity = new RefreshToken(
                        newRefreshToken,
                        user,
                        Instant.now().plus(jwtService.getRefreshTokenDays(), ChronoUnit.DAYS)
                );
                refreshTokenRepository.save(newEntity);

                if (auditLogService != null) {
                    auditLogService.logWithActor(user.getId(), user.getEmail(), user.getRole().name(),
                            AuditAction.LOGIN_SUCCESS, AuditResult.SUCCESS, "USER", String.valueOf(user.getId()),
                            "Refresh token rotated successfully", null, null);
                }

                return new AuthDtos.AuthResponse(newAccessToken, newRefreshToken, UserResponse.from(user));
            }
        }

        // Fallback for stateless validation if repository is unavailable (e.g. mock test context)
        if (!jwtService.isRefreshToken(rawRefreshToken)) {
            throw new UnauthorizedException("Refresh token is invalid or expired.");
        }
        String email;
        try {
            email = jwtService.subject(rawRefreshToken);
        } catch (RuntimeException ex) {
            throw new UnauthorizedException("Refresh token is invalid or expired.");
        }
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UnauthorizedException("Refresh token is invalid."));
        if (!user.isEnabled()) {
            throw new UnauthorizedException("This account has been deactivated.");
        }
        if (!jwtService.isValid(rawRefreshToken, user)) {
            throw new UnauthorizedException("Refresh token is invalid or expired.");
        }

        return issueTokens(user);
    }

    /**
     * Backward-compatible refresh endpoint handler.
     */
    @Transactional
    public AuthDtos.AuthResponse refresh(AuthDtos.RefreshTokenRequest request) {
        if (request == null || request.refreshToken() == null) {
            throw new UnauthorizedException("Refresh token is required.");
        }
        return rotateRefreshToken(request.refreshToken());
    }

    /**
     * Revokes a session upon user logout.
     */
    @Transactional
    public void logout(String rawRefreshToken, User authenticatedUser) {
        if (refreshTokenRepository != null) {
            if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
                refreshTokenRepository.findByToken(rawRefreshToken).ifPresent(rt -> {
                    rt.setRevoked(true);
                    refreshTokenRepository.save(rt);
                });
            } else if (authenticatedUser != null) {
                refreshTokenRepository.revokeAllActiveTokensByUser(authenticatedUser);
            }
        }

        if (auditLogService != null && authenticatedUser != null) {
            auditLogService.logWithActor(authenticatedUser.getId(), authenticatedUser.getEmail(),
                    authenticatedUser.getRole().name(), AuditAction.LOGOUT, AuditResult.SUCCESS,
                    "USER", String.valueOf(authenticatedUser.getId()), "User logged out successfully", null, null);
        }
    }

    /**
     * Helper to issue access and refresh tokens and persist the refresh token in TiDB.
     */
    private AuthDtos.AuthResponse issueTokens(User user) {
        String accessToken = jwtService.accessToken(user);
        String refreshToken = jwtService.refreshToken(user);

        if (refreshTokenRepository != null) {
            RefreshToken entity = new RefreshToken(
                    refreshToken,
                    user,
                    Instant.now().plus(jwtService.getRefreshTokenDays(), ChronoUnit.DAYS)
            );
            refreshTokenRepository.save(entity);
        }

        return new AuthDtos.AuthResponse(accessToken, refreshToken, UserResponse.from(user));
    }
}
