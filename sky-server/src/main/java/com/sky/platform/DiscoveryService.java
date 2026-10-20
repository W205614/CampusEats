package com.sky.platform;

import static com.sky.common.BusinessException.require;

import com.sky.infra.*;
import com.sky.security.Actor;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiscoveryService {
  private final Db db;
  private final PlatformScope scope;
  private final StoreCatalog catalog;
  private final Clock clock;
  private final DirectoryService directory;

  public DiscoveryService(
      Db db, PlatformScope scope, StoreCatalog catalog, Clock clock, DirectoryService directory) {
    this.db = db;
    this.scope = scope;
    this.catalog = catalog;
    this.clock = clock;
    this.directory = directory;
  }

  public List<Map<String, Object>> search(long campus, String query, Long category, Boolean open) {
    require(query == null || query.length() <= 80, 400, "INVALID_QUERY", "搜索关键词过长");
    LocalDateTime at = LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Shanghai")));
    var stores =
        db.rows(
            "SELECT s.* FROM store s JOIN campus c ON c.id=s.campus_id WHERE s.campus_id=?"
                + " AND s.enabled=true AND c.enabled=true"
                + (Boolean.TRUE.equals(open) ? " AND s.open=true" : "")
                + " ORDER BY s.id LIMIT 200",
            campus);
    var ids =
        stores.stream()
            .filter(s -> open == null || directory.accepting(s, at) == open)
            .map(s -> Db.id(s, "id"))
            .toList();
    return catalog.search(ids, query, category, at);
  }

  public List<Map<String, Object>> favorites() {
    return db.rows(
        "SELECT f.*,s.name store_name,CASE WHEN f.target_type='STORE' THEN s.name WHEN"
            + " f.target_type='DISH' THEN d.name ELSE m.name END name FROM favorite f JOIN store s"
            + " ON s.id=f.store_id LEFT JOIN dish d ON f.target_type='DISH' AND d.id=f.target_id"
            + " AND d.store_id=f.store_id LEFT JOIN setmeal m ON f.target_type='SETMEAL' AND"
            + " m.id=f.target_id AND m.store_id=f.store_id WHERE f.user_id=? ORDER BY"
            + " f.store_id,f.target_type,f.target_id",
        Actor.user().id());
  }

  @Transactional
  public void favorite(long store, String type, long id, boolean enabled) {
    long user = Actor.user().id();
    if (enabled) {
      scope.store(store, true);
      if (!type.equals("STORE")) catalog.product(store, type, id, false);
      else require(store == id, 400, "INVALID_FAVORITE", "店铺收藏参数不正确");
      db.update(
          "INSERT IGNORE INTO favorite(user_id,target_type,target_id,store_id) VALUES(?,?,?,?)",
          user,
          type,
          id,
          store);
    } else
      db.update(
          "DELETE FROM favorite WHERE user_id=? AND target_type=? AND target_id=? AND store_id=?",
          user,
          type,
          id,
          store);
  }

  @Transactional
  public long review(long order, PlatformCommands.Review input) {
    long user = Actor.user().id();
    var o = scope.order(order, true);
    require(Db.integer(o, "status") == 5, 409, "REVIEW_STATE", "完成订单后才可评价");
    require(
        db.count("SELECT COUNT(*) FROM review WHERE order_id=?", order) == 0,
        409,
        "REVIEW_EXISTS",
        "该订单已评价");
    return db.insert(
        "INSERT INTO review(order_id,user_id,store_id,rating,content,created_at)"
            + " VALUES(?,?,?,?,?,?)",
        order,
        user,
        o.get("store_id"),
        input.rating(),
        input.content(),
        LocalDateTime.now(clock));
  }

  public Map<String, Object> reviews(long store, boolean admin) {
    if (admin) scope.merchant(store, false);
    else scope.store(store, false);
    var rows =
        db.rows(
            "SELECT id,order_id,rating,content,reply,hidden,hide_reason,created_at FROM review"
                + " WHERE store_id=?"
                + (admin ? "" : " AND hidden=false")
                + " ORDER BY id DESC LIMIT 100",
            store);
    var stats =
        db.one(
            "SELECT COUNT(*) total,COALESCE(AVG(rating),0) rating FROM review WHERE store_id=? AND"
                + " hidden=false",
            store);
    return Map.of("stats", stats, "records", rows);
  }

  @Transactional
  public void reply(long id, String text) {
    var r = db.one("SELECT * FROM review WHERE id=? FOR UPDATE", id);
    require(r != null, 404, "NOT_FOUND", "评价不存在");
    scope.merchant(Db.id(r, "store_id"), false);
    require(r.get("reply") == null, 409, "REPLY_EXISTS", "每条评价只能回复一次");
    db.update("UPDATE review SET reply=? WHERE id=?", text, id);
    scope.audit("REVIEW_REPLY", id, Db.id(r, "store_id"), "商家回复评价");
  }

  @Transactional
  public void hide(long id, String reason) {
    scope.platform();
    require(reason.length() <= 200, 400, "INVALID_REASON", "隐藏原因最多200字");
    var r = db.one("SELECT * FROM review WHERE id=? FOR UPDATE", id);
    require(r != null, 404, "NOT_FOUND", "评价不存在");
    db.update("UPDATE review SET hidden=true,hide_reason=? WHERE id=?", reason, id);
    scope.audit("REVIEW_HIDE", id, Db.id(r, "store_id"), reason);
  }

  public Map<String, Object> report(Long campus, Long store, LocalDate begin, LocalDate end) {
    Actor a = Actor.employee();
    if (store != null) scope.merchant(store, true);
    else scope.platform();
    require(
        begin != null
            && end != null
            && !end.isBefore(begin)
            && java.time.temporal.ChronoUnit.DAYS.between(begin, end) < 366,
        400,
        "INVALID_RANGE",
        "日期范围最多366天");
    String filter = "";
    var args = new ArrayList<Object>();
    if (store != null) {
      filter += " AND o.store_id=?";
      args.add(store);
    }
    if (campus != null) {
      filter += " AND o.campus_id=?";
      args.add(campus);
    }
    var payments = new ArrayList<Object>();
    payments.add(begin.atStartOfDay());
    payments.add(end.plusDays(1).atStartOfDay());
    payments.addAll(args);
    var paid =
        db.one(
            "SELECT COUNT(DISTINCT o.checkout_id) payment_count,COUNT(*)"
                + " child_order_count,COALESCE(SUM(o.paid_cents),0)"
                + " paid_cents,COALESCE(SUM(o.shop_discount_cents),0)"
                + " shop_discount_cents,COALESCE(SUM(o.platform_discount_cents),0)"
                + " platform_discount_cents FROM orders o JOIN checkout c ON c.id=o.checkout_id"
                + " WHERE c.paid_at>=? AND c.paid_at<?"
                + filter,
            payments.toArray());
    var refunds =
        db.one(
            "SELECT COALESCE(SUM(r.amount_cents),0) refunded_cents FROM refund_request r JOIN"
                + " orders o ON o.id=r.order_id WHERE r.state='SUCCEEDED' AND r.succeeded_at>=? AND"
                + " r.succeeded_at<?"
                + filter,
            payments.toArray());
    var completed =
        db.one(
            "SELECT COUNT(*) completed,COALESCE(SUM(ROUND(o.amount*100)),0) completed_cents FROM"
                + " orders o WHERE o.status=5 AND o.completed_at>=? AND o.completed_at<?"
                + filter,
            payments.toArray());
    var metrics = new LinkedHashMap<String, Object>();
    metrics.putAll(paid);
    metrics.putAll(refunds);
    metrics.putAll(completed);
    long p = ((Number) paid.get("paid_cents")).longValue(),
        r = ((Number) refunds.get("refunded_cents")).longValue();
    metrics.put("net_cents", p - r);
    var daily =
        db.rows(
            "SELECT DATE(c.paid_at) date,SUM(o.paid_cents) paid_cents,COUNT(DISTINCT c.id)"
                + " payment_count FROM orders o JOIN checkout c ON c.id=o.checkout_id WHERE"
                + " c.paid_at>=? AND c.paid_at<?"
                + filter
                + " GROUP BY DATE(c.paid_at) ORDER BY date",
            payments.toArray());
    var byStore =
        db.rows(
            "SELECT o.store_id,s.name store_name,SUM(o.paid_cents) paid_cents,COUNT(*)"
                + " child_order_count FROM orders o JOIN checkout c ON c.id=o.checkout_id JOIN"
                + " store s ON s.id=o.store_id WHERE c.paid_at>=? AND c.paid_at<?"
                + filter
                + " GROUP BY o.store_id,s.name ORDER BY o.store_id",
            payments.toArray());
    return Map.of("metrics", metrics, "daily", daily, "stores", byStore);
  }

  public byte[] export(Long campus, Long store, LocalDate begin, LocalDate end) {
    var report = report(campus, store, begin, end);
    try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        var out = new java.io.ByteArrayOutputStream()) {
      var sheet = wb.createSheet("经营统计");
      int row = 0;
      @SuppressWarnings("unchecked")
      var metrics = (Map<String, Object>) report.get("metrics");
      for (var entry : metrics.entrySet()) {
        var line = sheet.createRow(row++);
        line.createCell(0).setCellValue(metricLabel(entry.getKey()));
        line.createCell(1)
            .setCellValue(
                entry.getKey().endsWith("_cents")
                    ? Money.text(((Number) entry.getValue()).longValue())
                    : entry.getValue().toString());
      }
      sheet.setColumnWidth(0, 8000);
      sheet.setColumnWidth(1, 6000);
      @SuppressWarnings("unchecked")
      var daily = (List<Map<String, Object>>) report.get("daily");
      @SuppressWarnings("unchecked")
      var stores = (List<Map<String, Object>>) report.get("stores");
      exportRows(wb, "每日支付", daily, List.of("date", "payment_count", "paid_cents"));
      exportRows(wb, "店铺分布", stores, List.of("store_name", "child_order_count", "paid_cents"));
      wb.write(out);
      return out.toByteArray();
    } catch (java.io.IOException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private String metricLabel(String key) {
    return switch (key) {
      case "payment_count" -> "合单支付笔数";
      case "child_order_count" -> "店铺子订单数";
      case "paid_cents" -> "支付额 / 元";
      case "refunded_cents" -> "成功退款额 / 元";
      case "net_cents" -> "净收款 / 元";
      case "shop_discount_cents" -> "店铺优惠 / 元";
      case "platform_discount_cents" -> "平台优惠 / 元";
      case "completed" -> "完成订单数";
      case "completed_cents" -> "完成单成交额 / 元";
      case "date" -> "日期";
      case "store_name" -> "店铺";
      default -> key;
    };
  }

  private void exportRows(
      org.apache.poi.xssf.usermodel.XSSFWorkbook wb,
      String name,
      List<Map<String, Object>> rows,
      List<String> columns) {
    var sheet = wb.createSheet(name);
    var heading = sheet.createRow(0);
    for (int c = 0; c < columns.size(); c++) {
      heading.createCell(c).setCellValue(metricLabel(columns.get(c)));
      sheet.setColumnWidth(c, 6500);
    }
    int index = 1;
    for (var record : rows) {
      var row = sheet.createRow(index++);
      for (int c = 0; c < columns.size(); c++) {
        String key = columns.get(c);
        Object value = record.get(key);
        row.createCell(c)
            .setCellValue(
                value == null
                    ? ""
                    : key.endsWith("_cents")
                        ? Money.text(((Number) value).longValue())
                        : value.toString());
      }
    }
  }
}
