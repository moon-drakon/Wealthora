package com.spendwise.auth.audit;

import java.util.List;

public interface AuditRepository {

    void append(AuditEvent event);

    List<AuditEvent> findAll();
}
