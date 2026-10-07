package com.sky.business;

import static com.sky.common.BusinessException.require;

import com.sky.api.Commands.Page;
import com.sky.infra.Db;
import com.sky.mapper.OrderReadMapper;
import com.sky.security.Actor;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class OrderQueryService {
  private final Db db;
  private final OrderReadMapper mapper;

  public OrderQueryService(Db db, OrderReadMapper mapper) {
    this.db = db;
    this.mapper = mapper;
  }

  public Page<Map<String, Object>> page(
      Actor actor, int page, int size, Integer status, String query) {
    require(
        page >= 1
            && page <= 1000
            && size >= 1
            && size <= 100
            && (query == null || query.length() <= 50),
        400,
        "INVALID_PAGE",
        "分页或搜索条件不正确");
    Long user = actor.type().equals("USER") ? actor.id() : null;
    Long courier = actor.role().equals("DELIVERER") ? actor.id() : null;
    long count = mapper.count(user, courier, status, query);
    var rows = mapper.page(user, courier, status, query, size, (page - 1) * size);
    if (!rows.isEmpty()) {
      var details = mapper.details(rows.stream().map(x -> Db.id(x, "id")).toList());
      for (var row : rows)
        row.put(
            "details",
            details.stream().filter(x -> Db.id(x, "order_id") == Db.id(row, "id")).toList());
    }
    return new Page<>(count, rows);
  }

  public Map<String, Object> detail(Actor actor, long id) {
    var row =
        actor.type().equals("USER")
            ? db.one("SELECT * FROM orders WHERE id=? AND user_id=?", id, actor.id())
            : actor.role().equals("DELIVERER")
                ? db.one("SELECT * FROM orders WHERE id=? AND courier_id=?", id, actor.id())
                : db.one("SELECT * FROM orders WHERE id=?", id);
    require(row != null, 404, "NOT_FOUND", "订单不存在");
    row.put("details", mapper.details(List.of(id)));
    row.put(
        "refund",
        db.one("SELECT id,state,attempts,last_error FROM refund_task WHERE order_id=?", id));
    return row;
  }

  public Map<String, Object> byKey(long user, String key) {
    var row = db.one("SELECT id FROM orders WHERE user_id=? AND request_id=?", user, key);
    require(row != null, 404, "NOT_FOUND", "未找到该请求对应的订单");
    return detail(new Actor(user, "USER", "USER", "", false), Db.id(row, "id"));
  }

  public List<Map<String, Object>> audit(int page, int size) {
    Actor.admin();
    require(page > 0 && page <= 1000 && size > 0 && size <= 100, 400, "INVALID_PAGE", "分页不正确");
    return db.rows(
        "SELECT * FROM audit_log ORDER BY id DESC LIMIT ? OFFSET ?", size, (page - 1) * size);
  }
}
