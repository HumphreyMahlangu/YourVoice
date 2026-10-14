package io.github.humphreymahlangu.votetrust.entity;

public enum SecurityAuditEventType {
    USER_REGISTER,
    USER_LOGIN,
    ADMIN_BOOTSTRAP,
    RATE_LIMIT_BLOCKED,
    TOKEN_REFRESH,
    USER_LOGOUT,
    ADMIN_ACCOUNT_CREATED
}
