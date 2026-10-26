package com.sky.platform;

import static com.sky.common.BusinessException.require;

import com.sky.infra.Db;
import com.sky.security.Actor;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Both HTTP statistics and exports enter through this proxy and obtain one MVCC snapshot. */
@Service
public class ReportQueryService {
  private final Db db;
  private final PlatformScope scope;
  private final Clock clock;

  public ReportQueryService(Db db, PlatformScope scope, Clock clock) {
    this.db = db;
    this.scope = scope;
    this.clock = clock;
  }

  public record Metrics(
      long paymentCount,
      long childOrderCount,
      long paidCents,
      long shopDiscountCents,
      long platformDiscountCents,
      long refundedCents,
      long completed,
      long completedCents,
      long netCents) {}

  public record Daily(LocalDate date, long paidCents, long paymentCount) {}

  public record StoreTotal(long storeId, String storeName, long paidCents, long childOrderCount) {}

  public record ReportSnapshot(
      Metrics metrics, List<Daily> daily, List<StoreTotal> stores, LocalDateTime generatedAt) {}

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ReportSnapshot view(Long campus, Long store, LocalDate begin, LocalDate end) {
    var data = snapshot(campus, store, begin, end);
    @SuppressWarnings("unchecked")
    var m = (Map<String, Object>) data.get("metrics");
    @SuppressWarnings("unchecked")
    var days = (List<Map<String, Object>>) data.get("daily");
    @SuppressWarnings("unchecked")
    var stores = (List<Map<String, Object>>) data.get("stores");
    return new ReportSnapshot(
        new Metrics(
            Db.id(m, "payment_count"),
            Db.id(m, "child_order_count"),
            Db.id(m, "paid_cents"),
            Db.id(m, "shop_discount_cents"),
            Db.id(m, "platform_discount_cents"),
            Db.id(m, "refunded_cents"),
            Db.id(m, "completed"),
            Db.id(m, "completed_cents"),
            Db.id(m, "net_cents")),
        days.stream()
            .map(
                r ->
                    new Daily(
                        LocalDate.parse(r.get("date").toString()),
                        Db.id(r, "paid_cents"),
                        Db.id(r, "payment_count")))
            .toList(),
        stores.stream()
            .map(
                r ->
                    new StoreTotal(
                        Db.id(r, "store_id"),
                        r.get("store_name").toString(),
                        Db.id(r, "paid_cents"),
                        Db.id(r, "child_order_count")))
            .toList(),
        (LocalDateTime) data.get("generatedAt"));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Map<String, Object> snapshot(Long campus, Long store, LocalDate begin, LocalDate end) {
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
    return Map.of(
        "metrics",
        metrics,
        "daily",
        daily,
        "stores",
        byStore,
        "generatedAt",
        LocalDateTime.now(clock));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public byte[] export(Long campus, Long store, LocalDate begin, LocalDate end) {
    var report = snapshot(campus, store, begin, end);
    try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        var out = new java.io.ByteArrayOutputStream()) {
      var sheet = wb.createSheet("经营统计");
      int row = 0;
      var generated = sheet.createRow(row++);
      generated.createCell(0).setCellValue("快照生成时间 / 上海时间");
      generated.createCell(1).setCellValue(report.get("generatedAt").toString());
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
