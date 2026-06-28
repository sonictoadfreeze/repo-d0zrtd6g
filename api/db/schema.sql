-- InkVPN backend schema (PostgreSQL)

CREATE TABLE IF NOT EXISTS users (
    id           BIGINT PRIMARY KEY,            -- telegram user id
    username     TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS subscriptions (
    key          TEXT PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id),
    plan         TEXT NOT NULL,
    device_limit INT NOT NULL DEFAULT 5,
    expires_at   TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS payments (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id),
    key          TEXT REFERENCES subscriptions(key),
    provider     TEXT NOT NULL,
    amount       NUMERIC(12,2) NOT NULL,
    currency     TEXT NOT NULL DEFAULT 'RUB',
    status       TEXT NOT NULL DEFAULT 'pending',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS hwids (
    key          TEXT NOT NULL REFERENCES subscriptions(key),
    hwid         TEXT NOT NULL,
    first_seen   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (key, hwid)
);
