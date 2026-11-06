package com.sky.payment;

import com.sky.infra.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentJobs {
  private final PaymentService payments;
  private final Db db;
  private final RuntimeOperations operations;
  private final boolean enabled;
  private final AtomicLong unknown = new AtomicLong();

  public PaymentJobs(
      PaymentService payments,
      Db db,
      RuntimeOperations operations,
      MeterRegistry registry,
      @Value("${campus.tasks-enabled:true}") boolean enabled) {
    this.payments = payments;
    this.db = db;
    this.operations = operations;
    this.enabled = enabled;
    registry.gauge("campus.payment.unknown", unknown);
  }

  @Scheduled(fixedDelay = 5000)
  public void run() {
    if (enabled)
      operations.run(
          "channelPayments",
          round -> {
            payments.poll(round::withinBudget);
            unknown.set(
                db.count(
                    "SELECT COUNT(*) FROM payment_intent WHERE state IN ('UNKNOWN','REVIEW')"));
          });
  }
}
