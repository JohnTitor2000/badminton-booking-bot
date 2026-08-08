CREATE TABLE events (
    id                    BIGSERIAL PRIMARY KEY,
    event_date            DATE        NOT NULL UNIQUE,
    status                VARCHAR(20) NOT NULL,
    created_by            VARCHAR(20) NOT NULL,
    channel_message_id    INTEGER,
    discussion_chat_id    BIGINT,
    discussion_anchor_message_id INTEGER,
    discussion_thread_id  INTEGER,
    booking_message_id    INTEGER,
    created_at            TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_events_status ON events (status);
CREATE INDEX idx_events_thread ON events (discussion_chat_id, discussion_thread_id);

CREATE TABLE bookings (
    id                BIGSERIAL PRIMARY KEY,
    event_id          BIGINT      NOT NULL REFERENCES events (id),
    telegram_user_id  BIGINT      NOT NULL,
    display_name      VARCHAR(255) NOT NULL,
    username          VARCHAR(255),
    start_slot        INTEGER     NOT NULL,
    duration_minutes  INTEGER     NOT NULL,
    party_size        INTEGER     NOT NULL,
    status            VARCHAR(20) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_bookings_event ON bookings (event_id, status);
CREATE INDEX idx_bookings_user ON bookings (event_id, telegram_user_id, status);
