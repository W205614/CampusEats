package com.sky.web;

import com.sky.payment.ChannelFinance;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ChannelFinanceController {
  public record Merchant(
      @Pattern(regexp = "[0-9]{6,32}") @NotBlank String subMchid,
      boolean enabled,
      @PositiveOrZero long version) {}

  public record Joint(@NotEmpty @Size(max = 200) List<@Positive Long> stores) {}

  public record Day(@NotNull LocalDate businessDate) {}

  public record Resolution(@NotBlank @Size(max = 200) String reason) {}

  private final ChannelFinance finance;

  public ChannelFinanceController(ChannelFinance finance) {
    this.finance = finance;
  }

  @GetMapping("/merchant/stores/{store}/payment-config")
  public ChannelFinance.Merchant merchant(@PathVariable long store) {
    return finance.merchant(store);
  }

  @PutMapping("/platform/stores/{store}/payment-config")
  public void merchant(@PathVariable long store, @Valid @RequestBody Merchant input) {
    finance.merchant(
        store, new ChannelFinance.Merchant(input.subMchid(), input.enabled(), input.version()));
  }

  @PutMapping("/platform/coupons/{id}/participants")
  public void participants(@PathVariable long id, @Valid @RequestBody Joint input) {
    finance.jointCoupon(id, input.stores());
  }

  @GetMapping("/platform/coupons/{id}/participants")
  public Object participants(@PathVariable long id) {
    return finance.jointCoupon(id);
  }

  @PostMapping("/merchant/stores/{store}/channel-reconciliations")
  public Object reconcile(@PathVariable long store, @Valid @RequestBody Day day)
      throws java.io.IOException {
    return finance.reconcile(store, day.businessDate());
  }

  @GetMapping("/merchant/stores/{store}/channel-reconciliations")
  public Object statements(@PathVariable long store) {
    return finance.statements(store);
  }

  @GetMapping("/merchant/stores/{store}/channel-differences")
  public Object differences(@PathVariable long store) {
    return finance.differences(store);
  }

  @PostMapping("/platform/stores/{store}/channel-differences/{id}/review")
  public void review(
      @PathVariable long store, @PathVariable long id, @Valid @RequestBody Resolution input) {
    finance.resolve(store, id, input.reason());
  }
}
