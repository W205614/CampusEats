package com.sky.platform;

import com.sky.infra.Db;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;

/** Authorization and projection stay with the audit query instead of the HTTP layer. */
@Service
public class AuditHistory {
  public record Entry(long id, String actorType, long actorId, String action, long targetId,
      String detail, LocalDateTime createdAt, Long storeId, Long campusId) {}

  private final Db db;
  private final PlatformScope scope;

  public AuditHistory(Db db, PlatformScope scope) {
    this.db = db;
    this.scope = scope;
  }

  public List<Entry> list(Long store, AuditQuery query) {
    if (store == null) scope.platform();
    else scope.merchant(store, true);
    int pageLimit = query.validate();
    var args = new ArrayList<Object>();
    String filter = "";
    if (store != null) { filter = " AND store_id=?"; args.add(store); }
    filter += query.conditions(args);
    args.add(pageLimit);
    return db.rows("SELECT id,actor_type,actor_id,action,target_id,detail,created_at,store_id,campus_id"
            + " FROM audit_log WHERE 1=1" + filter + " ORDER BY id DESC LIMIT ?", args.toArray())
        .stream().map(row -> new Entry(Db.id(row, "id"), row.get("actor_type").toString(),
            Db.id(row, "actor_id"), row.get("action").toString(), Db.id(row, "target_id"),
            row.get("detail").toString(), Db.time(row.get("created_at")),
            row.get("store_id") == null ? null : Db.id(row, "store_id"),
            row.get("campus_id") == null ? null : Db.id(row, "campus_id")))
        .toList();
  }
}
