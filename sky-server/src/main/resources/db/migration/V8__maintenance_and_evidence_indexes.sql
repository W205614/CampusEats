ALTER TABLE auth_session ADD KEY ix_session_expiry(expires_at);
ALTER TABLE outbox_event ADD KEY ix_outbox_retention(state,created_at);
ALTER TABLE evidence ADD KEY ix_evidence_pending(order_id,user_id,request_id);
