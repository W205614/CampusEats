package com.sky.business;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sky.common.BusinessException;
import com.sky.infra.Db;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class PaymentGatewayTest {
  private final Db db = mock(Db.class);
  private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);

  private MockPaymentGateway gateway(boolean enabled) {
    when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
    return new MockPaymentGateway(enabled, db, manager);
  }

  private void durableRows() {
    var rows = new HashMap<String, Map<String, Object>>();
    when(db.one(anyString(), any(Object[].class))).thenAnswer(call -> {
      Object[] args = (Object[]) call.getRawArguments()[1];
      return rows.get(args[0]);
    });
    when(db.update(anyString(), any(Object[].class))).thenAnswer(call -> {
      Object[] args = (Object[]) call.getRawArguments()[1];
      rows.putIfAbsent((String) args[0], Map.of("payment_key", args[1], "amount_cents", args[2],
          "currency", args[3], "reference", args[4]));
      return 1;
    });
  }

  @Test
  void refundIdentityIncludesAmountAndOriginalPaymentAndSurvivesARetry() {
    durableRows();
    PaymentGateway port = gateway(true);
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
    assertEquals(receipt, gateway(true).refund(request), "A new gateway instance uses the stored decision");
    verify(manager, atLeastOnce()).getTransaction(argThat(definition ->
        definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
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
    PaymentGateway port = gateway(false);
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
    verifyNoInteractions(db);
  }
}
