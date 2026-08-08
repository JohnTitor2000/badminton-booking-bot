CREATE TABLE booking_presets (
    telegram_user_id  BIGINT PRIMARY KEY,
    start_slot        INTEGER     NOT NULL,
    duration_minutes  INTEGER     NOT NULL,
    party_size        INTEGER     NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL
);
