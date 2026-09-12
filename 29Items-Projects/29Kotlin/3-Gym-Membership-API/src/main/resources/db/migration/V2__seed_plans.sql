-- V2__seed_plans.sql: Seed initial gym membership tiers

INSERT INTO plans (id, name, tier, price_cents, duration_days, max_guest_passes, features, is_active)
VALUES 
(
    'plan-basic-monthly',
    'Basic Monthly',
    'BASIC',
    2999,
    30,
    0,
    ARRAY['GYM_FLOOR', 'LOCKER_ROOM'],
    TRUE
),
(
    'plan-premium-monthly',
    'Premium Monthly',
    'PREMIUM',
    5999,
    30,
    2,
    ARRAY['GYM_FLOOR', 'LOCKER_ROOM', 'POOL', 'SAUNA', 'GROUP_CLASSES'],
    TRUE
),
(
    'plan-vip-annual',
    'VIP Annual All-Access',
    'VIP',
    59999,
    365,
    5,
    ARRAY['GYM_FLOOR', 'LOCKER_ROOM', 'POOL', 'SAUNA', 'GROUP_CLASSES', 'VIP_LOUNGE', 'PERSONAL_TRAINER_CONSULT', 'FREE_TOWEL_SERVICE'],
    TRUE
)
ON CONFLICT (id) DO NOTHING;
