package com.campuseats.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.campuseats.common.BusinessException;
import com.campuseats.infra.Db;
import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class PaymentLoggingTest {
  @Test
  void channelFailureCannotInjectLogLinesAndKeepsTheStoredDiagnostic() {
    Db db = mock(Db.class);
    WechatPayChannel channel = mock(WechatPayChannel.class);
    String id = "intent\r\nforged", code = "CHANNEL\r\nforged";
    Clock clock = Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"), ZoneOffset.UTC);
    when(db.update(anyString(), any(Object[].class))).thenReturn(1);
    when(db.one("SELECT * FROM payment_intent WHERE id=?", id))
        .thenReturn(
            Map.of(
                "id", id,
                "state", "PENDING",
                "close_requested", false,
                "submitted", true,
                "expires_at", LocalDateTime.now(clock).plusHours(1)));
    when(channel.request(eq("GET"), anyString(), anyMap()))
        .thenThrow(new BusinessException(502, code, "channel failure"));
    var payments =
        new PaymentService(
            db, null, clock, new WechatPaySettings(new org.springframework.mock.env.MockEnvironment()),
            channel, null, null, mock(org.springframework.transaction.PlatformTransactionManager.class));
    Logger logger = (Logger) LoggerFactory.getLogger(PaymentService.class);
    var output = new ListAppender<ILoggingEvent>();
    output.start();
    logger.addAppender(output);
    try {
      payments.drive(id);
      assertEquals(1, output.list.size());
      String message = output.list.getFirst().getFormattedMessage();
      assertFalse(message.contains("\r"));
      assertFalse(message.contains("\n"));
      assertTrue(message.contains("intentId=intent__forged"));
      assertTrue(message.contains("code=CHANNEL__forged"));
      verify(db)
          .update(
              startsWith("UPDATE payment_intent SET state=CASE"),
              eq(code), any(), any(), eq(id), anyString());
    } finally {
      logger.detachAppender(output);
      output.stop();
    }
  }
}
