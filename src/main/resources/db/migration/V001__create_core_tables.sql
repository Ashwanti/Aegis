CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE orgs (
  id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  name          TEXT NOT NULL,
  base_currency CHAR(3) NOT NULL DEFAULT 'INR',
  plan          TEXT NOT NULL DEFAULT 'BASIC',
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at    TIMESTAMPTZ
);

CREATE TABLE entities (
  id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  org_id     UUID NOT NULL REFERENCES orgs(id),
  name       TEXT NOT NULL,
  tax_id     TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at TIMESTAMPTZ
);

CREATE TABLE users (
  id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  org_id      UUID NOT NULL REFERENCES orgs(id),
  email       TEXT NOT NULL UNIQUE,
  hashed_pw   TEXT NOT NULL,
  role        TEXT NOT NULL DEFAULT 'ANALYST',
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at  TIMESTAMPTZ
);