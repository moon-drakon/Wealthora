package com.wealthora.server.api;

import com.wealthora.server.security.SessionPrincipal;
import com.wealthora.server.service.AdministrationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdministrationController {

    private final AdministrationService administrationService;

    public AdministrationController(
            AdministrationService administrationService) {
        this.administrationService = administrationService;
    }

    @GetMapping("/overview")
    AdminOverviewResponse overview(
            @AuthenticationPrincipal SessionPrincipal principal) {
        return administrationService.overview(principal);
    }

    @GetMapping("/users")
    List<UserResponse> users(
            @AuthenticationPrincipal SessionPrincipal principal) {
        return administrationService.listUsers(principal);
    }

    @GetMapping("/pending-registrations")
    List<UserResponse> pendingRegistrations(
            @AuthenticationPrincipal SessionPrincipal principal) {
        return administrationService.pendingRegistrations(principal);
    }

    @GetMapping("/verifications")
    List<UserResponse> verifications(
            @AuthenticationPrincipal SessionPrincipal principal) {
        return administrationService.pendingVerifications(principal);
    }

    @GetMapping("/audit-logs")
    List<AdminAuditResponse> auditLogs(
            @AuthenticationPrincipal SessionPrincipal principal) {
        return administrationService.auditEvents(principal);
    }

    @GetMapping("/security")
    AdminSecurityResponse security(
            @AuthenticationPrincipal SessionPrincipal principal) {
        return administrationService.security(principal);
    }

    @GetMapping("/settings")
    AdminSettingsResponse settings(
            @AuthenticationPrincipal SessionPrincipal principal) {
        return administrationService.applicationSettings(principal);
    }

    @PutMapping("/settings")
    AdminSettingsResponse updateSettings(
            @AuthenticationPrincipal SessionPrincipal principal,
            @Valid @RequestBody AdminSettingsRequest request) {
        return administrationService.updateSettings(principal, request);
    }

    @GetMapping("/database-health")
    DatabaseHealthResponse databaseHealth(
            @AuthenticationPrincipal SessionPrincipal principal) {
        return administrationService.databaseHealth(principal);
    }

    @PostMapping("/users/{identifier}/approve")
    UserResponse approve(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable UUID identifier,
            @Valid @RequestBody AdminActionRequest request) {
        return administrationService.approve(principal, identifier, request);
    }

    @PostMapping("/users/{identifier}/reject")
    UserResponse reject(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable UUID identifier,
            @Valid @RequestBody AdminActionRequest request) {
        return administrationService.reject(principal, identifier, request);
    }

    @PostMapping("/users/{identifier}/activate")
    UserResponse activate(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable UUID identifier,
            @Valid @RequestBody AdminActionRequest request) {
        return administrationService.activate(principal, identifier, request);
    }

    @PostMapping("/users/{identifier}/suspend")
    UserResponse suspend(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable UUID identifier,
            @Valid @RequestBody AdminActionRequest request) {
        return administrationService.suspend(principal, identifier, request);
    }

    @PostMapping("/users/{identifier}/disable")
    UserResponse disable(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable UUID identifier,
            @Valid @RequestBody AdminActionRequest request) {
        return administrationService.disable(principal, identifier, request);
    }

    @PostMapping("/users/{identifier}/grant-admin")
    UserResponse grantAdministrator(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable UUID identifier,
            @Valid @RequestBody AdminActionRequest request) {
        return administrationService.grantAdministrator(
                principal, identifier, request);
    }

    @PostMapping("/users/{identifier}/revoke-admin")
    UserResponse revokeAdministrator(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable UUID identifier,
            @Valid @RequestBody AdminActionRequest request) {
        return administrationService.revokeAdministrator(
                principal, identifier, request);
    }
}
