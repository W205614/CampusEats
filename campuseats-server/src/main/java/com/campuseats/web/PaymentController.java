package com.campuseats.web;

import com.campuseats.payment.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {
  private final PaymentService payments;
  private final ChannelRefunds refunds;

  public PaymentController(PaymentService payments, ChannelRefunds refunds) {
    this.payments = payments;
    this.refunds = refunds;
  }

  public record Create(@NotBlank String channel, @PositiveOrZero long version) {}

  @GetMapping("/user/payment-capabilities")
  public Map<String, Object> capabilities() {
    return payments.capabilities();
  }

  @PostMapping("/user/checkouts/{id}/payment-intents")
  public PaymentService.View create(
      @PathVariable long id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Create input,
      HttpServletRequest request) {
    return payments.create(id, key, input.channel(), input.version(), request.getRemoteAddr());
  }

  @GetMapping("/user/checkouts/{id}/payment")
  public PaymentService.View status(@PathVariable long id) {
    return payments.status(id);
  }

  @GetMapping("/platform/payment-exceptions")
  public Object exceptions() {
    return payments.exceptions();
  }

  @PostMapping("/platform/payment-exceptions/{id}/retry")
  public void retry(@PathVariable String id) {
    payments.retry(id);
  }

  @PostMapping("/payments/wechat/notify")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void payment(HttpServletRequest req, HttpServletResponse res, @RequestBody String body)
      throws IOException {
    payments.notification(
        req.getHeader("Wechatpay-Serial"),
        req.getHeader("Wechatpay-Timestamp"),
        req.getHeader("Wechatpay-Nonce"),
        req.getHeader("Wechatpay-Signature"),
        body);
    res.setStatus(204);
  }

  @PostMapping("/payments/wechat/refund-notify")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void refund(HttpServletRequest req, HttpServletResponse res, @RequestBody String body)
      throws IOException {
    refunds.notification(
        req.getHeader("Wechatpay-Serial"),
        req.getHeader("Wechatpay-Timestamp"),
        req.getHeader("Wechatpay-Nonce"),
        req.getHeader("Wechatpay-Signature"),
        body);
    res.setStatus(204);
  }
}
