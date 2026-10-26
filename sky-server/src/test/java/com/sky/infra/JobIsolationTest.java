package com.sky.infra;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sky.business.*;
import com.sky.platform.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class JobIsolationTest {
  @Test
  void refundClaimFailureDoesNotBlockNotificationBatch() {
    var db = mock(Db.class);
    var tasks = mock(TaskRunner.class);
    var operations = new RuntimeOperations(Clock.systemUTC(), true);
    var jobs =
        new BackgroundJobs(
            db, tasks, mock(OrderCommandService.class), Clock.systemUTC(), true, operations);
    when(tasks.runRefund(any())).thenThrow(new IllegalStateException());
    when(tasks.runEvent(any())).thenReturn(true);
    jobs.work();
    jobs.notifications();
    verify(tasks, times(50)).runEvent(any());
    assertTrue(operations.snapshot().get("issues").toString().contains("legacyRefunds:FAILED"));
  }

  @Test
  void badCheckoutDoesNotBlockFollowingCheckoutOrDeliveryMarking() {
    var db = mock(Db.class);
    var checkout = mock(CheckoutService.class);
    var orders = mock(OrderCommandService.class);
    var operations = new RuntimeOperations(Clock.systemUTC(), true);
    var jobs =
        new PlatformJobs(
            db,
            Clock.systemUTC(),
            mock(PaymentGateway.class),
            checkout,
            mock(PlatformScope.class),
            mock(PlatformTransactionManager.class),
            true,
            operations,
            orders,
            mock(com.sky.finance.LedgerService.class));
    when(db.rows(contains("FROM checkout"), any(Object[].class)))
        .thenReturn(List.of(Map.of("id", 1L), Map.of("id", 2L)));
    when(db.rows(contains("FROM orders"), any(Object[].class)))
        .thenReturn(List.of(Map.of("id", 3L)));
    doThrow(new IllegalStateException()).when(checkout).expire(1);
    jobs.expireCheckouts();
    jobs.markOverdue();
    verify(checkout).expire(2);
    verify(orders).markOverdue(3);
    assertTrue(operations.snapshot().get("issues").toString().contains("checkoutExpiry:FAILED"));
  }
}
