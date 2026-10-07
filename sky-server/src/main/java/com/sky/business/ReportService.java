package com.sky.business;

import static com.sky.common.BusinessException.require;

import com.sky.infra.Db;
import com.sky.security.Actor;
import java.io.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class ReportService {
  private final Db db;
  private final Clock clock;

  public ReportService(Db db, Clock clock) {
    this.db = db;
    this.clock = clock;
  }

  public Map<String, Object> range(LocalDate begin, LocalDate end) {
    Actor.admin();
    require(
        begin != null
            && end != null
            && !end.isBefore(begin)
            && ChronoUnit.DAYS.between(begin, end) < 366,
        400,
        "INVALID_RANGE",
        "日期范围最多366天");
    var daily =
        db.rows(
            "SELECT DATE(order_time) date,COUNT(*) orders,SUM(status=5) completed,COALESCE(SUM(CASE"
                + " WHEN status=5 AND pay_status=1 THEN amount ELSE 0 END),0) turnover FROM orders"
                + " WHERE order_time>=? AND order_time<? GROUP BY DATE(order_time) ORDER BY date",
            begin.atStartOfDay(),
            end.plusDays(1).atStartOfDay());
    var users =
        db.rows(
            "SELECT DATE(create_time) date,COUNT(*) new_users FROM user WHERE create_time>=? AND"
                + " create_time<? GROUP BY DATE(create_time) ORDER BY date",
            begin.atStartOfDay(),
            end.plusDays(1).atStartOfDay());
    var top =
        db.rows(
            "SELECT d.name,SUM(d.number) quantity FROM order_detail d JOIN orders o ON"
                + " o.id=d.order_id WHERE o.status=5 AND o.pay_status=1 AND o.order_time>=? AND"
                + " o.order_time<? GROUP BY d.name ORDER BY quantity DESC,d.name LIMIT 10",
            begin.atStartOfDay(),
            end.plusDays(1).atStartOfDay());
    Map<String, Map<String, Object>> byDate = new HashMap<>();
    for (var row : daily) byDate.put(row.get("date").toString(), row);
    Map<String, Object> newUsers = new HashMap<>();
    for (var row : users) newUsers.put(row.get("date").toString(), row.get("new_users"));
    List<Map<String, Object>> result = new ArrayList<>();
    for (LocalDate d = begin; !d.isAfter(end); d = d.plusDays(1)) {
      var row =
          new LinkedHashMap<String, Object>(
              byDate.getOrDefault(
                  d.toString(), Map.of("orders", 0, "completed", 0, "turnover", "0.00")));
      row.put("date", d.toString());
      row.put("new_users", newUsers.getOrDefault(d.toString(), 0));
      result.add(row);
    }
    return Map.of("daily", result, "top10", top);
  }

  public Map<String, Object> dashboard() {
    Actor actor = Actor.employee();
    String scope = actor.role().equals("DELIVERER") ? " AND courier_id=" + actor.id() : "";
    var row =
        db.one(
            "SELECT COUNT(*) orders,COALESCE(SUM(status=2),0) pending,COALESCE(SUM(status=3),0)"
                + " accepted,COALESCE(SUM(status=4),0) delivering,COALESCE(SUM(status=5),0)"
                + " completed,COALESCE(SUM(CASE WHEN status=5 AND pay_status=1 THEN amount ELSE 0"
                + " END),0) turnover FROM orders WHERE order_time>=?"
                + scope,
            LocalDate.now(clock).atStartOfDay());
    row.put(
        "refundFailures",
        actor.role().equals("DELIVERER")
            ? 0
            : db.count("SELECT COUNT(*) FROM refund_task WHERE state='FAILED'"));
    row.put(
        "outboxBacklog",
        actor.role().equals("DELIVERER")
            ? 0
            : db.count("SELECT COUNT(*) FROM outbox_event WHERE state<>'SUCCEEDED'"));
    return row;
  }

  public byte[] export(LocalDate begin, LocalDate end) {
    var report = range(begin, end);
    try (var workbook = new XSSFWorkbook();
        var out = new ByteArrayOutputStream()) {
      var sheet = workbook.createSheet("每日运营");
      var title = sheet.createRow(0);
      String[] cols = {"日期", "订单", "完成", "营业额", "新增用户"};
      for (int i = 0; i < cols.length; i++) title.createCell(i).setCellValue(cols[i]);
      @SuppressWarnings("unchecked")
      var daily = (List<Map<String, Object>>) report.get("daily");
      int n = 1;
      for (var item : daily) {
        var row = sheet.createRow(n++);
        row.createCell(0).setCellValue(item.get("date").toString());
        row.createCell(1).setCellValue(Double.parseDouble(item.get("orders").toString()));
        row.createCell(2).setCellValue(Double.parseDouble(item.get("completed").toString()));
        row.createCell(3).setCellValue(Double.parseDouble(item.get("turnover").toString()));
        row.createCell(4).setCellValue(Double.parseDouble(item.get("new_users").toString()));
      }
      for (int i = 0; i < cols.length; i++) sheet.setColumnWidth(i, 5000);
      workbook.write(out);
      return out.toByteArray();
    } catch (IOException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
