-- Keep balance grouping ordered and avoid fetching wide order snapshots.
ALTER TABLE orders ADD KEY ix_orders_finance_balance(store_id,id,paid_cents,refunded_cents);
