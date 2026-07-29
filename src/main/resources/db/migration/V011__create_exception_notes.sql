-- V011__create_exception_notes.sql

CREATE TABLE exception_notes (
  id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  exception_id UUID NOT NULL REFERENCES exceptions(id),
  author_id    UUID NOT NULL REFERENCES users(id),
  body         TEXT NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);