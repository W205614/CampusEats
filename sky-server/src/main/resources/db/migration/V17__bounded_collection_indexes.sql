CREATE INDEX ix_staff_store_page ON employee(store_id,id);
CREATE INDEX ix_wallet_user_page ON user_coupon(user_id,id);
CREATE INDEX ix_coupon_store_page ON coupon(store_id,id);
CREATE INDEX ix_promotion_store_page ON promotion(store_id,id);
CREATE INDEX ix_favorite_store_page ON favorite(user_id,store_id,target_type,target_id);
