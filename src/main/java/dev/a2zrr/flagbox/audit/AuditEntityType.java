package dev.a2zrr.flagbox.audit;

/** The kinds of entity whose changes are recorded in the audit log. */
public enum AuditEntityType {
    PROJECT,
    ENVIRONMENT,
    FLAG,
    FLAG_CONFIG,
    API_KEY
}
