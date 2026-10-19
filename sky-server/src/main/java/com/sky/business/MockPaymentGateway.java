package com.sky.business;

import com.sky.common.BusinessException;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MockPaymentGateway implements PaymentGateway {
  private final boolean enabled;
  private final AtomicInteger paymentFailures = new AtomicInteger(),
      refundFailures = new AtomicInteger();

  public MockPaymentGateway(@Value("${campus.mock-payment-enabled:true}") boolean enabled) {
    this.enabled = enabled;
  }

  @Override
  public Receipt pay(Payment request) {
    validate(request.businessKey(), request.amountCents(), request.currency());
    BusinessException.require(enabled, 503, "MOCK_DISABLED", "模拟支付未启用");
    if (paymentFailures.getAndUpdate(x -> Math.max(0, x - 1)) > 0)
      throw new BusinessException(503, "MOCK_PAYMENT_FAILED", "模拟支付失败，请使用相同订单重试");
    return new Receipt("MOCK", "MOCK-" + request.businessKey());
  }

  private final java.util.Map<String, Refund> requests = new java.util.HashMap<>();

  @Override
  public synchronized Receipt refund(Refund request) {
    validate(request.businessKey(), request.amountCents(), request.currency());
    BusinessException.require(enabled, 503, "MOCK_DISABLED", "模拟退款未启用");
    BusinessException.require(
        request.paymentKey() != null && !request.paymentKey().isBlank(),
        400,
        "INVALID_PAYMENT",
        "缺少支付编号");
    var old = requests.get(request.businessKey());
    BusinessException.require(
        old == null || old.equals(request), 409, "REFUND_CONFLICT", "退款编号不能更换内容");
    if (old == null) {
      if (refundFailures.getAndUpdate(x -> Math.max(0, x - 1)) > 0)
        throw new BusinessException(503, "MOCK_REFUND_FAILED", "模拟退款失败");
      requests.put(request.businessKey(), request);
    }
    return new Receipt("MOCK", "MOCK-" + request.businessKey());
  }

  private void validate(String key, long amount, String currency) {
    BusinessException.require(
        key != null && !key.isBlank() && amount >= 0 && "CNY".equals(currency),
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
