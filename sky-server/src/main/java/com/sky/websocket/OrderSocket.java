package com.sky.websocket;

import com.sky.infra.Db;
import com.sky.security.Actor;
import jakarta.annotation.PreDestroy;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class OrderSocket extends TextWebSocketHandler {
  private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
  private final ThreadPoolExecutor sender =
      new ThreadPoolExecutor(
          4,
          4,
          30,
          TimeUnit.SECONDS,
          new ArrayBlockingQueue<>(256),
          new ThreadPoolExecutor.AbortPolicy());
  private final Db db;
  private final Clock clock;

  public OrderSocket(Db db, Clock clock) {
    this.db = db;
    this.clock = clock;
  }

  @Override
  public synchronized void afterConnectionEstablished(WebSocketSession session) throws Exception {
    Actor actor = (Actor) session.getAttributes().get("actor");
    long own =
        sessions.values().stream()
            .filter(s -> ((Actor) s.getAttributes().get("actor")).id() == actor.id())
            .count();
    if (sessions.size() >= 100 || own >= 2) {
      session.close(CloseStatus.POLICY_VIOLATION);
      return;
    }
    sessions.put(
        session.getId(),
        new org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator(
            session, 3000, 65536));
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    sessions.remove(session.getId());
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    if (message.getPayloadLength() > 1024) session.close(CloseStatus.TOO_BIG_TO_PROCESS);
  }

  /** Enqueue a best-effort hint; clients recover state by querying orders. */
  public void publish(String payload) {
    Long storeId = eventStore(payload);
    sender.execute(
        () -> {
          for (var entry : sessions.entrySet()) {
            var session = entry.getValue();
            try {
              if (!active((Actor) session.getAttributes().get("actor"))) {
                session.close(CloseStatus.POLICY_VIOLATION);
                sessions.remove(entry.getKey());
                continue;
              }
              Actor actor = (Actor) session.getAttributes().get("actor");
              if (session.isOpen() && authorized(actor, storeId))
                session.sendMessage(new TextMessage(payload));
            } catch (Exception ex) {
              sessions.remove(entry.getKey());
              try {
                session.close();
              } catch (Exception ignored) {
              }
            }
          }
        });
  }

  private Long eventStore(String payload) {
    var matcher =
        java.util.regex.Pattern.compile("\\\"orderId\\\"\\s*:\\s*\\\"?(\\d+)").matcher(payload);
    if (!matcher.find()) return null;
    var order = db.one("SELECT store_id FROM orders WHERE id=?", Long.parseLong(matcher.group(1)));
    return order == null ? null : Db.id(order, "store_id");
  }

  static boolean authorized(Actor actor, Long storeId) {
    return actor.role().equals("PLATFORM_ADMIN")
        || actor.role().equals("ADMIN")
        || Set.of("SHOP_MANAGER", "OPERATOR").contains(actor.role())
            && storeId != null
            && Objects.equals(actor.storeId(), storeId);
  }

  @Scheduled(fixedDelay = 20000)
  public void validateSessions() {
    for (var entry : sessions.entrySet())
      try {
        if (!active((Actor) entry.getValue().getAttributes().get("actor"))) {
          entry.getValue().close(CloseStatus.POLICY_VIOLATION);
          sessions.remove(entry.getKey());
        }
      } catch (Exception ignored) {
      }
  }

  private boolean active(Actor actor) {
    return !actor.role().equals("DELIVERER")
        && db.count(
                "SELECT COUNT(*) FROM auth_session s JOIN employee e ON e.id=s.subject_id WHERE"
                    + " s.jti=? AND s.subject_type='ADMIN' AND s.revoked=false AND s.expires_at>?"
                    + " AND e.status=1 AND e.auth_version=s.auth_version",
                actor.jti(),
                LocalDateTime.now(clock))
            == 1;
  }

  @PreDestroy
  public void stop() {
    sender.shutdownNow();
    for (var s : sessions.values())
      try {
        s.close();
      } catch (Exception ignored) {
      }
  }
}
