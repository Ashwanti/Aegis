-- V014__create_webhooks.sql

CREATE TABLE webhooks (
  id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  org_id      UUID NOT NULL REFERENCES orgs(id),
  url         TEXT NOT NULL,
  secret      TEXT NOT NULL,
  events_json JSONB NOT NULL DEFAULT '[]',
  active      BOOLEAN NOT NULL DEFAULT TRUE,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at  TIMESTAMPTZ
);