-- Scope-first history pages retain id cursors; state indexes support the failure work queues.
ALTER TABLE aftersale ADD KEY ix_aftersale_user_history(user_id,id),
  ADD KEY ix_aftersale_store_history(store_id,id), ADD KEY ix_aftersale_state_history(state,id);
ALTER TABLE refund_request ADD KEY ix_refund_store_history(store_id,id),
  ADD KEY ix_refund_state_history(state,id);
-- ix_outbox_store(store_id,state,id) already exists; add unfiltered history and order lookup.
ALTER TABLE outbox_event ADD KEY ix_outbox_store_history(store_id,id),
  ADD KEY ix_outbox_aggregate_history(aggregate_id,id);
ALTER TABLE dish ADD KEY ix_dish_discovery(store_id,status,category_id,id);
ALTER TABLE setmeal ADD KEY ix_setmeal_discovery(store_id,status,category_id,id);
