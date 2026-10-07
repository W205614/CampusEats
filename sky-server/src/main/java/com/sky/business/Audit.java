package com.sky.business;

import com.sky.infra.Db;
import com.sky.security.Actor;
import java.time.*;
import org.springframework.stereotype.Service;

@Service
public class Audit {
  private final Db db;
  private final Clock clock;

  public Audit(Db db, Clock clock) {
    this.db = db;
    this.clock = clock;
  }

  public void write(Actor actor, String action, long target, String detail) {
    db.update(
        "INSERT INTO audit_log(actor_type,actor_id,action,target_id,detail,created_at)"
            + " VALUES(?,?,?,?,?,?)",
        actor == null ? "SYSTEM" : actor.type(),
        actor == null ? 0 : actor.id(),
        action,
        target,
        detail,
        LocalDateTime.now(clock));
  }
}
