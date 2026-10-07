package com.sky.security;

import com.sky.common.BusinessException;
import java.time.Clock;
import java.util.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RateLimits {
  private final StringRedisTemplate redis;
  private final Clock clock;
  private static final int LOCAL_CAPACITY = 10000;
  private final Map<String, Window> local = new HashMap<>();

  private record Window(long expiresAt, long count) {}

  private static final DefaultRedisScript<Long> INCR =
      new DefaultRedisScript<>(
          "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1])"
              + " end; return n",
          Long.class);

  public RateLimits(StringRedisTemplate redis, Clock clock) {
    this.redis = redis;
    this.clock = clock;
  }

  public void check(String key, int max, int seconds) {
    long count;
    try {
      count =
          Objects.requireNonNull(
              redis.execute(INCR, List.of("campus:rate:" + key), String.valueOf(seconds)));
    } catch (Exception ex) {
      count = fallbackCount(key, seconds);
    }
    BusinessException.require(count <= max, 429, "RATE_LIMITED", "操作过于频繁，请稍后重试");
  }

  private synchronized long fallbackCount(String key, int seconds) {
    long now = clock.millis();
    // Reclaim at the boundary before admission; each key keeps its own window lifetime.
    if (local.size() >= LOCAL_CAPACITY)
      local.entrySet().removeIf(entry -> now >= entry.getValue().expiresAt());
    var window = local.get(key);
    if (window == null || now >= window.expiresAt()) {
      if (window == null && local.size() >= LOCAL_CAPACITY)
        throw new BusinessException(503, "RATE_LIMIT_BUSY", "服务繁忙，请稍后重试");
      window = new Window(now + seconds * 1000L, 1);
    } else window = new Window(window.expiresAt(), window.count() + 1);
    local.put(key, window);
    return window.count();
  }
}
