package com.wealthora.server.security;

import com.wealthora.server.api.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public final class TokenHasher {

    private final byte[] pepper;

    public TokenHasher(
            @Value("${wealthora.security.token-pepper:}") String pepper) {
        if (pepper == null || pepper.length() < 32) {
            throw new IllegalStateException(
                    "TOKEN_PEPPER must contain at least 32 characters.");
        }
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
    }

    public String hash(String token) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(
                    token.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Secure token hashing is unavailable.", exception);
        }
    }

    public boolean matches(String token, String expectedHash) {
        byte[] actual = HexFormat.of().parseHex(hash(token));
        byte[] expected;
        try {
            expected = HexFormat.of().parseHex(expectedHash);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        return MessageDigest.isEqual(actual, expected);
    }
}
