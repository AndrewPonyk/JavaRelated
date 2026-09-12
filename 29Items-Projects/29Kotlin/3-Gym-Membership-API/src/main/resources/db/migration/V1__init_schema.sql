-- V1__init_schema.sql: Initial schema for Gym Membership API

CREATE TABLE IF NOT EXISTS plans (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    tier VARCHAR(20) NOT NULL, -- BASIC, PREMIUM, VIP
    price_cents INT NOT NULL,
    duration_days INT NOT NULL,
    max_guest_passes INT NOT NULL DEFAULT 0,
    features TEXT[] NOT NULL DEFAULT '{}',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS members (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    badge_code VARCHAR(100) UNIQUE NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'ROLE_MEMBER', -- ROLE_MEMBER, ROLE_TRAINER, ROLE_ADMIN
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS subscriptions (
    id VARCHAR(36) PRIMARY KEY,
    member_id VARCHAR(36) NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    plan_id VARCHAR(50) NOT NULL REFERENCES plans(id),
    status VARCHAR(20) NOT NULL, -- ACTIVE, FROZEN, EXPIRED, CANCELLED
    start_date TIMESTAMP WITH TIME ZONE NOT NULL,
    end_date TIMESTAMP WITH TIME ZONE NOT NULL,
    remaining_guest_passes INT NOT NULL DEFAULT 0,
    cumulative_freeze_days INT NOT NULL DEFAULT 0,
    auto_renew BOOLEAN NOT NULL DEFAULT TRUE,
    last_warning_sent_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS freeze_records (
    id VARCHAR(36) PRIMARY KEY,
    subscription_id VARCHAR(36) NOT NULL REFERENCES subscriptions(id) ON DELETE CASCADE,
    member_id VARCHAR(36) NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    start_date TIMESTAMP WITH TIME ZONE NOT NULL,
    scheduled_end_date TIMESTAMP WITH TIME ZONE NOT NULL,
    actual_end_date TIMESTAMP WITH TIME ZONE,
    days_frozen INT NOT NULL,
    reason VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS check_ins (
    id VARCHAR(36) PRIMARY KEY,
    member_id VARCHAR(36) NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    badge_code VARCHAR(100) NOT NULL,
    zone VARCHAR(50) NOT NULL, -- GYM_FLOOR, POOL, SAUNA, VIP_LOUNGE, CLASS_STUDIO
    turnstile_id VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL, -- GRANTED, REJECTED
    rejection_reason VARCHAR(255),
    scanned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_members_badge_code ON members(badge_code);
CREATE INDEX IF NOT EXISTS idx_subscriptions_member_status ON subscriptions(member_id, status);
CREATE INDEX IF NOT EXISTS idx_subscriptions_end_date ON subscriptions(end_date);
CREATE INDEX IF NOT EXISTS idx_check_ins_member_scanned ON check_ins(member_id, scanned_at DESC);
CREATE INDEX IF NOT EXISTS idx_freeze_records_sub ON freeze_records(subscription_id);
