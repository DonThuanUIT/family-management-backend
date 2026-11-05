package com.familyfund.audit.annotation;

import com.familyfund.audit.model.AuditAction;
import com.familyfund.audit.model.AuditEntityType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation placed on service mutation methods to automatically record an audit trail.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /**
     * Action type performed (CREATE, UPDATE, DELETE, APPROVE, REJECT, STATUS_CHANGE, etc.)
     */
    AuditAction action();

    /**
     * Entity category being mutated (BRANCH, MEMBER, EVENT, etc.)
     */
    AuditEntityType entityType();

    /**
     * SpEL expression to extract entity ID from method arguments or return value.
     * Examples: "#branchId", "#requestId", "#result.eventId"
     */
    String entityId() default "";

    /**
     * Descriptive note or template for the audit log.
     */
    String description() default "";
}
