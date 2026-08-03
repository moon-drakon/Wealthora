package com.wealthora.server.api;

import com.wealthora.server.security.SessionPrincipal;
import com.wealthora.server.service.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {

    private final AuthenticationService authenticationService;

    public AuthenticationController(
            AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    SessionResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        return authenticationService.login(
                request, httpRequest.getRemoteAddr());
    }

    @PostMapping("/refresh")
    SessionResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authenticationService.refresh(request.refreshToken());
    }

    @GetMapping("/me")
    UserResponse currentUser(
            @AuthenticationPrincipal SessionPrincipal principal) {
        return authenticationService.currentUser(principal);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(
            @AuthenticationPrincipal SessionPrincipal principal) {
        authenticationService.logout(principal);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    ResponseEntity<Void> logoutAll(
            @AuthenticationPrincipal SessionPrincipal principal) {
        authenticationService.logoutAll(principal);
        return ResponseEntity.noContent().build();
    }
}
