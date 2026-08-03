package com.wealthora.server.oauth;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.wealthora.server.config.GoogleOAuthProperties;
import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class OfficialGoogleIdentityGateway
        implements GoogleIdentityGateway {

    private static final List<String> SCOPES =
            List.of("openid", "email", "profile");
    private final GoogleOAuthProperties properties;
    private final NetHttpTransport transport = new NetHttpTransport();
    private final GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

    public OfficialGoogleIdentityGateway(GoogleOAuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isConfigured() {
        return properties.isConfigured();
    }

    @Override
    public String configurationMessage() {
        return properties.isConfigured()
                ? "Google browser sign-in is ready."
                : properties.configurationProblem();
    }

    @Override
    public String redirectUri() {
        return properties.redirectUri();
    }

    @Override
    public String authorizationUrl(String state, String nonce) {
        if (!isConfigured()) {
            throw new IllegalStateException(configurationMessage());
        }
        return new GoogleAuthorizationCodeRequestUrl(
                properties.clientId(), properties.redirectUri(), SCOPES)
                .setState(state)
                .set("nonce", nonce)
                .set("hd", "northsouth.edu")
                .set("prompt", "select_account")
                .build();
    }

    @Override
    public VerifiedGoogleIdentity exchangeAndVerify(String code) {
        try {
            GoogleTokenResponse token = new GoogleAuthorizationCodeTokenRequest(
                    transport, jsonFactory, properties.clientId(),
                    properties.clientSecret(), code,
                    properties.redirectUri()).execute();
            GoogleIdToken idToken = token.parseIdToken();
            GoogleIdTokenVerifier verifier =
                    new GoogleIdTokenVerifier.Builder(transport, jsonFactory)
                            .setAudience(List.of(properties.clientId())).build();
            if (idToken == null || !verifier.verify(idToken)) {
                throw new IllegalStateException(
                        "Google returned an invalid identity token.");
            }
            GoogleIdToken.Payload payload = idToken.getPayload();
            return new VerifiedGoogleIdentity(payload.getSubject(),
                    payload.getEmail(), Boolean.TRUE.equals(
                            payload.getEmailVerified()),
                    payload.getHostedDomain(), text(payload.get("name")),
                    text(payload.get("nonce")), payload.getIssuer(),
                    audience(payload.getAudience()),
                    Instant.ofEpochSecond(payload.getExpirationTimeSeconds()));
        } catch (IOException | java.security.GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Google authorization could not be verified.", exception);
        }
    }

    private static List<String> audience(Object value) {
        if (value instanceof Collection<?> values) {
            return values.stream().map(Object::toString).toList();
        }
        return value == null ? List.of() : List.of(value.toString());
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
