package com.sky.platform;

import static com.sky.common.BusinessException.require;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

/** Optional keyset cursor and bounded filters for administrative audit history. */
public record AuditQuery(
    Long beforeId,
    Integer limit,
    Long actorId,
    String action,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate begin,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
  public AuditQuery {
    action = action == null || action.isBlank() ? null : action.strip();
  }

  public int pageLimit() {
    return limit == null ? 200 : ListLimit.resolve(limit);
  }

  public void validate() {
    pageLimit();
    require(beforeId == null || beforeId > 0, 400, "INVALID_CURSOR", "分页游标须为正整数");
    require(actorId == null || actorId > 0, 400, "INVALID_ACTOR", "操作人编号须为正整数");
    require(action == null || action.matches("[A-Z][A-Z0-9_]{0,47}"),
        400, "INVALID_ACTION", "操作类型须为完整的操作代码");
    require(begin == null && end == null
            || begin != null && end != null && !end.isBefore(begin)
                && begin.getYear() >= 2000 && end.getYear() <= 2100
                && ChronoUnit.DAYS.between(begin, end) < 366,
        400, "INVALID_RANGE", "请同时提供起止日期，范围最多366天");
  }

  public String conditions(List<Object> args) {
    String sql = "";
    if (beforeId != null) { sql += " AND id<?"; args.add(beforeId); }
    if (actorId != null) { sql += " AND actor_id=?"; args.add(actorId); }
    if (action != null) { sql += " AND action=?"; args.add(action); }
    if (begin != null) {
      sql += " AND created_at>=? AND created_at<?";
      args.add(begin.atStartOfDay());
      args.add(end.plusDays(1).atStartOfDay());
    }
    return sql;
  }
}
