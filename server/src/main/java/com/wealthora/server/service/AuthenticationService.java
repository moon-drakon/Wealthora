package com.wealthora.server.service;

import com.wealthora.server.api.ApiException;
import com.wealthora.server.api.LoginRequest;
import com.wealthora.server.api.SessionResponse;
import com.wealthora.server.api.UserResponse;
import com.wealthora.server.config.SessionProperties;
import com.wealthora.server.domain.AccountStatus;
import com.wealthora.server.domain.AuditLogEntry;
import com.wealthora.server.domain.AuthProvider;
import com.wealthora.server.domain.AuthenticationIdentity;
import com.wealthora.server.domain.LoginAttempt;
import com.wealthora.server.domain.RefreshTokenRecord;
import com.wealthora.server.domain.SessionRecord;
import com.wealthora.server.domain.UserAccount;
import com.wealthora.server.domain.UserRoleAssignment;
import com.wealthora.server.repository.AuditLogRepository;
import com.wealthora.server.repository.AuthenticationIdentityRepository;
import com.wealthora.server.repository.LoginAttemptRepository;
import com.wealthora.server.repository.RefreshTokenRepository;
import com.wealthora.server.repository.SessionRecordRepository;
import com.wealthora.server.repository.UserAccountRepository;
import com.wealthora.server.repository.UserRoleRepository;
import com.wealthora.server.security.NsuEmailPolicy;
import com.wealthora.server.security.SessionPrincipal;
import com.wealthora.server.security.TokenHasher;
import java.nio.CharBuffer;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {

    private static final String GENERIC_LOGIN_FAILURE =
            "Email or password is incorrect, or the account is unavailable.";
    private static final int TOKEN_BYTES = 32;

    private final UserAccountRepository users;
    private final AuthenticationIdentityRepository identities;
    private final UserRoleRepository roles;
    private final SessionRecordRepository sessions;
    private final RefreshTokenRepository refreshTokens;
    private final LoginAttemptRepository loginAttempts;
    private final AuditLogRepository auditLogs;
    private final PasswordEncoder passwordEncoder;
    private final TokenHasher tokenHasher;
    private final SessionProperties properties;
    private final SecureRandom secureRandom;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthenticationService(
            UserAccountRepository users,
            AuthenticationIdentityRepository identities,
            UserRoleRepository roles,
            SessionRecordRepository sessions,
            RefreshTokenRepository refreshTokens,
            LoginAttemptRepository loginAttempts,
            AuditLogRepository auditLogs,
            PasswordEncoder passwordEncoder,
            TokenHasher tokenHasher,
            SessionProperties properties,
            SecureRandom secureRandom,
            Clock clock) {
        this.users = users;
        this.identities = identities;
        this.roles = roles;
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
        this.loginAttempts = loginAttempts;
        this.auditLogs = auditLogs;
        this.passwordEncoder = passwordEncoder;
        this.tokenHasher = tokenHasher;
        this.properties = properties;
        this.secureRandom = secureRandom;
        this.clock = clock;
        dummyPasswordHash = passwordEncoder.encode(
                "Wealthora-Dummy-Password-Only-1!");
    }

    @Transactional(noRollbackFor = ApiException.class)
    public SessionResponse login(LoginRequest request, String remoteAddress) {
        Instant now = clock.instant();
        String normalizedEmail = normalizeAttemptedEmail(request.email());
        UserAccount user = users.findByEmail(normalizedEmail).orElse(null);
        AuthenticationIdentity identity = user == null ? null
                : identities.findByUserIdAndProvider(
                        user.getId(), AuthProvider.PASSWORD).orElse(null);
        String expectedHash = identity == null
                ? dummyPasswordHash : identity.getPasswordHash();
        boolean passwordMatches = matchesAndClear(
                request.password(), expectedHash);
        boolean eligible = user != null && identity != null
                && passwordMatches && user.isEmailVerified()
                && user.getAccountStatus() == AccountStatus.ACTIVE
                && !user.isLockedAt(now);
        recordAttempt(user, normalizedEmail, remoteAddress, eligible, now);
        if (!eligible) {
            if (user != null && !passwordMatches) {
                user.recordFailedLogin(now,
                        properties.maximumFailedAttempts(),
                        properties.lockDuration());
                users.save(user);
            }
            audit(user, "LOGIN_FAILED", "DENIED",
                    "Credentials or account unavailable.", now);
            throw new ApiException(HttpStatus.UNAUTHORIZED,
                    "LOGIN_FAILED", GENERIC_LOGIN_FAILURE);
        }

        user.recordSuccessfulLogin(now);
        users.save(user);
        SessionResponse response = createSession(
                user, request.deviceLabel(), now);
        audit(user, "LOGIN_SUCCESS", "SUCCESS",
                "Password sign-in completed.", now);
        return response;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public SessionResponse refresh(char[] rawRefreshToken) {
        Instant now = clock.instant();
        String token = tokenTextAndClear(rawRefreshToken);
        RefreshTokenRecord refresh = refreshTokens.findByTokenHash(
                tokenHasher.hash(token)).orElseThrow(
                        AuthenticationService::invalidRefresh);
        SessionRecord session = sessions.findById(refresh.getSessionId())
                .orElseThrow(AuthenticationService::invalidRefresh);
        UserAccount user = users.findById(session.getUserId())
                .orElseThrow(AuthenticationService::invalidRefresh);
        if (!refresh.isUsableAt(now) || session.getRevokedAt() != null
                || !user.isEmailVerified()
                || user.getAccountStatus() != AccountStatus.ACTIVE) {
            session.revoke(now);
            sessions.save(session);
            throw invalidRefresh();
        }

        refresh.consume(now);
        refreshTokens.save(refresh);
        String accessToken = opaqueToken();
        String nextRefreshToken = opaqueToken();
        Instant accessExpiry = now.plus(properties.accessExpiry());
        Instant refreshExpiry = now.plus(properties.refreshExpiry());
        session.rotateAccessToken(
                tokenHasher.hash(accessToken), accessExpiry);
        sessions.save(session);
        refreshTokens.save(new RefreshTokenRecord(UUID.randomUUID(),
                session.getId(), tokenHasher.hash(nextRefreshToken),
                now, refreshExpiry));
        audit(user, "SESSION_REFRESHED", "SUCCESS",
                "Refresh token rotated.", now);
        return response(user, session, accessToken, nextRefreshToken,
                accessExpiry, refreshExpiry);
    }

    @Transactional(readOnly = true)
    public Optional<SessionPrincipal> authenticate(String rawAccessToken) {
        if (rawAccessToken == null || rawAccessToken.length() < 32
                || rawAccessToken.length() > 200) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return sessions.findByAccessTokenHash(
                        tokenHasher.hash(rawAccessToken))
                .filter(session -> session.isUsableAt(now))
                .flatMap(session -> users.findById(session.getUserId())
                        .filter(user -> user.isEmailVerified()
                                && user.getAccountStatus()
                                        == AccountStatus.ACTIVE)
                        .map(user -> new SessionPrincipal(
                                session.getId(), user.getId(),
                                assignedRoles(user.getId()))));
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(SessionPrincipal principal) {
        UserAccount user = users.findById(principal.userId())
                .orElseThrow(AuthenticationService::invalidSession);
        return UserResponse.from(user, assignedRoles(user.getId()));
    }

    @Transactional
    public void logout(SessionPrincipal principal) {
        Instant now = clock.instant();
        SessionRecord session = sessions.findById(principal.sessionId())
                .orElseThrow(AuthenticationService::invalidSession);
        session.revoke(now);
        sessions.save(session);
        users.findById(principal.userId()).ifPresent(user -> audit(
                user, "LOGOUT", "SUCCESS", "Session revoked.", now));
    }

    @Transactional
    public void logoutAll(SessionPrincipal principal) {
        Instant now = clock.instant();
        sessions.findByUserIdAndRevokedAtIsNull(principal.userId())
                .forEach(session -> {
                    session.revoke(now);
                    sessions.save(session);
                });
        users.findById(principal.userId()).ifPresent(user -> audit(
                user, "LOGOUT_ALL", "SUCCESS",
                "All sessions revoked.", now));
    }

    private SessionResponse createSession(
            UserAccount user, String deviceLabel, Instant now) {
        String accessToken = opaqueToken();
        String refreshToken = opaqueToken();
        Instant accessExpiry = now.plus(properties.accessExpiry());
        Instant refreshExpiry = now.plus(properties.refreshExpiry());
        SessionRecord session = sessions.save(new SessionRecord(
                UUID.randomUUID(), user.getId(),
                tokenHasher.hash(accessToken), now, accessExpiry,
                safeDeviceLabel(deviceLabel)));
        refreshTokens.save(new RefreshTokenRecord(UUID.randomUUID(),
                session.getId(), tokenHasher.hash(refreshToken),
                now, refreshExpiry));
        return response(user, session, accessToken, refreshToken,
                accessExpiry, refreshExpiry);
    }

    private SessionResponse response(
            UserAccount user, SessionRecord session,
            String accessToken, String refreshToken,
            Instant accessExpiry, Instant refreshExpiry) {
        return new SessionResponse(accessToken, refreshToken,
                session.getCreatedAt(), accessExpiry, refreshExpiry,
                UserResponse.from(user, assignedRoles(user.getId())));
    }

    private Set<String> assignedRoles(UUID userId) {
        return roles.findByUserId(userId).stream()
                .map(UserRoleAssignment::getRoleName)
                .collect(Collectors.toUnmodifiableSet());
    }

    private void recordAttempt(
            UserAccount user, String email, String remoteAddress,
            boolean successful, Instant now) {
        String remoteHash = remoteAddress == null || remoteAddress.isBlank()
                ? null : tokenHasher.hash(remoteAddress);
        loginAttempts.save(new LoginAttempt(UUID.randomUUID(),
                user == null ? null : user.getId(),
                tokenHasher.hash(email), successful, now, remoteHash));
    }

    private void audit(
            UserAccount user, String action, String outcome,
            String reason, Instant now) {
        auditLogs.save(new AuditLogEntry(UUID.randomUUID(), now,
                user == null ? null : user.getId(), action,
                user == null ? null : user.getId(), outcome, reason));
    }

    private boolean matchesAndClear(char[] password, String expectedHash) {
        String text = tokenTextAndClear(password);
        return passwordEncoder.matches(text, expectedHash);
    }

    private static String tokenTextAndClear(char[] value) {
        if (value == null) return "";
        String text = CharBuffer.wrap(value).toString();
        Arrays.fill(value, '\0');
        return text;
    }

    private String opaqueToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String normalizeAttemptedEmail(String email) {
        if (email == null) return "";
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        try {
            return NsuEmailPolicy.require(normalized);
        } catch (ApiException exception) {
            return normalized;
        }
    }

    private static String safeDeviceLabel(String value) {
        if (value == null || value.isBlank()) return "Wealthora Desktop";
        String normalized = value.strip();
        return normalized.length() <= 160
                ? normalized : normalized.substring(0, 160);
    }

    private static ApiException invalidRefresh() {
        return new ApiException(HttpStatus.UNAUTHORIZED,
                "REFRESH_FAILED", "The session cannot be refreshed.");
    }

    private static ApiException invalidSession() {
        return new ApiException(HttpStatus.UNAUTHORIZED,
                "SESSION_INVALID", "The session is no longer active.");
    }
}
