-- ============================================================================
-- V9: Customer Authentication & Registration Lifecycle Schema
-- Implements Customer Registration, Verification, Admin Review, and Passwordless OTP
-- ============================================================================

-- 1. Customer Registrations Table
CREATE TABLE IF NOT EXISTS customer_registrations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    full_name VARCHAR(255) NOT NULL,
    company_name VARCHAR(255) NOT NULL,
    job_title VARCHAR(255),
    status VARCHAR(64) NOT NULL DEFAULT 'PENDING_EMAIL_VERIFICATION',
    verification_token_hash VARCHAR(255),
    verification_token_expires_at TIMESTAMPTZ,
    email_verified_at TIMESTAMPTZ,
    assigned_tenant_id UUID REFERENCES organizations(id) ON DELETE SET NULL,
    assigned_role VARCHAR(64) CHECK (assigned_role IS NULL OR assigned_role IN ('OWNER','ADMIN','INCIDENT_MANAGER','SRE','DEVELOPER','VIEWER')),
    reviewed_by UUID,
    reviewed_at TIMESTAMPTZ,
    rejection_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_customer_reg_email ON customer_registrations(email);
CREATE INDEX IF NOT EXISTS idx_customer_reg_status ON customer_registrations(status);
CREATE INDEX IF NOT EXISTS idx_customer_reg_tenant ON customer_registrations(assigned_tenant_id);

-- 2. Authentication OTPs Table
CREATE TABLE IF NOT EXISTS auth_otps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    otp_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed BOOLEAN NOT NULL DEFAULT FALSE,
    attempts INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_auth_otps_email_expires ON auth_otps(email, expires_at DESC);
