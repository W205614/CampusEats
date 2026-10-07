package com.sky.business;

import com.sky.common.BusinessException;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MockPaymentGateway {
  private final boolean enabled;
  private final AtomicInteger paymentFailures = new AtomicInteger(),
      refundFailures = new AtomicInteger();

  public MockPaymentGateway(@Value("${campus.mock-payment-enabled:true}") boolean enabled) {
    this.enabled = enabled;
  }

  public String pay(long order) {
    BusinessException.require(enabled, 503, "MOCK_DISABLED", "模拟支付未启用");
    if (paymentFailures.getAndUpdate(x -> Math.max(0, x - 1)) > 0)
      throw new BusinessException(503, "MOCK_PAYMENT_FAILED", "模拟支付失败，请使用相同订单重试");
    return "MOCK-" + order;
  }

  public void refund(long order) {
    BusinessException.require(enabled, 503, "MOCK_DISABLED", "模拟退款未启用");
    if (refundFailures.getAndUpdate(x -> Math.max(0, x - 1)) > 0)
      throw new BusinessException(503, "MOCK_REFUND_FAILED", "模拟退款失败");
  }

  public void failNextPayments(int count) {
    paymentFailures.set(count);
  }

  public void failNextRefunds(int count) {
    refundFailures.set(count);
  }
}
