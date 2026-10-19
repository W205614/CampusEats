package com.sky.business;

import static org.junit.jupiter.api.Assertions.*;

import com.sky.common.BusinessException;
import org.junit.jupiter.api.Test;

class PaymentGatewayTest {
  @Test
  void refundIdentityIncludesAmountAndOriginalPaymentAndSurvivesARetry() {
    PaymentGateway port = new MockPaymentGateway(true);
    var request = new PaymentGateway.Refund("refund-request:3", "checkout:2", 1500, "CNY");
    var receipt = port.refund(request);
    ((MockPaymentGateway) port).failNextRefunds(1);
    assertEquals(receipt, port.refund(request));
    assertEquals(
        "REFUND_CONFLICT",
        assertThrows(
                BusinessException.class,
                () ->
                    port.refund(
                        new PaymentGateway.Refund("refund-request:3", "checkout:4", 1500, "CNY")))
            .code());
    assertEquals(
        "REFUND_CONFLICT",
        assertThrows(
                BusinessException.class,
                () ->
                    port.refund(
                        new PaymentGateway.Refund("refund-request:3", "checkout:2", 1501, "CNY")))
            .code());
    assertEquals(
        "MOCK_REFUND_FAILED",
        assertThrows(
                BusinessException.class,
                () ->
                    port.refund(
                        new PaymentGateway.Refund("refund-request:4", "checkout:2", 1500, "CNY")))
            .code());
  }

  @Test
  void disabledMockNeverProducesASuccessfulReceipt() {
    PaymentGateway port = new MockPaymentGateway(false);
    assertEquals(
        "MOCK_DISABLED",
        assertThrows(
                BusinessException.class,
                () -> port.pay(new PaymentGateway.Payment("checkout:1", 100, "CNY")))
            .code());
    assertEquals(
        "MOCK_DISABLED",
        assertThrows(
                BusinessException.class,
                () -> port.refund(new PaymentGateway.Refund("refund:1", "checkout:1", 100, "CNY")))
            .code());
  }
}
