package com.sky.business;

import com.sky.infra.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class OrderEvents {
  private final Db db;
  private final Json json;
  private final Clock clock;

  public OrderEvents(Db db, Json json, Clock clock) {
    this.db = db;
    this.json = json;
    this.clock = clock;
  }

  public void append(String type, long order) {
    LocalDateTime now = LocalDateTime.now(clock);
    db.update(
        "INSERT INTO outbox_event(event_type,aggregate_id,payload,next_attempt_at,created_at)"
            + " VALUES(?,?,?,?,?)",
        type,
        order,
        json.write(Map.of("orderId", String.valueOf(order), "type", type)),
        now,
        now);
  }
}
