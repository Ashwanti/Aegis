

-- transactions: composite lookup by source + date
CREATE INDEX idx_transactions_source_txndate
    ON transactions (source_id, txn_date);

CREATE INDEX idx_audit_events_created_at
    ON audit_events (created_at);