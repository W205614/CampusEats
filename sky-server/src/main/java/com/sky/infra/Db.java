package com.sky.infra;

import java.sql.Statement;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

@org.springframework.stereotype.Component
public class Db {
  public final JdbcTemplate jdbc;
  public final NamedParameterJdbcTemplate named;

  public Db(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
    this.named = new NamedParameterJdbcTemplate(jdbc);
  }

  public List<Map<String, Object>> rows(String sql, Object... args) {
    return jdbc.queryForList(sql, args);
  }

  public Map<String, Object> one(String sql, Object... args) {
    var rows = rows(sql, args);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  public int update(String sql, Object... args) {
    return jdbc.update(sql, args);
  }

  public long count(String sql, Object... args) {
    Long n = jdbc.queryForObject(sql, Long.class, args);
    return n == null ? 0 : n;
  }

  public long insert(String sql, Object... args) {
    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(
        c -> {
          var p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
          for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
          return p;
        },
        keys);
    return Objects.requireNonNull(keys.getKey()).longValue();
  }

  public static long id(Map<String, Object> row, String field) {
    return ((Number) row.get(field)).longValue();
  }

  public static int integer(Map<String, Object> row, String field) {
    return ((Number) row.get(field)).intValue();
  }

  public static java.time.LocalDateTime time(Object value) {
    if (value instanceof java.time.LocalDateTime t) return t;
    if (value instanceof java.sql.Timestamp t) return t.toLocalDateTime();
    throw new IllegalArgumentException("Expected datetime");
  }

  public static boolean bool(Map<String, Object> row, String field) {
    Object v = row.get(field);
    return Boolean.TRUE.equals(v) || (v instanceof Number n && n.intValue() != 0);
  }
}
