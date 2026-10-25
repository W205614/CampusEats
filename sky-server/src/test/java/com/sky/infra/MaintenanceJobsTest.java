package com.sky.infra;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class MaintenanceJobsTest {
  @Test
  void fullBatchesAlternateAndStopAfterTenPerKind() {
    var db = mock(Db.class);
    var registry = new SimpleMeterRegistry();
    when(db.update(anyString(), any(Object[].class))).thenReturn(200);
    var jobs = new MaintenanceJobs(db, Clock.systemUTC(), registry, true,
        new RuntimeOperations(Clock.systemUTC(), true), () -> 0);
    jobs.clean();
    var order = inOrder(db);
    for (int batch = 0; batch < 10; batch++) {
      order.verify(db).update(startsWith("DELETE FROM auth_session"), any(Object[].class));
      order.verify(db).update(startsWith("DELETE FROM outbox_event"), any(Object[].class));
    }
    verify(db, times(20)).update(anyString(), any(Object[].class));
    assertEquals(2000, registry.get("campus.maintenance.deleted").tag("kind", "sessions").counter().count());
    assertEquals(2000, registry.get("campus.maintenance.deleted").tag("kind", "outbox").counter().count());
  }

  @Test
  void budgetStopsNewBatchesAndNextRoundStartsWithOtherKind() {
    var db = mock(Db.class);
    var registry = new SimpleMeterRegistry();
    var ticker = new AtomicLong();
    when(db.update(anyString(), any(Object[].class))).thenAnswer(call -> {
      ticker.addAndGet(5_000_000_000L);
      return 200;
    });
    var jobs = new MaintenanceJobs(db, Clock.systemUTC(), registry, true,
        new RuntimeOperations(Clock.systemUTC(), true), ticker::get);
    jobs.clean();
    jobs.clean();
    var order = inOrder(db);
    order.verify(db).update(startsWith("DELETE FROM auth_session"), any(Object[].class));
    order.verify(db).update(startsWith("DELETE FROM outbox_event"), any(Object[].class));
    verify(db, times(2)).update(anyString(), any(Object[].class));
    assertEquals(1, registry.get("campus.maintenance.budget.exhausted").gauge().value());
    assertEquals(2, registry.get("campus.maintenance.budget.exhaustions").counter().count());
  }

  @Test
  void partialBatchesDoNotKeepQueryingEmptyFamilies() {
    var db = mock(Db.class);
    when(db.update(anyString(), any(Object[].class))).thenReturn(3);
    var jobs = new MaintenanceJobs(db, Clock.systemUTC(), new SimpleMeterRegistry(), true,
        new RuntimeOperations(Clock.systemUTC(), true), () -> 0);
    jobs.clean();
    verify(db, times(2)).update(anyString(), any(Object[].class));
  }

  @Test
  void failureIsVisibleAndNextSuccessfulRunClearsFailureState() {
    var db = mock(Db.class);
    var registry = new SimpleMeterRegistry();
    when(db.update(anyString(), any(Object[].class))).thenThrow(new IllegalStateException());
    var jobs =
        new MaintenanceJobs(
            db, Clock.systemUTC(), registry, true, new RuntimeOperations(Clock.systemUTC(), true));
    jobs.work();
    assertEquals(1, registry.get("campus.maintenance.failed").gauge().value());
    assertEquals(1, registry.get("campus.maintenance.failures").counter().count());
    doReturn(0).when(db).update(anyString(), any(Object[].class));
    jobs.work();
    assertEquals(0, registry.get("campus.maintenance.failed").gauge().value());
  }

  @Test
  void taskSwitchDisablesMaintenance() {
    var db = mock(Db.class);
    new MaintenanceJobs(
            db,
            Clock.systemUTC(),
            new SimpleMeterRegistry(),
            false,
            new RuntimeOperations(Clock.systemUTC(), false))
        .work();
    verifyNoInteractions(db);
  }
}
