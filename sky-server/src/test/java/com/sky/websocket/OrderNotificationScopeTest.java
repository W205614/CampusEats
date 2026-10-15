package com.sky.websocket;

import static org.junit.jupiter.api.Assertions.*;

import com.sky.security.Actor;
import org.junit.jupiter.api.Test;

class OrderNotificationScopeTest {
  @Test
  void notificationsFollowRoleAndStoreScope() {
    var manager = new Actor(2, "ADMIN", "SHOP_MANAGER", "", false, 1L, 1L);
    var operator = new Actor(3, "ADMIN", "OPERATOR", "", false, 1L, 1L);
    var courier = new Actor(4, "ADMIN", "DELIVERER", "", false, null, 1L);
    var platform = new Actor(1, "ADMIN", "PLATFORM_ADMIN", "", false, null, null);
    assertTrue(OrderSocket.authorized(manager, 1L));
    assertTrue(OrderSocket.authorized(operator, 1L));
    assertFalse(OrderSocket.authorized(manager, 2L));
    assertFalse(OrderSocket.authorized(operator, null));
    assertFalse(OrderSocket.authorized(courier, 1L));
    assertTrue(OrderSocket.authorized(platform, 2L));
  }
}
