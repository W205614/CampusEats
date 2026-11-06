package com.sky.infra;

import io.micrometer.core.instrument.MeterRegistry;
import java.nio.file.*;
import java.time.*;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Reports an operator's independently verified restore receipt; it does not perform a restore. */
@Component
@Profile("prod")
@ConditionalOnProperty(name = "campus.operations.restore-evidence")
public class RestoreEvidenceMetrics {
  private final Path receipt;
  private final Json json;
  private final Clock clock;
  private final AtomicLong timestamp = new AtomicLong(), valid = new AtomicLong();

  public RestoreEvidenceMetrics(
      @Value("${campus.operations.restore-evidence}") String receipt,
      Json json,
      Clock clock,
      MeterRegistry metrics) {
    this.receipt = Path.of(receipt);
    this.json = json;
    this.clock = clock;
    metrics.gauge("campus.backup.last.verified.timestamp.seconds", timestamp);
    metrics.gauge("campus.backup.evidence.valid", valid);
  }

  @Scheduled(fixedDelay = 60000, initialDelay = 1000)
  public void sample() {
    try {
      if (!Files.isRegularFile(receipt, LinkOption.NOFOLLOW_LINKS) || Files.size(receipt) > 65536)
        throw new IllegalArgumentException("Invalid restore receipt file");
      var data = json.map(Files.readString(receipt));
      if (!"PASSED".equals(data.get("status"))
          || !Boolean.TRUE.equals(data.get("isolated"))
          || !Boolean.TRUE.equals(data.get("databaseChecked"))
          || !Boolean.TRUE.equals(data.get("objectsChecked"))
          || !Boolean.TRUE.equals(data.get("channelReconciled")))
        throw new IllegalArgumentException("Incomplete restore receipt");
      Instant verifiedAt = OffsetDateTime.parse(data.get("verifiedAt").toString()).toInstant();
      if (verifiedAt.isAfter(clock.instant()))
        throw new IllegalArgumentException("Future restore receipt");
      timestamp.set(verifiedAt.getEpochSecond());
      valid.set(1);
    } catch (Exception error) {
      timestamp.set(0);
      valid.set(0);
      org.slf4j.LoggerFactory.getLogger(RestoreEvidenceMetrics.class)
          .warn(
              "Restore receipt unavailable or invalid; type={}", error.getClass().getSimpleName());
    }
  }
}
