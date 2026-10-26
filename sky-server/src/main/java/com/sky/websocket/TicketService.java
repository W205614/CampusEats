package com.sky.websocket;

import static com.sky.common.BusinessException.require;

import com.sky.common.BusinessException;
import com.sky.infra.*;
import com.sky.security.Actor;
import com.sky.security.RateLimits;
import java.time.*;
import java.util.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TicketService {
  private final StringRedisTemplate redis;
  private final Json json;
  private final Db db;
  private final Clock clock;
  private final RedisAccess access;
  private final RateLimits limits;

  public TicketService(StringRedisTemplate redis, Json json, Db db, Clock clock,
      RedisAccess access, RateLimits limits) {
    this.redis = redis;
    this.json = json;
    this.db = db;
    this.clock = clock;
    this.access = access;
    this.limits = limits;
  }

  public Map<String, Object> issue() {
    return issue("internal");
  }

  public Map<String, Object> issue(String sourceAddress) {
    Actor actor = Actor.employee();
    require(!actor.role().equals("DELIVERER"), 403, "FORBIDDEN", "配送员使用订单查询");
    limits.check("ws-ticket-account:" + actor.id(), 30, 60);
    limits.check("ws-ticket-ip:" + sourceAddress, 60, 60);
    String ticket = UUID.randomUUID().toString();
    try {
      access.call(() -> {
        redis.opsForValue().set("campus:ws:" + ticket, json.write(actor), Duration.ofSeconds(30));
        return true;
      });
    } catch (Exception ex) {
      throw new BusinessException(503, "REDIS_UNAVAILABLE", "实时通知暂不可用，请使用订单查询");
    }
    return Map.of("ticket", ticket, "expiresIn", 30);
  }

  public Actor consume(String ticket) {
    if (ticket == null || !ticket.matches("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}")) return null;
    try {
      String data = access.call(() -> redis.opsForValue().getAndDelete("campus:ws:" + ticket));
      if (data == null) return null;
      var row = json.map(data);
      String jti = row.get("jti").toString();
      long id = Long.parseLong(row.get("id").toString());
      if (db.count(
              "SELECT COUNT(*) FROM auth_session s JOIN employee e ON e.id=s.subject_id WHERE"
                  + " s.jti=? AND s.subject_type='ADMIN' AND s.subject_id=? AND s.revoked=false AND"
                  + " s.expires_at>? AND e.status=1 AND e.auth_version=s.auth_version",
              jti,
              id,
              LocalDateTime.now(clock))
          != 1) return null;
      var employee = db.one("SELECT role,store_id,campus_id FROM employee WHERE id=?", id);
      if (employee == null || employee.get("role").equals("DELIVERER")) return null;
      return new Actor(
          id,
          "ADMIN",
          employee.get("role").toString(),
          jti,
          false,
          employee.get("store_id") == null ? null : Db.id(employee, "store_id"),
          employee.get("campus_id") == null ? null : Db.id(employee, "campus_id"));
    } catch (Exception ex) {
      throw new BusinessException(503, "NOTIFICATION_UNAVAILABLE", "实时通知暂不可用，请使用订单查询");
    }
  }
}
