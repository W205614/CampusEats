package com.campuseats.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.campuseats.common.BusinessException;
import com.campuseats.infra.Db;
import com.campuseats.platform.PlatformScope;
import com.campuseats.security.Actor;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class FinanceAuthorizationBoundaryTest {
  @Test
  void selectingEitherSettlementScopeCannotSkipItsRoleAndStorePermission() {
    Db db = mock(Db.class);
    when(db.one(anyString(), eq(7L))).thenReturn(Map.of("store_id", 123L));
    when(db.one(anyString(), eq(123L))).thenReturn(Map.of("id", 123L));
    var finance = new FinanceService(db, Clock.systemUTC(), new PlatformScope(db), null);
    var actor = new Actor(1, "ADMIN", "SHOP_MANAGER", "test", false, 124L, 1L);
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(actor, null, List.of()));
    try {
      assertEquals(
          "FORBIDDEN", assertThrows(BusinessException.class, () -> finance.detail(7, null)).code());
      assertEquals(
          "FORBIDDEN", assertThrows(BusinessException.class, () -> finance.detail(7, 123L)).code());
      assertEquals(
          "NOT_FOUND", assertThrows(BusinessException.class, () -> finance.detail(7, 124L)).code());
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
