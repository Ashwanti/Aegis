-- V007__create_recon_profiles.sql

CREATE TABLE recon_profiles (
  id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  entity_id    UUID NOT NULL REFERENCES entities(id),
  name         TEXT NOT NULL,
  source_a_id  UUID NOT NULL REFERENCES sources(id),
  source_b_id  UUID NOT NULL REFERENCES sources(id),
  rules_json   JSONB NOT NULL DEFAULT '[]',
  schedule     TEXT,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at   TIMESTAMPTZ
);