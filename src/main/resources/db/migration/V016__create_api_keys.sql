-- V016__create_api_keys.sql

CREATE TABLE api_keys (
  id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  org_id       UUID NOT NULL REFERENCES orgs(id),
  name         TEXT NOT NULL,
  hashed_key   TEXT NOT NULL UNIQUE,
  permissions  JSONB NOT NULL DEFAULT '[]',
  last_used_at TIMESTAMPTZ,
  expires_at   TIMESTAMPTZ,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at   TIMESTAMPTZ
);