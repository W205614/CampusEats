package com.sky.websocket;

import static com.sky.common.BusinessException.require;

import com.sky.common.BusinessException;
import com.sky.infra.*;
import com.sky.security.Actor;
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

  public TicketService(StringRedisTemplate redis, Json json, Db db, Clock clock) {
    this.redis = redis;
    this.json = json;
    this.db = db;
    this.clock = clock;
  }

  public Map<String, Object> issue() {
    Actor actor = Actor.employee();
    require(!actor.role().equals("DELIVERER"), 403, "FORBIDDEN", "配送员使用订单查询");
    String ticket = UUID.randomUUID().toString();
    try {
      redis.opsForValue().set("campus:ws:" + ticket, json.write(actor), Duration.ofSeconds(30));
    } catch (Exception ex) {
      throw new BusinessException(503, "REDIS_UNAVAILABLE", "实时通知暂不可用，请使用订单查询");
    }
    return Map.of("ticket", ticket, "expiresIn", 30);
  }

  public Actor consume(String ticket) {
    try {
      String data = redis.opsForValue().getAndDelete("campus:ws:" + ticket);
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
      return new Actor(id, "ADMIN", row.get("role").toString(), jti, false);
    } catch (Exception ex) {
      return null;
    }
  }
}
