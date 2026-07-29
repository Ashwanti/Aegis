-- V015__create_ingestion_errors.sql

CREATE TABLE ingestion_errors (
  id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  source_id    UUID NOT NULL REFERENCES sources(id),
  file_name    TEXT NOT NULL,
  row_number   INTEGER NOT NULL,
  raw_row      TEXT NOT NULL,
  error_reason TEXT NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);