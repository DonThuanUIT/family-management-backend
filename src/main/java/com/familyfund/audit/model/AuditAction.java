package com.familyfund.audit.model;

/**
 * Action types supported in system audit logs.
 */
public enum AuditAction {
    ASSIGN,
    REVOKE,
    APPROVE,
    REJECT,
    CREATE,
    UPDATE,
    DELETE,
    STATUS_CHANGE,
    ASSIGN_PERSONNEL
}
