package com.wealthora.server.oauth;

public interface GoogleIdentityGateway {

    boolean isConfigured();

    String configurationMessage();

    String redirectUri();

    String authorizationUrl(String state, String nonce);

    VerifiedGoogleIdentity exchangeAndVerify(String authorizationCode);
}
