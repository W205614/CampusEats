package com.sky.platform;

import static com.sky.common.BusinessException.require;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Calendar dates are inclusive Asia/Shanghai business dates, matching persisted local datetimes.
 */
public record HistoryQuery(
    Long beforeId,
    String state,
    String orderNumber,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate begin,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
    Long taskId,
    Integer limit) {
  public static final HistoryQuery EMPTY = new HistoryQuery(null, null, null, null, null, null);

  public HistoryQuery(Long beforeId, String state, String orderNumber, LocalDate begin, LocalDate end, Long taskId) {
    this(beforeId, state, orderNumber, begin, end, taskId, null);
  }

  public int pageLimit() {
    return ListLimit.resolve(limit);
  }

  public HistoryQuery {
    state = state == null || state.isBlank() ? null : state.strip();
    orderNumber = orderNumber == null || orderNumber.isBlank() ? null : orderNumber.strip();
  }

  public void validate(Set<String> states, boolean tasks) {
    pageLimit();
    require(beforeId == null || beforeId > 0, 400, "INVALID_CURSOR", "分页游标须为正整数");
    require(taskId == null || taskId > 0, 400, "INVALID_TASK", "任务编号须为正整数");
    require(orderNumber == null || orderNumber.length() <= 64, 400, "INVALID_QUERY", "订单号过长");
    require(
        begin == null && end == null
            || begin != null
                && end != null
                && !end.isBefore(begin)
                && begin.getYear() >= 2000
                && end.getYear() <= 2100
                && ChronoUnit.DAYS.between(begin, end) < 366,
        400,
        "INVALID_RANGE",
        "请同时提供起止日期，范围最多366天");
    require(state == null || states.contains(state), 400, "INVALID_STATE", "筛选状态不正确");
    require(tasks || taskId == null, 400, "INVALID_TASK", "售后列表不支持任务编号");
  }

  /** The alias is an internal SQL identifier, never request input. */
  public String conditions(String alias, List<Object> args) {
    String sql = "";
    if (beforeId != null) {
      sql += " AND " + alias + ".id<?";
      args.add(beforeId);
    }
    if (state != null) {
      sql += " AND " + alias + ".state=?";
      args.add(state);
    }
    if (taskId != null) {
      sql += " AND " + alias + ".id=?";
      args.add(taskId);
    }
    if (begin != null) {
      sql += " AND " + alias + ".created_at>=? AND " + alias + ".created_at<?";
      args.add(begin.atStartOfDay());
      args.add(end.plusDays(1).atStartOfDay());
    }
    return sql;
  }
}
