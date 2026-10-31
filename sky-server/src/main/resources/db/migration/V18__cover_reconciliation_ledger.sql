-- Reconciliation groups ledger entries by order without fetching each journal row.
ALTER TABLE finance_ledger ADD KEY ix_finance_ledger_balance(order_id,source_type,amount_cents);
