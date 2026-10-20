package com.sky.platform;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sky.business.MenuCache;
import com.sky.infra.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class StoreCatalogQueryTest {
  @Test
  void discoveryBatchQueryCountDoesNotGrowWithStoreCount() {
    for (int size : List.of(1, 10, 200)) {
      var calls = new AtomicInteger();
      var jdbc =
          mock(
              org.springframework.jdbc.core.JdbcTemplate.class,
              invocation -> {
                if (!invocation.getMethod().getName().equals("query"))
                  return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
                calls.incrementAndGet();
                String sql =
                    ((org.springframework.jdbc.core.SqlProvider) invocation.getArgument(0))
                        .getSql();
                var rows = new ArrayList<Map<String, Object>>();
                if (sql.contains("UNION ALL")) {
                  rows.add(
                      new LinkedHashMap<>(
                          Map.of(
                              "id", 1L, "store_id", 1L, "item_type", "DISH", "store_name", "店")));
                  rows.add(
                      new LinkedHashMap<>(
                          Map.of(
                              "id",
                              500L,
                              "store_id",
                              1L,
                              "item_type",
                              "SETMEAL",
                              "store_name",
                              "店")));
                } else if (sql.contains("SELECT p.*")) {
                  long id = sql.contains("FROM dish p") ? 1L : 500L;
                  rows.add(
                      new LinkedHashMap<>(
                          Map.of(
                              "id",
                              id,
                              "store_id",
                              1L,
                              "name",
                              "菜",
                              "price",
                              12,
                              "status",
                              1,
                              "category_status",
                              1,
                              "category_id",
                              1L)));
                } else if (sql.contains("SELECT sd.*")) {
                  rows.add(
                      new LinkedHashMap<>(Map.of("setmeal_id", 500L, "dish_id", 1L, "copies", 2)));
                } else if (sql.contains(" remaining FROM dish")) {
                  rows.add(new LinkedHashMap<>(Map.of("id", 1L, "remaining", 10)));
                }
                return rows;
              });
      var catalog =
          new StoreCatalog(
              new Db(jdbc),
              mock(PlatformScope.class),
              mock(MenuCache.class),
              new Json(new tools.jackson.databind.ObjectMapper()),
              Clock.systemUTC());
      var results =
          catalog.search(
              java.util.stream.LongStream.rangeClosed(1, size).boxed().toList(),
              "菜",
              null,
              LocalDateTime.of(2026, 10, 20, 12, 0));
      assertEquals(2, results.size());
      assertEquals(5, results.getLast().get("remaining"));
      assertEquals(7, calls.get());
    }
  }

  @Test
  void coldAndWarmMenusUseBoundedQueriesAndKeepDynamicStockOutOfCache() {
    for (int size : List.of(10, 100)) {
      var calls = new AtomicInteger();
      var remaining = new AtomicInteger(8);
      Db db =
          mock(
              Db.class,
              invocation -> {
                if (!invocation.getMethod().getName().equals("rows"))
                  return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
                calls.incrementAndGet();
                String sql = invocation.getArgument(0);
                var rows = new ArrayList<Map<String, Object>>();
                if (sql.contains("SELECT p.*") && sql.contains("FROM dish p")) {
                  for (long id = 1; id <= size; id++)
                    rows.add(
                        new LinkedHashMap<>(
                            Map.of(
                                "id",
                                id,
                                "name",
                                "餐品" + id,
                                "status",
                                1,
                                "category_status",
                                1,
                                "price",
                                12,
                                "category_id",
                                1L)));
                } else if (sql.contains("SELECT p.*") && sql.contains("FROM setmeal p")) {
                  rows.add(
                      new LinkedHashMap<>(
                          Map.of(
                              "id",
                              500L,
                              "name",
                              "套餐",
                              "status",
                              1,
                              "category_status",
                              1,
                              "price",
                              20,
                              "category_id",
                              2L)));
                } else if (sql.contains(" remaining FROM dish")) {
                  for (long id = 1; id <= size; id++)
                    rows.add(new LinkedHashMap<>(Map.of("id", id, "remaining", remaining.get())));
                } else if (sql.contains("SELECT sd.*")) {
                  rows.add(
                      new LinkedHashMap<>(Map.of("setmeal_id", 500L, "dish_id", 1L, "copies", 2)));
                } else if (sql.contains("FROM promotion")) {
                  rows.add(
                      new LinkedHashMap<>(
                          Map.of(
                              "id",
                              9L,
                              "item_type",
                              "DISH",
                              "item_id",
                              1L,
                              "price_cents",
                              800L,
                              "ends_at",
                              LocalDateTime.of(2026, 10, 19, 13, 0))));
                }
                return rows;
              });
      MenuCache cache = mock(MenuCache.class);
      var saved = new ArrayList<List<Map<String, Object>>>();
      when(cache.load(anyString(), any()))
          .thenAnswer(
              i -> {
                if (saved.isEmpty())
                  saved.add(((Supplier<List<Map<String, Object>>>) i.getArgument(1)).get());
                return saved.getFirst();
              });
      var catalog =
          new StoreCatalog(
              db,
              mock(PlatformScope.class),
              cache,
              new Json(new tools.jackson.databind.ObjectMapper()),
              Clock.fixed(Instant.parse("2026-10-19T04:00:00Z"), ZoneId.of("Asia/Shanghai")));
      var cold = catalog.products(1, false, null, null);
      assertEquals(7, calls.get());
      assertEquals(size + 1, cold.size());
      assertEquals(4, cold.getLast().get("remaining"));
      assertNotNull(cold.getFirst().get("promotion"));
      calls.set(0);
      remaining.set(2);
      var warm = catalog.products(1, false, null, null);
      assertEquals(3, calls.get());
      assertEquals(1, warm.getLast().get("remaining"));
      assertEquals(8, cold.getFirst().get("remaining"));
      assertEquals(
          1,
          catalog.products(1, false, "餐品1", 1L).stream()
              .filter(p -> p.get("name").equals("餐品1"))
              .count());
    }
  }
}
