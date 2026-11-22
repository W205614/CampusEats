package com.campuseats.platform;

import static com.campuseats.common.BusinessException.require;

import com.campuseats.common.Hashes;
import com.campuseats.infra.*;
import com.campuseats.security.Actor;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class DiscoveryPages {
  public record Page(List<Map<String, Object>> records, String nextCursor, boolean hasMore) {}

  private final Db db;
  private final Json json;
  private final Clock clock;
  private final DiscoveryCursor cursors;
  private final DirectoryService directory;
  private final StoreCatalog catalog;

  public DiscoveryPages(
      Db db,
      Json json,
      Clock clock,
      DiscoveryCursor cursors,
      DirectoryService directory,
      StoreCatalog catalog) {
    this.db = db;
    this.json = json;
    this.clock = clock;
    this.cursors = cursors;
    this.directory = directory;
    this.catalog = catalog;
  }

  public Page stores(
      Long campus, String query, Boolean open, String cursor, Integer limit, boolean admin) {
    int size = limit(limit);
    query = query(query);
    require(campus == null || campus > 0, 400, "INVALID_CAMPUS", "校区编号不正确");
    Actor actor = admin ? Actor.employee() : null;
    if (admin)
      require(
          Set.of("PLATFORM_ADMIN", "SHOP_MANAGER", "OPERATOR").contains(actor.role()),
          403,
          "FORBIDDEN",
          "无店铺管理权限");
    String scope = scope("stores", campus, query, open, admin ? actor : "public");
    var position = cursors.decode(cursor, scope);
    var params = parameters(query, open, now());
    params.put("after", position.id());
    params.put("fetchLimit", size + 1);
    String filter = admin ? "" : " AND s.enabled=true AND c.enabled=true";
    if (admin && !actor.role().equals("PLATFORM_ADMIN")) {
      require(actor.storeId() != null, 403, "FORBIDDEN", "无店铺管理权限");
      filter += " AND s.id=:ownStore";
      params.put("ownStore", actor.storeId());
    }
    if (campus != null) {
      filter += " AND s.campus_id=:campus";
      params.put("campus", campus);
    }
    var rows =
        db.named.queryForList(
            "SELECT s.*,c.name campus_name FROM store s JOIN campus c ON c.id=s.campus_id"
                + " WHERE s.id>:after"
                + filter
                + " AND LOCATE(CAST(:query AS BINARY),CAST(s.name AS BINARY))>0"
                + openFilter(open)
                + " ORDER BY s.id LIMIT :fetchLimit",
            params);
    boolean more = rows.size() > size;
    var selected = new ArrayList<>(rows.subList(0, Math.min(size, rows.size())));
    String next =
        more
            ? cursors.encode(
                scope, new DiscoveryCursor.Position(0, 0, Db.id(selected.getLast(), "id")))
            : null;
    decorateStores(selected, (LocalDateTime) params.get("at"));
    return new Page(selected, next, more);
  }

  public Map<String, Object> store(long id) {
    require(id > 0, 400, "INVALID_STORE", "店铺编号不正确");
    var row =
        db.one(
            "SELECT s.*,c.name campus_name FROM store s JOIN campus c ON c.id=s.campus_id"
                + " WHERE s.id=? AND s.enabled=true AND c.enabled=true",
            id);
    require(row != null, 404, "NOT_FOUND", "店铺不存在或已停用");
    decorateStores(List.of(row), now());
    return row;
  }

  public Page search(
      long campus, String query, Long category, Boolean open, String cursor, Integer limit) {
    return search(campus, query, category, open, cursor, limit, false, null);
  }

  public Page search(
      long campus, String query, Long category, Boolean open, String cursor, Integer limit,
      boolean inStock, Long maxPriceCents) {
    int size = limit(limit);
    query = query(query);
    require(campus > 0 && (category == null || category > 0), 400, "INVALID_QUERY", "搜索范围不正确");
    require(maxPriceCents == null || (maxPriceCents >= 0 && maxPriceCents <= 9999999999L),
        400, "INVALID_QUERY", "餐品价格上限不正确");
    String scope = inStock || maxPriceCents != null
        ? scope("search", campus, query, category, open, inStock, maxPriceCents)
        : scope("search", campus, query, category, open);
    var position = cursors.decode(cursor, scope);
    var at = now();
    var params = parameters(query, open, at);
    params.put("campus", campus);
    params.put("afterStore", position.storeId());
    params.put("afterRank", position.rank());
    params.put("afterId", position.id());
    params.put("fetchLimit", size + 1);
    if (category != null) params.put("category", category);
    if (inStock) params.put("date", at.toLocalDate());
    if (maxPriceCents != null) params.put("maxPrice", maxPriceCents);
    var branches = new ArrayList<String>();
    for (int rank = 0; rank < 2; rank++) {
      String type = rank == 0 ? "DISH" : "SETMEAL";
      // The first K global rows can only contain the first K rows of either ordered branch.
      // Bound the UNION materialization to 2*K candidates without changing substring semantics.
      String table = PlatformScope.table(type);
      // Product-first index order lets broad matches stop at K instead of sorting every match.
      // Literal substring searches with few/no matches can still scan the remaining index range.
      branches.add(
          "(SELECT STRAIGHT_JOIN p.id,p.store_id,'"
              + type
              + "' item_type,"
              + rank
              + " item_rank,s.name store_name FROM "
              + table
              + " p FORCE INDEX(ix_"
              + table
              + "_search_order) JOIN store s ON s.id=p.store_id"
              + " JOIN category c ON c.id=p.category_id AND c.store_id=p.store_id"
              + " JOIN campus campus ON campus.id=s.campus_id"
              + " WHERE s.campus_id=:campus AND s.enabled=true AND campus.enabled=true"
              + " AND p.status=1 AND c.status=1"
              + (category == null ? "" : " AND p.category_id=:category")
              + " AND (p.store_id>:afterStore OR (p.store_id=:afterStore AND ("
              + rank
              + ">:afterRank OR ("
              + rank
              + "=:afterRank AND p.id>:afterId))))"
              + " AND (LOCATE(CAST(:query AS BINARY),CAST(p.name AS BINARY))>0"
              + " OR LOCATE(CAST(:query AS BINARY),CAST(s.name AS BINARY))>0)"
              + openFilter(open)
              + (inStock ? stockFilter(rank == 0) : "")
              + (maxPriceCents == null ? "" :
                  " AND LEAST(p.price*100,COALESCE((SELECT a.price_cents FROM promotion a"
                      + " WHERE a.store_id=p.store_id AND a.item_type='" + type + "' AND a.item_id=p.id"
                      + " AND a.enabled=true AND a.starts_at<=:at AND a.ends_at>:at ORDER BY a.id LIMIT 1),"
                      + " p.price*100))<=:maxPrice")
              + " ORDER BY p.store_id,p.id LIMIT :fetchLimit)");
    }
    var rows =
        db.named.queryForList(
            "SELECT * FROM ("
                + String.join(" UNION ALL ", branches)
                + ") candidates ORDER BY store_id,item_rank,id LIMIT :fetchLimit",
            params);
    boolean more = rows.size() > size;
    var selected = new ArrayList<>(rows.subList(0, Math.min(size, rows.size())));
    String next = null;
    if (more) {
      var last = selected.getLast();
      next =
          cursors.encode(
              scope,
              new DiscoveryCursor.Position(
                  Db.id(last, "store_id"), Db.integer(last, "item_rank"), Db.id(last, "id")));
    }
    return new Page(catalog.searchDetails(selected, at), next, more);
  }

  private static String stockFilter(boolean dish) {
    if (dish)
      return " AND COALESCE((SELECT q.total-q.reserved-q.consumed FROM daily_quota q"
          + " WHERE q.dish_id=p.id AND q.business_date=:date),p.default_quota)>0";
    return " AND EXISTS(SELECT 1 FROM setmeal_dish sd WHERE sd.setmeal_id=p.id)"
        + " AND NOT EXISTS(SELECT 1 FROM setmeal_dish sd"
        + " LEFT JOIN dish d ON d.id=sd.dish_id"
        + " LEFT JOIN daily_quota q ON q.dish_id=d.id AND q.business_date=:date"
        + " WHERE sd.setmeal_id=p.id AND (d.id IS NULL OR d.status<>1 OR"
        + " COALESCE(q.total,d.default_quota)-COALESCE(q.reserved,0)-COALESCE(q.consumed,0)<sd.copies))";
  }

  private void decorateStores(List<Map<String, Object>> rows, LocalDateTime at) {
    for (var row : rows) {
      row.put("accepting", directory.accepting(row, at));
      row.put("hours", json.strings(row.get("hours").toString()));
    }
  }

  private Map<String, Object> parameters(String query, Boolean open, LocalDateTime at) {
    var params = new HashMap<String, Object>();
    params.put("query", query);
    params.put("at", at);
    params.put("time", at.format(DateTimeFormatter.ofPattern("HH:mm")));
    if (open != null) params.put("open", open);
    return params;
  }

  // Slots are validated zero-padded HH:mm-HH:mm values, so comparison also supports 24:00.
  private String openFilter(Boolean open) {
    return open == null
        ? ""
        : " AND (s.enabled=true AND s.open=true AND EXISTS"
            + " (SELECT 1 FROM JSON_TABLE(s.hours,'$[*]' COLUMNS(slot varchar(11) PATH '$')) h"
            + " WHERE :time>=LEFT(h.slot,5) AND :time<SUBSTRING(h.slot,7,5)))=:open";
  }

  private String scope(Object... values) {
    return Hashes.sha256(json.write(Arrays.asList(values)));
  }

  private static int limit(Integer value) {
    require(value == null || value >= 1 && value <= 200, 400, "INVALID_LIMIT", "每页数量须为1至200");
    return value == null ? 50 : value;
  }

  private static String query(String value) {
    require(value == null || value.length() <= 80, 400, "INVALID_QUERY", "搜索关键词最多80字");
    return value == null ? "" : value.strip();
  }

  private LocalDateTime now() {
    return LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Shanghai")));
  }
}
