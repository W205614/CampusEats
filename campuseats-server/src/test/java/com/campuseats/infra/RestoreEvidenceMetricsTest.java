package com.campuseats.infra;

import static org.junit.jupiter.api.Assertions.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.*;
import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class RestoreEvidenceMetricsTest {
  @TempDir Path directory;

  @Test
  void missingIncompleteAndFutureReceiptsNeverReportARecentVerifiedRestore() throws Exception {
    var json = new Json(new ObjectMapper());
    var clock = Clock.fixed(Instant.parse("2026-11-06T06:00:00Z"), ZoneOffset.UTC);
    var registry = new SimpleMeterRegistry();
    Path file = directory.resolve("receipt.json");
    var sampler = new RestoreEvidenceMetrics(file.toString(), json, clock, registry);
    sampler.sample();
    assertEquals(0, registry.get("campus.backup.evidence.valid").gauge().value());
    Files.writeString(
        file, json.write(Map.of("status", "PASSED", "verifiedAt", clock.instant().toString())));
    sampler.sample();
    assertEquals(0, registry.get("campus.backup.last.verified.timestamp.seconds").gauge().value());
    Files.writeString(
        file,
        json.write(
            Map.of(
                "status",
                "PASSED",
                "isolated",
                true,
                "databaseChecked",
                true,
                "objectsChecked",
                true,
                "channelReconciled",
                true,
                "verifiedAt",
                clock.instant().plusSeconds(60).toString())));
    sampler.sample();
    assertEquals(0, registry.get("campus.backup.evidence.valid").gauge().value());
    Files.writeString(
        file,
        json.write(
            Map.of(
                "status",
                "PASSED",
                "isolated",
                true,
                "databaseChecked",
                true,
                "objectsChecked",
                true,
                "channelReconciled",
                true,
                "verifiedAt",
                clock.instant().minusSeconds(3600).toString())));
    sampler.sample();
    assertEquals(1, registry.get("campus.backup.evidence.valid").gauge().value());
    assertEquals(
        clock.instant().minusSeconds(3600).getEpochSecond(),
        registry.get("campus.backup.last.verified.timestamp.seconds").gauge().value());
  }
}
