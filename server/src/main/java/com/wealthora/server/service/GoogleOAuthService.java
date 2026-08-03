package com.wealthora.server.service;

import com.wealthora.server.api.ApiException;
import com.wealthora.server.api.GoogleOAuthPollResponse;
import com.wealthora.server.api.GoogleOAuthStartResponse;
import com.wealthora.server.api.GoogleOAuthStatusResponse;
import com.wealthora.server.config.GoogleOAuthProperties;
import com.wealthora.server.config.RegistrationProperties;
import com.wealthora.server.domain.AccountStatus;
import com.wealthora.server.domain.AuditLogEntry;
import com.wealthora.server.domain.AuthProvider;
import com.wealthora.server.domain.AuthenticationIdentity;
import com.wealthora.server.domain.GoogleOAuthFlow;
import com.wealthora.server.domain.GoogleOAuthFlowStatus;
import com.wealthora.server.domain.UserAccount;
import com.wealthora.server.domain.UserRole;
import com.wealthora.server.domain.UserRoleAssignment;
import com.wealthora.server.oauth.GoogleIdentityGateway;
import com.wealthora.server.oauth.VerifiedGoogleIdentity;
import com.wealthora.server.repository.AuditLogRepository;
import com.wealthora.server.repository.AuthenticationIdentityRepository;
import com.wealthora.server.repository.GoogleOAuthFlowRepository;
import com.wealthora.server.repository.UserAccountRepository;
import com.wealthora.server.repository.UserRoleRepository;
import com.wealthora.server.security.NsuEmailPolicy;
import com.wealthora.server.security.TokenHasher;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleOAuthService {

    private static final int RANDOM_BYTES = 32;
    private static final String DOMAIN = "northsouth.edu";
    private final GoogleOAuthProperties properties;
    private final RegistrationProperties registrationProperties;
    private final GoogleIdentityGateway gateway;
    private final GoogleOAuthFlowRepository flows;
    private final UserAccountRepository users;
    private final AuthenticationIdentityRepository identities;
    private final UserRoleRepository roles;
    private final AuditLogRepository auditLogs;
    private final AuthenticationService authenticationService;
    private final TokenHasher tokenHasher;
    private final SecureRandom secureRandom;
    private final Clock clock;

    public GoogleOAuthService(
            GoogleOAuthProperties properties,
            RegistrationProperties registrationProperties,
            GoogleIdentityGateway gateway,
            GoogleOAuthFlowRepository flows,
            UserAccountRepository users,
            AuthenticationIdentityRepository identities,
            UserRoleRepository roles,
            AuditLogRepository auditLogs,
            AuthenticationService authenticationService,
            TokenHasher tokenHasher,
            SecureRandom secureRandom,
            Clock clock) {
        this.properties = properties;
        this.registrationProperties = registrationProperties;
        this.gateway = gateway;
        this.flows = flows;
        this.users = users;
        this.identities = identities;
        this.roles = roles;
        this.auditLogs = auditLogs;
        this.authenticationService = authenticationService;
        this.tokenHasher = tokenHasher;
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    public GoogleOAuthStatusResponse status() {
        return new GoogleOAuthStatusResponse(gateway.isConfigured(),
                gateway.configurationMessage(),
                gateway.isConfigured() ? gateway.redirectUri() : "");
    }

    @Transactional
    public GoogleOAuthStartResponse start(String deviceLabel) {
        if (!gateway.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "GOOGLE_OAUTH_NOT_CONFIGURED",
                    gateway.configurationMessage());
        }
        String state = randomToken();
        String nonce = randomToken();
        String pollSecret = randomToken();
        Instant now = clock.instant();
        GoogleOAuthFlow flow = flows.save(new GoogleOAuthFlow(
                UUID.randomUUID(), tokenHasher.hash(pollSecret),
                tokenHasher.hash(state), tokenHasher.hash(nonce),
                safeDeviceLabel(deviceLabel), now,
                now.plus(properties.flowExpiry())));
        return new GoogleOAuthStartResponse(flow.getId().toString(),
                pollSecret, gateway.authorizationUrl(state, nonce),
                flow.getExpiresAt());
    }

    @Transactional
    public GoogleOAuthCallbackResult callback(
            String state, String code, String providerError) {
        Instant now = clock.instant();
        GoogleOAuthFlow flow = findPendingState(state, now);
        if (providerError != null && !providerError.isBlank()) {
            flow.fail("Google authorization was cancelled or denied.", now);
            flows.save(flow);
            return failed(flow.getFailureMessage());
        }
        if (code == null || code.isBlank()) {
            flow.fail("Google did not return an authorization code.", now);
            flows.save(flow);
            return failed(flow.getFailureMessage());
        }
        try {
            VerifiedGoogleIdentity identity =
                    gateway.exchangeAndVerify(code.strip());
            validate(identity, flow, now);
            LinkOutcome outcome = link(identity, now);
            if (!outcome.eligible()) {
                flow.fail(outcome.message(), now);
                flows.save(flow);
                return failed(outcome.message());
            }
            flow.complete(outcome.user().getId(), now);
            flows.save(flow);
            return new GoogleOAuthCallbackResult(true,
                    "Google sign-in completed",
                    "Return to Wealthora to finish signing in.");
        } catch (ApiException exception) {
            flow.fail(exception.getMessage(), now);
            flows.save(flow);
            return failed(exception.getMessage());
        } catch (RuntimeException exception) {
            flow.fail("Google authorization could not be verified safely.", now);
            flows.save(flow);
            return failed(flow.getFailureMessage());
        }
    }

    @Transactional
    public GoogleOAuthPollResponse poll(
            String rawIdentifier, String pollSecret) {
        UUID identifier;
        try {
            identifier = UUID.fromString(rawIdentifier);
        } catch (IllegalArgumentException exception) {
            throw invalidFlow();
        }
        if (pollSecret == null || pollSecret.length() < 32
                || pollSecret.length() > 200) {
            throw invalidFlow();
        }
        GoogleOAuthFlow flow = flows.findForPoll(identifier,
                tokenHasher.hash(pollSecret)).orElseThrow(
                        GoogleOAuthService::invalidFlow);
        Instant now = clock.instant();
        if (flow.getStatus() == GoogleOAuthFlowStatus.PENDING
                && !flow.getExpiresAt().isAfter(now)) {
            flow.fail("Google sign-in expired. Start again.", now);
            flows.save(flow);
        }
        if (flow.getStatus() == GoogleOAuthFlowStatus.PENDING) {
            return new GoogleOAuthPollResponse("PENDING",
                    "Waiting for browser authorization.", null);
        }
        if (flow.getStatus() == GoogleOAuthFlowStatus.FAILED) {
            return new GoogleOAuthPollResponse("FAILED",
                    flow.getFailureMessage(), null);
        }
        if (flow.getStatus() == GoogleOAuthFlowStatus.CONSUMED) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "GOOGLE_OAUTH_ALREADY_USED",
                    "This Google sign-in result has already been used.");
        }
        var session = authenticationService.createGoogleSession(
                flow.getUserId(), flow.getDeviceLabel());
        flow.consume(now);
        flows.save(flow);
        return new GoogleOAuthPollResponse("COMPLETED",
                "Google sign-in completed.", session);
    }

    private GoogleOAuthFlow findPendingState(String state, Instant now) {
        if (state == null || state.length() < 32 || state.length() > 200) {
            throw invalidState();
        }
        GoogleOAuthFlow flow = flows.findByStateHash(tokenHasher.hash(state))
                .orElseThrow(GoogleOAuthService::invalidState);
        if (!flow.isPendingAt(now)) throw invalidState();
        return flow;
    }

    private void validate(
            VerifiedGoogleIdentity identity,
            GoogleOAuthFlow flow,
            Instant now) {
        if (identity == null
                || !("https://accounts.google.com".equals(identity.issuer())
                || "accounts.google.com".equals(identity.issuer()))
                || !identity.audience().contains(properties.clientId())
                || identity.expiresAt() == null
                || !identity.expiresAt().isAfter(now)) {
            throw denied("Google returned an invalid identity token.");
        }
        if (identity.nonce() == null || !tokenHasher.matches(
                identity.nonce(), flow.getNonceHash())) {
            throw denied("Google sign-in nonce validation failed.");
        }
        if (!identity.emailVerified()) {
            throw denied("Google has not verified this email address.");
        }
        String email = NsuEmailPolicy.require(identity.email());
        if (!DOMAIN.equalsIgnoreCase(clean(identity.hostedDomain()))) {
            throw denied("Use a Google Workspace account for northsouth.edu.");
        }
        if (identity.subject() == null || identity.subject().isBlank()
                || identity.subject().length() > 255 || email.isBlank()) {
            throw denied("Google returned an incomplete identity.");
        }
    }

    private LinkOutcome link(VerifiedGoogleIdentity identity, Instant now) {
        String email = NsuEmailPolicy.require(identity.email());
        AuthenticationIdentity subjectIdentity = identities
                .findByProviderAndProviderSubject(
                        AuthProvider.GOOGLE, identity.subject()).orElse(null);
        UserAccount user = subjectIdentity == null ? null
                : users.findById(subjectIdentity.getUserId()).orElseThrow(
                        () -> denied("The linked Google account is unavailable."));
        if (user != null && !user.getEmail().equals(email)) {
            throw denied("The Google identity does not match its linked Wealthora account.");
        }
        if (user == null) {
            user = users.findByEmail(email).orElse(null);
            if (user != null) {
                AuthenticationIdentity existingGoogle = identities
                        .findByUserIdAndProvider(
                                user.getId(), AuthProvider.GOOGLE).orElse(null);
                if (existingGoogle != null && !identity.subject().equals(
                        existingGoogle.getProviderSubject())) {
                    throw denied("This Wealthora account is linked to another Google identity.");
                }
            }
        }
        if (user != null && (user.getAccountStatus() == AccountStatus.SUSPENDED
                || user.getAccountStatus() == AccountStatus.DISABLED)) {
            throw denied("This Wealthora account is unavailable.");
        }
        boolean created = user == null;
        if (created) {
            UUID userId = UUID.randomUUID();
            user = new UserAccount(userId, safeName(identity.fullName(), email),
                    email, studentId(email),
                    AccountStatus.PENDING_EMAIL_VERIFICATION, now);
            AccountStatus next = registrationProperties.requiresAdminApproval()
                    ? AccountStatus.PENDING_APPROVAL : AccountStatus.ACTIVE;
            user.verifyEmail(next, now);
            users.save(user);
            roles.save(new UserRoleAssignment(userId, UserRole.USER));
        } else if (!user.isEmailVerified()
                && user.getAccountStatus()
                        == AccountStatus.PENDING_EMAIL_VERIFICATION) {
            AccountStatus next = registrationProperties.requiresAdminApproval()
                    ? AccountStatus.PENDING_APPROVAL : AccountStatus.ACTIVE;
            user.verifyEmail(next, now);
            users.save(user);
        }
        if (subjectIdentity == null && identities.findByUserIdAndProvider(
                user.getId(), AuthProvider.GOOGLE).isEmpty()) {
            identities.save(new AuthenticationIdentity(UUID.randomUUID(),
                    user.getId(), AuthProvider.GOOGLE,
                    identity.subject(), null, now));
            audit(user, created ? "GOOGLE_REGISTRATION_CREATED"
                    : "GOOGLE_IDENTITY_LINKED", "SUCCESS",
                    created ? "Verified Google account created."
                            : "Google identity linked by verified NSU email.", now);
        }
        boolean eligible = user.isEmailVerified()
                && user.getAccountStatus() == AccountStatus.ACTIVE;
        return new LinkOutcome(user, eligible, eligible
                ? "Google identity verified."
                : "Google account is linked and awaiting administrator approval.");
    }

    private void audit(
            UserAccount user, String action, String outcome,
            String reason, Instant now) {
        auditLogs.save(new AuditLogEntry(UUID.randomUUID(), now,
                user.getId(), action, user.getId(), outcome, reason));
    }

    private String randomToken() {
        byte[] bytes = new byte[RANDOM_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String safeDeviceLabel(String value) {
        if (value == null || value.isBlank()) return "Wealthora Desktop";
        String clean = value.strip();
        return clean.length() <= 160 ? clean : clean.substring(0, 160);
    }

    private static String safeName(String value, String email) {
        String clean = value == null ? "" : value.strip();
        if (clean.isEmpty()) clean = studentId(email);
        return clean.length() <= 160 ? clean : clean.substring(0, 160);
    }

    private static String studentId(String email) {
        return email.substring(0, email.indexOf('@'));
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private static GoogleOAuthCallbackResult failed(String message) {
        return new GoogleOAuthCallbackResult(false,
                "Google sign-in was not completed", message);
    }

    private static ApiException invalidState() {
        return new ApiException(HttpStatus.BAD_REQUEST,
                "GOOGLE_OAUTH_STATE_INVALID",
                "This Google sign-in request is invalid or expired.");
    }

    private static ApiException invalidFlow() {
        return new ApiException(HttpStatus.UNAUTHORIZED,
                "GOOGLE_OAUTH_FLOW_INVALID",
                "This Google sign-in request is invalid or expired.");
    }

    private static ApiException denied(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED,
                "GOOGLE_IDENTITY_REJECTED", message);
    }

    private record LinkOutcome(
            UserAccount user, boolean eligible, String message) {
    }
}
