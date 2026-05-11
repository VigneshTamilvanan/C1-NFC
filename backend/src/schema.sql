CREATE TABLE IF NOT EXISTS tickets (
  id          SERIAL PRIMARY KEY,
  ticket_no   VARCHAR(20)  UNIQUE NOT NULL,
  txn_id      VARCHAR(100) UNIQUE NOT NULL,
  route       VARCHAR(20),
  source      VARCHAR(200),
  destination VARCHAR(200),
  fare        NUMERIC(8,2),
  status      VARCHAR(20)  DEFAULT 'issued',
  created_at  TIMESTAMPTZ  DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS users (
  id            SERIAL PRIMARY KEY,
  username      VARCHAR(100) UNIQUE NOT NULL,
  password_hash VARCHAR(200) NOT NULL,
  created_at    TIMESTAMPTZ  DEFAULT NOW()
);

-- Default admin user (password: admin123 — change after first login)
-- Run: node src/seed.js  to insert this user
