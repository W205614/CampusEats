package com.sky;

import com.sky.infra.RedisAccess;
import com.sky.security.RateLimits;
import java.util.Map;
import org.springframework.test.util.ReflectionTestUtils;

/** Fixed-clock integration fixtures must reset process-local state as well as their Redis database. */
final class RuntimeTestState {
  private RuntimeTestState() {}

  static void reset(RateLimits limits, RedisAccess access) {
    ((Map<?, ?>) ReflectionTestUtils.getField(limits, "local")).clear();
    ReflectionTestUtils.setField(access, "openUntil", 0L);
    ReflectionTestUtils.setField(access, "probe", false);
  }
}
