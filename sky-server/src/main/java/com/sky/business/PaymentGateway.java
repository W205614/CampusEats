package com.sky.business;

/** Immediate simulated operations only. Real channels require a separate asynchronous workflow. */
public interface PaymentGateway {
  record Payment(String businessKey, long amountCents, String currency) {}

  record Refund(String businessKey, String paymentKey, long amountCents, String currency) {}

  record Receipt(String channel, String reference) {}

  Receipt pay(Payment request);

  Receipt refund(Refund request);
}
