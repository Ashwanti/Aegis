-- V008__create_recon_runs.sql

CREATE TABLE recon_runs (
  id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  profile_id       UUID NOT NULL REFERENCES recon_profiles(id),
  status           TEXT NOT NULL DEFAULT 'PENDING',
  started_at       TIMESTAMPTZ,
  completed_at     TIMESTAMPTZ,
  stats            JSONB NOT NULL DEFAULT '{}',
  created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);