package com.sky.business;

import com.sky.common.BusinessException;
import com.sky.infra.Db;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class MockPaymentGateway implements PaymentGateway {
  private final boolean enabled;
  private final Db db;
  private final TransactionTemplate receipts;
  private final AtomicInteger paymentFailures = new AtomicInteger(),
      refundFailures = new AtomicInteger();

  public MockPaymentGateway(
      @Value("${campus.mock-payment-enabled:true}") boolean enabled,
      Db db,
      PlatformTransactionManager manager) {
    this.enabled = enabled;
    this.db = db;
    this.receipts = new TransactionTemplate(manager);
    this.receipts.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Override
  public Receipt pay(Payment request) {
    validate(request.businessKey(), request.amountCents(), request.currency());
    BusinessException.require(enabled, 503, "MOCK_DISABLED", "模拟支付未启用");
    if (paymentFailures.getAndUpdate(x -> Math.max(0, x - 1)) > 0)
      throw new BusinessException(503, "MOCK_PAYMENT_FAILED", "模拟支付失败，请使用相同订单重试");
    return new Receipt("MOCK", "MOCK-" + request.businessKey());
  }

  @Override
  public synchronized Receipt refund(Refund request) {
    validate(request.businessKey(), request.amountCents(), request.currency());
    BusinessException.require(enabled, 503, "MOCK_DISABLED", "模拟退款未启用");
    BusinessException.require(
        request.paymentKey() != null && !request.paymentKey().isBlank()
            && request.paymentKey().length() <= 128,
        400,
        "INVALID_PAYMENT",
        "缺少支付编号");
    // Simulates the external gateway's durable decision, independent of the caller's transaction.
    // A process loss after this commit must not charge the refund again on lease recovery.
    return receipts.execute(status -> {
      var old = db.one("SELECT * FROM mock_refund_receipt WHERE business_key=?", request.businessKey());
      if (old == null) {
        if (refundFailures.getAndUpdate(x -> Math.max(0, x - 1)) > 0)
          throw new BusinessException(503, "MOCK_REFUND_FAILED", "模拟退款失败");
        db.update(
            "INSERT INTO mock_refund_receipt(business_key,payment_key,amount_cents,currency,reference)"
                + " VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE business_key=VALUES(business_key)",
            request.businessKey(), request.paymentKey(), request.amountCents(), request.currency(),
            "MOCK-" + request.businessKey());
        old = db.one("SELECT * FROM mock_refund_receipt WHERE business_key=?", request.businessKey());
      }
      BusinessException.require(
          old != null && request.paymentKey().equals(old.get("payment_key"))
              && request.amountCents() == Db.id(old, "amount_cents")
              && request.currency().equals(old.get("currency")),
          409, "REFUND_CONFLICT", "退款编号不能更换内容");
      return new Receipt("MOCK", String.valueOf(old.get("reference")));
    });
  }

  private void validate(String key, long amount, String currency) {
    BusinessException.require(
        key != null && !key.isBlank() && key.length() <= 128 && amount >= 0 && "CNY".equals(currency),
        400,
        "INVALID_PAYMENT",
        "支付编号、金额或币种不正确");
  }

  public void failNextPayments(int count) {
    paymentFailures.set(count);
  }

  public void failNextRefunds(int count) {
    refundFailures.set(count);
  }
}
