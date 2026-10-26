package com.sky.platform;

import com.sky.infra.Db;
import java.time.*;
import org.springframework.stereotype.Component;

/** Durable inbox fan-out participates in the producer's database transaction. */
@Component
public class BusinessMessages {
  static final String COURIER_TYPES =
      "'ORDER_READY','ORDER_ASSIGN','ORDER_REASSIGNED','DELIVERY_ASSIGNED','ORDER_PICKUP','ORDER_COMPLETE','ORDER_CANCELLED','ORDER_EXCEPTION_CANCELLED','ORDER_EXCEPTION'";
  private static final java.util.Set<String> COURIER_EVENTS =
      java.util.Set.of(
          "ORDER_READY",
          "ORDER_ASSIGN",
          "ORDER_REASSIGNED",
          "DELIVERY_ASSIGNED",
          "ORDER_PICKUP",
          "ORDER_COMPLETE",
          "ORDER_CANCELLED",
          "ORDER_EXCEPTION_CANCELLED",
          "ORDER_EXCEPTION");
  private final Db db;
  private final Clock clock;

  public BusinessMessages(Db db, Clock clock) {
    this.db = db;
    this.clock = clock;
  }

  public void publish(long event, long order, String type) {
    var o = db.one("SELECT user_id,store_id,courier_id FROM orders WHERE id=?", order);
    if (o == null) return;
    String summary =
        switch (type) {
          case "ORDER_PAID" -> "订单模拟支付成功";
          case "ORDER_READY" -> "餐品已备好，请关注取餐或配送";
          case "DELIVERY_ASSIGNED", "ORDER_ASSIGN", "ORDER_REASSIGNED" -> "配送任务分配已更新";
          case "ORDER_PICKUP" -> "配送员已取餐";
          case "ORDER_COMPLETE", "ORDER_COMPLETED" -> "订单已完成";
          case "ORDER_CANCELLED", "ORDER_EXCEPTION_CANCELLED" -> "订单已取消";
          case "AFTERSALE_APPLY" -> "售后申请已提交";
          case "AFTERSALE_DECIDED" -> "售后申请已有处理结果";
          case "ORDER_REFUNDED" -> "模拟退款已完成";
          case "ORDER_EXCEPTION" -> "订单需要人工关注";
          default -> "订单处理进度已更新";
        };
    var at = LocalDateTime.now(clock);
    String insert =
        "INSERT IGNORE INTO"
            + " inbox_message(event_id,recipient_type,recipient_id,order_id,store_id,event_type,summary,created_at)"
            + " ";
    db.update(
        insert + "VALUES(?,'USER',?,?,?,?,?,?)",
        event,
        o.get("user_id"),
        order,
        o.get("store_id"),
        type,
        summary,
        at);
    db.update(
        insert
            + "SELECT ?,'ADMIN',id,?,?,?,?,? FROM employee WHERE status=1 AND ((store_id=? AND role"
            + " IN('SHOP_MANAGER','OPERATOR')) OR id=?"
            + ("ORDER_EXCEPTION".equals(type) ? " OR role='PLATFORM_ADMIN'" : "")
            + ")",
        event,
        order,
        o.get("store_id"),
        type,
        summary,
        at,
        o.get("store_id"),
        COURIER_EVENTS.contains(type) ? o.get("courier_id") : null);
  }
}
