package com.sky.infra;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

class OperationalMetricsTest {
  @Test
  void reportsEvictedKeysWithoutChangingDatabaseMetricValidity() {
    var redis = mock(StringRedisTemplate.class);
    var factory = mock(RedisConnectionFactory.class);
    var connection = mock(RedisConnection.class);
    var server = mock(RedisServerCommands.class);
    when(redis.getConnectionFactory()).thenReturn(factory);
    when(factory.getConnection()).thenReturn(connection);
    when(connection.serverCommands()).thenReturn(server);
    var info = new java.util.Properties();
    info.setProperty("evicted_keys", "17");
    when(server.info("stats")).thenReturn(info);
    var registry = new SimpleMeterRegistry();
    var db = mock(Db.class);
    when(db.one(contains("TIMESTAMPDIFF"), any(Object[].class)))
        .thenReturn(java.util.Map.of("age", 5L));
    var metrics =
        new OperationalMetrics(
            db, redis, registry, new RuntimeOperations(java.time.Clock.systemUTC(), true));
    metrics.sample();
    assertEquals(17, registry.get("campus.redis.evicted.keys").gauge().value());
    assertEquals(1, registry.get("campus.metrics.valid").gauge().value());
    assertEquals(5, registry.get("campus.refund.oldest.wait.seconds").gauge().value());
    when(server.info("stats")).thenThrow(new IllegalStateException("stats denied"));
    metrics.sample();
    assertEquals(-1, registry.get("campus.redis.evicted.keys").gauge().value());
    assertEquals(0, registry.get("campus.redis.degraded").gauge().value());
  }

  @Test
  void databaseFailureInvalidatesOldSamplesAndRecoveryRestoresValidity() {
    var db = mock(Db.class);
    var registry = new SimpleMeterRegistry();
    when(db.count(anyString())).thenReturn(4L);
    when(db.one(contains("TIMESTAMPDIFF"), any(Object[].class)))
        .thenReturn(java.util.Map.of("age", 5L));
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
