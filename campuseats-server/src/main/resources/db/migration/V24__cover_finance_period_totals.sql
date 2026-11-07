CREATE INDEX ix_finance_ledger_period_cover
    ON finance_ledger(store_id,occurred_at,source_type,amount_cents);
