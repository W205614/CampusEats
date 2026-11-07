-- Verified on 10k-order fixture: unfiltered admin ordering otherwise scans and sorts the table.
ALTER TABLE orders ADD KEY ix_order_time(order_time,id);
