package com.sky.platform;

import static com.sky.common.BusinessException.require;

public record ReviewQuery(Long beforeId, Integer limit) {
  public int pageLimit() {
    require(beforeId == null || beforeId > 0, 400, "INVALID_CURSOR", "分页游标须为正整数");
    return ListLimit.resolve(limit);
  }
}
