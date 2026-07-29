-- V012__create_fx_rates.sql

CREATE TABLE fx_rates (
  id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  base       CHAR(3) NOT NULL,
  quote      CHAR(3) NOT NULL,
  rate       NUMERIC(18,8) NOT NULL,
  rate_date  DATE NOT NULL,
  source     TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  UNIQUE (base, quote, rate_date)
);