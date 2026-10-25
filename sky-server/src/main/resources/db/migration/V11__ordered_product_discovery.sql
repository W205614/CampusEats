-- V9 remains useful for category-scoped reads. These complementary indexes preserve product ID
-- order across categories, allowing the bounded discovery branches to stop after K matches.
ALTER TABLE dish ADD KEY ix_dish_search_order(store_id,status,id);
ALTER TABLE setmeal ADD KEY ix_setmeal_search_order(store_id,status,id);
