-- History remains keyset-paginated; these access paths avoid traversing other stores' history.
ALTER TABLE review ADD KEY ix_review_store_history(store_id,id);
ALTER TABLE audit_log ADD KEY ix_audit_store_history(store_id,id),
  ADD KEY ix_audit_actor_history(actor_id,id),
  ADD KEY ix_audit_action_history(action,id);
