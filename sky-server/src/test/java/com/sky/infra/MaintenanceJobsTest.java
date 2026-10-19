package com.sky.infra;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class MaintenanceJobsTest {
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
