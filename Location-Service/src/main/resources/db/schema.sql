-- Run this once against your PostgreSQL instance before starting the app.
-- The app uses spring.jpa.hibernate.ddl-auto=update so Hibernate will also
-- create the table automatically, but this script is useful for reference
-- or for CI pipelines that prefer explicit DDL.

CREATE TABLE IF NOT EXISTS user_locations (
    id          BIGSERIAL       PRIMARY KEY,
    latitude    DOUBLE PRECISION NOT NULL,
    longitude   DOUBLE PRECISION NOT NULL,
    accuracy    DOUBLE PRECISION,
    address     VARCHAR(512),
    city        VARCHAR(128),
    country     VARCHAR(128),
    ip_address  VARCHAR(64),
    created_at  TIMESTAMP       NOT NULL DEFAULT now()
);

-- Optional index to speed up "most-recent first" queries
CREATE INDEX IF NOT EXISTS idx_user_locations_created_at
    ON user_locations (created_at DESC);
