package com.sky.security;

import com.sky.common.BusinessException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RateLimits {
  private final StringRedisTemplate redis;
  private final Map<String, Window> local = new ConcurrentHashMap<>();

  private record Window(long start, int count) {}

  private static final DefaultRedisScript<Long> INCR =
      new DefaultRedisScript<>(
          "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1])"
              + " end; return n",
          Long.class);

  public RateLimits(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public void check(String key, int max, int seconds) {
    long count;
    try {
      count =
          Objects.requireNonNull(
              redis.execute(INCR, List.of("campus:rate:" + key), String.valueOf(seconds)));
    } catch (Exception ex) {
      long now = System.currentTimeMillis();
      if (local.size() > 10000)
        local.entrySet().removeIf(e -> now - e.getValue().start() > seconds * 1000L);
      if (local.size() >= 10000 && !local.containsKey(key))
        throw new BusinessException(503, "RATE_LIMIT_BUSY", "服务繁忙，请稍后重试");
      count =
          local
              .compute(
                  key,
                  (k, v) ->
                      v == null || now - v.start() > seconds * 1000L
                          ? new Window(now, 1)
                          : new Window(v.start(), v.count() + 1))
              .count();
    }
    BusinessException.require(count <= max, 429, "RATE_LIMITED", "操作过于频繁，请稍后重试");
  }
}
