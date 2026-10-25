package com.sky.platform;

import static com.sky.common.BusinessException.require;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Process-local limits remain independent of Redis cache eviction and failures. */
@Component
public class EvidenceReadLimits {
  private static final int CAPACITY = 10000;
  private final Clock clock;
  private final int globalLimit, accountLimit, perMinute;
  private final Map<String, Window> accounts = new HashMap<>();
  private int running;

  private static final class Window {
    long expires;
    int requests, running;
  }

  public EvidenceReadLimits(
      Clock clock,
      @Value("${campus.evidence-read-concurrency:4}") int globalLimit,
      @Value("${campus.evidence-read-account-concurrency:2}") int accountLimit,
      @Value("${campus.evidence-read-per-minute:120}") int perMinute) {
    require(globalLimit > 0 && globalLimit <= 32 && accountLimit > 0
        && accountLimit <= globalLimit && perMinute > 0 && perMinute <= 10000,
        500, "INVALID_EVIDENCE_LIMITS", "举证读取限制配置不正确");
    this.clock = clock;
    this.globalLimit = globalLimit;
    this.accountLimit = accountLimit;
    this.perMinute = perMinute;
  }

  public synchronized Lease acquire(String account) {
    long now = clock.millis();
    if (accounts.size() >= CAPACITY)
      accounts.entrySet().removeIf(entry -> entry.getValue().running == 0
          && now >= entry.getValue().expires);
    var window = accounts.get(account);
    require(window != null || accounts.size() < CAPACITY,
        503, "EVIDENCE_BUSY", "举证读取繁忙，请稍后重试");
    if (window == null) {
      window = new Window();
      accounts.put(account, window);
    }
    if (now >= window.expires) {
      window.expires = now + 60000;
      window.requests = 0;
    }
    require(window.requests < perMinute, 429, "RATE_LIMITED", "举证读取过于频繁，请稍后重试");
    require(running < globalLimit && window.running < accountLimit,
        503, "EVIDENCE_BUSY", "举证读取繁忙，请稍后重试");
    window.requests++;
    window.running++;
    running++;
    return new Lease(window);
  }

  public final class Lease implements AutoCloseable {
    private final Window window;
    private final AtomicBoolean closed = new AtomicBoolean();

    private Lease(Window window) {
      this.window = window;
    }

    @Override
    public void close() {
      if (!closed.compareAndSet(false, true)) return;
      synchronized (EvidenceReadLimits.this) {
        running--;
        window.running--;
      }
    }
  }
}
