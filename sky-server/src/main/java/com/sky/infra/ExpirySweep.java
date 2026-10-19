package com.sky.infra;

import java.util.*;
import java.util.function.*;

/**
 * Each attempted id advances the cursor, including failures; reaching the tail wraps next round.
 */
public final class ExpirySweep {
  private long cursor;

  public void run(
      RuntimeOperations.Round round,
      LongFunction<List<Map<String, Object>>> select,
      LongConsumer process) {
    var rows = select.apply(cursor);
    if (rows.isEmpty()) {
      cursor = 0;
      return;
    }
    for (var row : rows) {
      if (!round.withinBudget()) break;
      long id = Db.id(row, "id");
      cursor = id;
      try {
        process.accept(id);
      } catch (Exception ex) {
        round.failed(ex, id);
      }
    }
  }
}
