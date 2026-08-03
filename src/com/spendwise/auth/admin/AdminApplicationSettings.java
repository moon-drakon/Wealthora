package com.spendwise.auth.admin;

public record AdminApplicationSettings(
        boolean registrationRequiresAdminApproval,
        boolean editable) {
}
