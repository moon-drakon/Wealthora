package com.spendwise.auth.admin;

import com.spendwise.auth.AuthConfigurationException;
import com.spendwise.auth.AuthenticatedUser;
import com.spendwise.auth.audit.AuditEvent;
import java.util.List;

public interface AdministrationGateway {

    boolean hasOnlineSession();
    AdminOverview getAdminOverview();
    List<AuthenticatedUser> listAdminUsers();
    List<AuthenticatedUser> listPendingRegistrations();
    List<AuthenticatedUser> listPendingVerifications();
    List<AuditEvent> listAdminAuditEvents();
    AdminSecurityStatus getAdminSecurityStatus();
    AdminApplicationSettings getAdminApplicationSettings();
    DatabaseHealthStatus getDatabaseHealth();
    AuthenticatedUser approveRegistration(String userIdentifier, String reason);
    AuthenticatedUser rejectRegistration(String userIdentifier, String reason);
    AuthenticatedUser activateAdminUser(String userIdentifier, String reason);
    AuthenticatedUser suspendAdminUser(String userIdentifier, String reason);
    AuthenticatedUser disableAdminUser(String userIdentifier, String reason);
    AuthenticatedUser grantAdminRole(
            String userIdentifier, char[] ownerPassword, String reason);
    AuthenticatedUser revokeAdminRole(
            String userIdentifier, char[] ownerPassword, String reason);
    AdminApplicationSettings updateAdminApplicationSettings(
            boolean approvalRequired, char[] ownerPassword, String reason);

    static AdministrationGateway unavailable() {
        return new AdministrationGateway() {
            private AuthConfigurationException unavailable() {
                return new AuthConfigurationException(
                        "Online administration requires an active server session.");
            }
            @Override public boolean hasOnlineSession() { return false; }
            @Override public AdminOverview getAdminOverview() { throw unavailable(); }
            @Override public List<AuthenticatedUser> listAdminUsers() { throw unavailable(); }
            @Override public List<AuthenticatedUser> listPendingRegistrations() { throw unavailable(); }
            @Override public List<AuthenticatedUser> listPendingVerifications() { throw unavailable(); }
            @Override public List<AuditEvent> listAdminAuditEvents() { throw unavailable(); }
            @Override public AdminSecurityStatus getAdminSecurityStatus() { throw unavailable(); }
            @Override public AdminApplicationSettings getAdminApplicationSettings() { throw unavailable(); }
            @Override public DatabaseHealthStatus getDatabaseHealth() { throw unavailable(); }
            @Override public AuthenticatedUser approveRegistration(String id, String reason) { throw unavailable(); }
            @Override public AuthenticatedUser rejectRegistration(String id, String reason) { throw unavailable(); }
            @Override public AuthenticatedUser activateAdminUser(String id, String reason) { throw unavailable(); }
            @Override public AuthenticatedUser suspendAdminUser(String id, String reason) { throw unavailable(); }
            @Override public AuthenticatedUser disableAdminUser(String id, String reason) { throw unavailable(); }
            @Override public AuthenticatedUser grantAdminRole(String id, char[] password, String reason) { throw unavailable(); }
            @Override public AuthenticatedUser revokeAdminRole(String id, char[] password, String reason) { throw unavailable(); }
            @Override public AdminApplicationSettings updateAdminApplicationSettings(boolean required, char[] password, String reason) { throw unavailable(); }
        };
    }
}
