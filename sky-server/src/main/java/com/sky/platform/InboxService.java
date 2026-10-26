package com.sky.platform;

import static com.sky.common.BusinessException.require;

import com.sky.infra.Db;
import com.sky.security.Actor;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InboxService {
  public record Message(
      String id,
      String orderId,
      String storeId,
      String eventType,
      String summary,
      LocalDateTime createdAt,
      LocalDateTime readAt) {}

  private final Db db;
  private final CursorPages pages;
  private final Clock clock;

  public InboxService(Db db, CursorPages pages, Clock clock) {
    this.db = db;
    this.pages = pages;
    this.clock = clock;
  }

  private String visible(Actor a, List<Object> args) {
    args.add(a.type());
    args.add(a.id());
    String sql = "m.recipient_type=? AND m.recipient_id=?";
    if (a.type().equals("USER")) {
      args.add(a.id());
      return sql + " AND o.user_id=?";
    }
    return switch (a.role()) {
      case "PLATFORM_ADMIN" -> sql;
      case "SHOP_MANAGER", "OPERATOR" -> {
        args.add(a.storeId());
        yield sql + " AND o.store_id=?";
      }
      case "DELIVERER" -> {
        args.add(a.id());
        args.add(a.campusId());
        yield sql
            + " AND o.courier_id=? AND o.campus_id=? AND m.event_type IN("
            + BusinessMessages.COURIER_TYPES
            + ")";
      }
      default -> sql + " AND false";
    };
  }

  public CursorPages.Page<Message> list(String cursor, Integer limit, Boolean unread) {
    var a = Actor.current();
    int size = CursorPages.size(limit);
    String scope = pages.scope("inbox", unread);
    var args = new ArrayList<Object>();
    String filter = visible(a, args);
    if (Boolean.TRUE.equals(unread)) filter += " AND m.read_at IS NULL";
    args.add(pages.before(cursor, scope));
    args.add(size + 1);
    var rows =
        db.rows(
            "SELECT m.* FROM inbox_message m JOIN orders o ON o.id=m.order_id WHERE "
                + filter
                + " AND m.id<? ORDER BY m.id DESC LIMIT ?",
            args.toArray());
    return pages.finish(
        rows.stream()
            .map(
                r ->
                    new Message(
                        r.get("id").toString(),
                        r.get("order_id").toString(),
                        r.get("store_id").toString(),
                        r.get("event_type").toString(),
                        r.get("summary").toString(),
                        Db.time(r.get("created_at")),
                        r.get("read_at") == null ? null : Db.time(r.get("read_at"))))
            .toList(),
        size,
        scope,
        r -> Long.parseLong(r.id()));
  }

  public long unread() {
    var args = new ArrayList<Object>();
    String filter = visible(Actor.current(), args);
    return db.count(
        "SELECT COUNT(*) FROM inbox_message m JOIN orders o ON o.id=m.order_id WHERE "
            + filter
            + " AND m.read_at IS NULL",
        args.toArray());
  }

  @Transactional
  public void read(long id) {
    var args = new ArrayList<Object>();
    String filter = visible(Actor.current(), args);
    args.add(id);
    var row =
        db.one(
            "SELECT m.id FROM inbox_message m JOIN orders o ON o.id=m.order_id WHERE "
                + filter
                + " AND m.id=?",
            args.toArray());
    require(row != null, 404, "NOT_FOUND", "消息不存在");
    db.update(
        "UPDATE inbox_message SET read_at=COALESCE(read_at,?) WHERE id=?",
        LocalDateTime.now(clock),
        id);
  }
}
