package com.miqu3iasg.banking.auth.domain;

public enum Permission {
    USER_READ,
    USER_WRITE,
    USER_DELETE,
    USER_IMPERSONATE,
    ACCOUNT_READ,
    ACCOUNT_WRITE,
    TRANSACTION_READ,
    TRANSACTION_WRITE,
    AUDIT_READ,
    ADMIN_MANAGE,
    SERVICE_INVOKE
}
