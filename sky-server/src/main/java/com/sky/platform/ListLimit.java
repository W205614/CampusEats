package com.sky.platform;

import static com.sky.common.BusinessException.require;

/** Bounded batch reads for refreshing already loaded history. */
public final class ListLimit {
  public static final int DEFAULT = 100;
  public static final int MAX = 1000;

  private ListLimit() {}

  public static int resolve(Integer limit) {
    require(limit == null || limit >= 1 && limit <= MAX,
        400, "INVALID_LIMIT", "读取条数须为1至1000的整数");
    return limit == null ? DEFAULT : limit;
  }
}
