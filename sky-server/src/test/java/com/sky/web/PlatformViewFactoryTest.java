package com.sky.web;

import static org.junit.jupiter.api.Assertions.*;

import com.sky.platform.DiscoveryPages;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;

class PlatformViewFactoryTest {
  @Test
  void orderProjectionPreservesNullsNestedValuesAndRoleSpecificOmissions() {
    var detail = new LinkedHashMap<String, Object>();
    detail.put("id", 9L);
    detail.put("order_id", 1L);
    detail.put("amount", new BigDecimal("10.00"));
    detail.put("number", 2);
    detail.put("dish_flavor", null);
    var row = new LinkedHashMap<String, Object>();
    row.put("id", 1L);
    row.put("status", 3);
    row.put("ready", true);
    row.put("order_time", Timestamp.valueOf("2026-10-26 12:00:00"));
    row.put("phone", null);
    row.put("details", List.of(detail));
    row.put("delivery", null);
    row.put("aftersales", List.of());
    row.put("refunds", List.of(Map.of("id", 12L, "amount_cents", 1L, "attempts", 0)));
    var projected = PlatformViewFactory.order(row);
    assertEquals("1", projected.id());
    assertEquals("10.00", projected.details().getFirst().amount());
    assertEquals(ApiAdvice.wire(row), ApiAdvice.wire(projected));
    var wire = (Map<?, ?>) ApiAdvice.wire(projected);
    assertTrue(wire.containsKey("phone"));
    assertFalse(wire.containsKey("pickupCode"));
    assertFalse(wire.containsKey("presentFields"));
    row.put("pickup_code", "00000001");
    assertEquals(ApiAdvice.wire(row), ApiAdvice.wire(PlatformViewFactory.order(row)));
  }

  @Test
  void discoveryProjectionKeepsDishAndSetmealShapesAndPageCursor() {
    var dish = new LinkedHashMap<String, Object>();
    dish.put("id", 1L);
    dish.put("item_type", "DISH");
    dish.put("price", new BigDecimal("10.00"));
    dish.put("remaining", 20);
    dish.put("flavors", List.of(Map.of("id", 10L, "name", "辣度", "values", List.of("微辣"))));
    dish.put("promotion", null);
    var setmeal = new LinkedHashMap<String, Object>();
    setmeal.put("id", 2L);
    setmeal.put("item_type", "SETMEAL");
    setmeal.put("components", List.of(Map.of("dish_id", 1L, "setmeal_id", 2L, "copies", 1,
        "price", new BigDecimal("10.00"), "current_name", "当前餐品")));
    setmeal.put("promotion", Map.of("id", 20L, "price_cents", 800L,
        "ends_at", LocalDateTime.of(2026, 10, 27, 12, 0)));
    var page = new DiscoveryPages.Page(List.of(dish, setmeal), "signed-cursor", true);
    assertEquals(ApiAdvice.wire(page), ApiAdvice.wire(PlatformViewFactory.search(page)));
    var store = Map.<String, Object>of("id", 1L, "campus_id", 2L, "enabled", true,
        "hours", List.of("00:00-24:00"));
    var stores = new DiscoveryPages.Page(List.of(store), null, false);
    assertEquals(ApiAdvice.wire(stores), ApiAdvice.wire(PlatformViewFactory.stores(stores)));
  }

  @Test
  void refundProjectionPreservesAllTaskFieldsIncludingNullsAndTimeSource() {
    var row = new LinkedHashMap<String, Object>();
    row.put("id", 1L);
    row.put("order_id", 2L);
    row.put("aftersale_id", null);
    row.put("store_id", 3L);
    row.put("amount_cents", 100L);
    row.put("state", "FAILED");
    row.put("attempts", 5);
    row.put("next_attempt_at", LocalDateTime.of(2026, 10, 26, 12, 0));
    row.put("lease_until", null);
    row.put("last_error", "MOCK_REFUND_FAILED");
    row.put("created_at", LocalDateTime.of(2026, 10, 25, 12, 0));
    row.put("succeeded_at", null);
    row.put("success_time_source", "ACTUAL");
    assertEquals(ApiAdvice.wire(List.of(row)), ApiAdvice.wire(PlatformViewFactory.refunds(List.of(row))));
  }

  @Test
  void unmappedFieldsAndWrongTypesFailRatherThanSilentlyDrifting() {
    var error = assertThrows(IllegalStateException.class,
        () -> PlatformViewFactory.order(Map.of("unexpected_business_field", "new")));
    assertTrue(error.getMessage().contains("unexpectedBusinessField"));
    assertThrows(IllegalStateException.class,
        () -> PlatformViewFactory.order(Map.of("details", List.of(Map.of("unmapped", 1)))));
    assertThrows(IllegalStateException.class,
        () -> PlatformViewFactory.order(Map.of("status", "3")));
  }
}
