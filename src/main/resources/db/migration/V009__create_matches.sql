-- V009__create_matches.sql

CREATE TABLE matches (
  id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  run_id        UUID NOT NULL REFERENCES recon_runs(id),
  txn_a_id      UUID NOT NULL REFERENCES transactions(id),
  txn_b_id      UUID REFERENCES transactions(id),
  match_type    TEXT NOT NULL,
  amount_delta  NUMERIC(18,6) NOT NULL DEFAULT 0,
  status        TEXT NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);