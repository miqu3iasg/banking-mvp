-- V8: Create authentication and authorization schema
-- Enterprise-grade auth infrastructure for banking application

-- Roles table
CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(30) NOT NULL UNIQUE,
    description VARCHAR(200),
    permissions TEXT[],
    mfa_required BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX idx_roles_name ON roles(name);

-- Insert default roles
INSERT INTO roles (name, description, permissions, mfa_required) VALUES
    ('ROLE_USER', 'Standard user with basic access', ARRAY['USER_READ', 'ACCOUNT_READ', 'TRANSACTION_READ', 'TRANSACTION_WRITE']::TEXT[], FALSE),
    ('ROLE_ADMIN', 'Administrator with elevated privileges', ARRAY['USER_READ', 'USER_WRITE', 'ACCOUNT_READ', 'TRANSACTION_READ', 'AUDIT_READ', 'ADMIN_MANAGE']::TEXT[], TRUE),
    ('ROLE_SUPER_ADMIN', 'Super administrator with full access', ARRAY['USER_READ', 'USER_WRITE', 'USER_DELETE', 'USER_IMPERSONATE', 'ACCOUNT_READ', 'ACCOUNT_WRITE', 'TRANSACTION_READ', 'TRANSACTION_WRITE', 'AUDIT_READ', 'ADMIN_MANAGE', 'SERVICE_INVOKE']::TEXT[], TRUE),
    ('ROLE_SERVICE', 'Service account for system integrations', ARRAY['SERVICE_INVOKE']::TEXT[], FALSE);

-- Users table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(320) NOT NULL UNIQUE,
    email_hash VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(120),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    mfa_secret VARCHAR(64),
    failed_login_attempts INTEGER DEFAULT 0,
    locked_until TIMESTAMP WITH TIME ZONE,
    last_login_at TIMESTAMP WITH TIME ZONE,
    password_changed_at TIMESTAMP WITH TIME ZONE,
    password_expires_at TIMESTAMP WITH TIME ZONE,
    force_password_reset BOOLEAN NOT NULL DEFAULT FALSE,
    suspension_reason VARCHAR(500),
    suspension_timestamp TIMESTAMP WITH TIME ZONE,
    consent_email BOOLEAN NOT NULL DEFAULT FALSE,
    consent_timestamp TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE,
    version BIGINT DEFAULT 0
);

CREATE UNIQUE INDEX idx_users_email ON users(email);
CREATE UNIQUE INDEX idx_users_email_hash ON users(email_hash);
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_created_at ON users(created_at);

-- User roles junction table
CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);

-- Role permissions table (for explicit permission mapping)
CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission VARCHAR(50) NOT NULL,
    PRIMARY KEY (role_id, permission)
);

-- Refresh tokens table
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    family_id UUID NOT NULL,
    device_fingerprint VARCHAR(128),
    ip_hash VARCHAR(64),
    user_agent_hash VARCHAR(64),
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revoked_reason VARCHAR(50),
    replaced_by_token_id UUID,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMP WITH TIME ZONE,
    created_from_ip_hash VARCHAR(64)
);

CREATE UNIQUE INDEX idx_refresh_token_token_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_token_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_token_family_id ON refresh_tokens(family_id);
CREATE INDEX idx_refresh_token_expires_at ON refresh_tokens(expires_at);

-- Email verification tokens table
CREATE TABLE email_verification_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    email VARCHAR(320) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed BOOLEAN NOT NULL DEFAULT FALSE,
    consumed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_email_verif_token_hash ON email_verification_tokens(token_hash);
CREATE INDEX idx_email_verif_user_id ON email_verification_tokens(user_id);
CREATE INDEX idx_email_verif_expires_at ON email_verification_tokens(expires_at);

-- Password reset tokens table
CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed BOOLEAN NOT NULL DEFAULT FALSE,
    consumed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_from_ip_hash VARCHAR(64),
    attempt_count INTEGER DEFAULT 0
);

CREATE UNIQUE INDEX idx_pwd_reset_token_hash ON password_reset_tokens(token_hash);
CREATE INDEX idx_pwd_reset_user_id ON password_reset_tokens(user_id);
CREATE INDEX idx_pwd_reset_expires_at ON password_reset_tokens(expires_at);

-- Password history table
CREATE TABLE password_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    password_hash VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pwd_hist_user_id ON password_history(user_id);
CREATE INDEX idx_pwd_hist_created_at ON password_history(created_at);

-- MFA backup codes table
CREATE TABLE mfa_backup_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash VARCHAR(120) NOT NULL UNIQUE,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_mfa_backup_user_id ON mfa_backup_codes(user_id);
CREATE UNIQUE INDEX idx_mfa_backup_code_hash ON mfa_backup_codes(code_hash);

-- API keys table
CREATE TABLE api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key_hash VARCHAR(120) NOT NULL UNIQUE,
    key_prefix VARCHAR(8) NOT NULL,
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(200),
    expires_at TIMESTAMP WITH TIME ZONE,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revoked_reason VARCHAR(200),
    last_used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    rotated_from_key_id UUID REFERENCES api_keys(id),
    rotation_grace_period_end TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX idx_api_key_key_hash ON api_keys(key_hash);
CREATE INDEX idx_api_key_owner_id ON api_keys(owner_id);
CREATE INDEX idx_api_key_expires_at ON api_keys(expires_at);

-- API key scopes table
CREATE TABLE api_key_scopes (
    api_key_id UUID NOT NULL REFERENCES api_keys(id) ON DELETE CASCADE,
    scope VARCHAR(50) NOT NULL,
    PRIMARY KEY (api_key_id, scope)
);

-- API key allowed IPs table
CREATE TABLE api_key_allowed_ips (
    api_key_id UUID NOT NULL REFERENCES api_keys(id) ON DELETE CASCADE,
    allowed_ip VARCHAR(45) NOT NULL,
    PRIMARY KEY (api_key_id, allowed_ip)
);

-- Audit log table
CREATE TABLE audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(50) NOT NULL,
    user_id UUID,
    user_id_hash VARCHAR(64),
    email_hash VARCHAR(64),
    ip_hash VARCHAR(64),
    user_agent VARCHAR(500),
    outcome VARCHAR(20) NOT NULL,
    failure_reason VARCHAR(50),
    details VARCHAR(1000),
    trace_id VARCHAR(64),
    span_id VARCHAR(32),
    session_id VARCHAR(64),
    resource_type VARCHAR(50),
    resource_id VARCHAR(100),
    old_value VARCHAR(500),
    new_value VARCHAR(500),
    mfa_required BOOLEAN NOT NULL DEFAULT FALSE,
    mfa_completed BOOLEAN NOT NULL DEFAULT FALSE,
    impersonator_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_user_id ON audit_log(user_id);
CREATE INDEX idx_audit_event_type ON audit_log(event_type);
CREATE INDEX idx_audit_trace_id ON audit_log(trace_id);
CREATE INDEX idx_audit_created_at ON audit_log(created_at);
CREATE INDEX idx_audit_outcome ON audit_log(outcome);

-- Login attempts table
CREATE TABLE login_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID,
    email_hash VARCHAR(64),
    ip_hash VARCHAR(64) NOT NULL,
    user_agent_hash VARCHAR(64),
    success BOOLEAN NOT NULL,
    failure_reason VARCHAR(50),
    locked_out BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_login_attempts_user_id ON login_attempts(user_id);
CREATE INDEX idx_login_attempts_ip_hash ON login_attempts(ip_hash);
CREATE INDEX idx_login_attempts_created_at ON login_attempts(created_at);
