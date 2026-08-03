package com.wealthora.server.api;

import com.wealthora.server.security.SessionPrincipal;
import com.wealthora.server.service.PasswordRecoveryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class PasswordSecurityController {

    private final PasswordRecoveryService passwordRecoveryService;

    public PasswordSecurityController(
            PasswordRecoveryService passwordRecoveryService) {
        this.passwordRecoveryService = passwordRecoveryService;
    }

    @PostMapping("/forgot-password")
    ResponseEntity<Void> forgotPassword(
            @Valid @RequestBody EmailRequest request) {
        passwordRecoveryService.requestReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset-password")
    ResponseEntity<Void> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        passwordRecoveryService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal SessionPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        passwordRecoveryService.changePassword(principal, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/set-password")
    ResponseEntity<Void> setPassword(
            @AuthenticationPrincipal SessionPrincipal principal,
            @Valid @RequestBody SetPasswordRequest request) {
        passwordRecoveryService.setPassword(principal, request);
        return ResponseEntity.noContent().build();
    }
}
