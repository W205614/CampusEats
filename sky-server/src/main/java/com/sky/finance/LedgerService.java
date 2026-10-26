package com.sky.finance;

import static com.sky.common.BusinessException.require;

import com.sky.infra.Db;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Called only inside the transaction which commits the corresponding business success. */
@Service
public class LedgerService {
  private final Db db;
  private final Clock clock;

  public LedgerService(Db db, Clock clock) {
    this.db = db;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void payment(long orderId) {
    var order = db.one("SELECT id,store_id,paid_cents,pay_status FROM orders WHERE id=?", orderId);
    require(
        order != null && Db.integer(order, "pay_status") != 0,
        409,
        "LEDGER_SOURCE",
        "支付尚未成功，不能记录流水");
    append("PAYMENT", orderId, orderId, Db.id(order, "store_id"), Db.id(order, "paid_cents"));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void refund(long refundRequestId) {
    var request =
        db.one(
            "SELECT id,order_id,store_id,amount_cents,state FROM refund_request WHERE id=?",
            refundRequestId);
    require(
        request != null && "SUCCEEDED".equals(request.get("state")),
        409,
        "LEDGER_SOURCE",
        "退款尚未成功，不能记录流水");
    append(
        "REFUND",
        refundRequestId,
        Db.id(request, "order_id"),
        Db.id(request, "store_id"),
        -Db.id(request, "amount_cents"));
  }

  private void append(String type, long source, long order, long store, long amount) {
    // A closed-day freeze takes the same store row exclusively. Posting time is read only
    // AFTER admission, so a transaction which waits across midnight posts in the new day;
    // a freeze waits for all admitted older cashflows to commit before choosing its snapshot.
    require(
        db.one("SELECT id FROM store WHERE id=? FOR SHARE", store) != null,
        409,
        "LEDGER_SOURCE",
        "流水所属店铺不存在");
    // A duplicate source can never change its content; retry preserves the first occurrence time.
    db.update(
        "INSERT INTO"
            + " finance_ledger(source_type,source_id,order_id,store_id,amount_cents,occurred_at)"
            + " VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id",
        type,
        source,
        order,
        store,
        amount,
        LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Shanghai"))));
    var saved =
        db.one(
            "SELECT order_id,store_id,amount_cents FROM finance_ledger"
                + " WHERE source_type=? AND source_id=?",
            type,
            source);
    require(
        saved != null
            && Db.id(saved, "order_id") == order
            && Db.id(saved, "store_id") == store
            && Db.id(saved, "amount_cents") == amount,
        409,
        "LEDGER_CONFLICT",
        "流水来源内容发生冲突");
    org.springframework.transaction.support.TransactionSynchronizationManager
        .registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
              @Override
              public void afterCommit() {
                org.slf4j.LoggerFactory.getLogger(LedgerService.class)
                    .info(
                        "Simulated ledger committed; type={} sourceId={} orderId={} storeId={}",
                        type,
                        source,
                        order,
                        store);
              }
            });
  }
}
