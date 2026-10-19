package com.sky.infra;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class OperationalMetricsTest {
  @Test
  void databaseFailureInvalidatesOldSamplesAndRecoveryRestoresValidity() {
    var db = mock(Db.class);
    var registry = new SimpleMeterRegistry();
    when(db.count(anyString())).thenReturn(4L);
    var metrics =
        new OperationalMetrics(
            db,
            mock(StringRedisTemplate.class),
            registry,
            new RuntimeOperations(java.time.Clock.systemUTC(), true));
    metrics.sample();
    assertEquals(1, registry.get("campus.metrics.valid").gauge().value());
    when(db.count(anyString())).thenThrow(new IllegalStateException());
    metrics.sample();
    assertEquals(0, registry.get("campus.metrics.valid").gauge().value());
    assertEquals(-1, registry.get("campus.refund.failed").gauge().value());
    assertEquals(1, registry.get("campus.metrics.failures").counter().count());
    doReturn(0L).when(db).count(anyString());
    metrics.sample();
    assertEquals(1, registry.get("campus.metrics.valid").gauge().value());
    assertEquals(0, registry.get("campus.refund.failed").gauge().value());
  }
}
