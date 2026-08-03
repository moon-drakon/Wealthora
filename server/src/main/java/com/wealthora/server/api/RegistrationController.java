package com.wealthora.server.api;

import com.wealthora.server.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(registrationService.register(request));
    }

    @PostMapping("/verify-email")
    UserResponse verify(@Valid @RequestBody VerifyEmailRequest request) {
        return registrationService.verify(request.email(), request.code());
    }

    @PostMapping("/resend-verification")
    ResponseEntity<Void> resend(@Valid @RequestBody EmailRequest request) {
        registrationService.resend(request.email());
        return ResponseEntity.accepted().build();
    }
}
