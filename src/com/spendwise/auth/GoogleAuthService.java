package com.spendwise.auth;

/** Opens a secure system-browser flow and returns a short-lived result. */
public interface GoogleAuthService {

    GoogleAuthorization authorize();
}
