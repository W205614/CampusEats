package com.sky.platform;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class PlatformCommands {
  private PlatformCommands() {}

  public record Campus(@NotBlank @Size(max = 80) String name, boolean enabled) {}

  public record Store(
      @Positive long campusId,
      @NotBlank @Size(max = 80) String name,
      boolean enabled,
      boolean open,
      @Min(0) @Max(10000) long deliveryFeeCents,
      @Min(0) @Max(10000) long packagingFeeCents,
      @NotEmpty @Size(max = 8)
          List<
                  @Pattern(
                      regexp =
                          "([01][0-9]|2[0-3]):[0-5][0-9]-(?:([01][0-9]|2[0-3]):[0-5][0-9]|24:00)")
                  String>
              hours,
      @Size(max = 20) String phone) {}

  public record Staff(
      @NotBlank @Size(max = 32) String username,
      @NotBlank @Size(max = 32) String name,
      @NotBlank @Pattern(regexp = "PLATFORM_ADMIN|SHOP_MANAGER|OPERATOR|DELIVERER") String role,
      Long storeId,
      Long campusId,
      boolean enabled,
      @Size(min = 12, max = 128) String password) {}

  public record CartChange(
      @PositiveOrZero long cartVersion,
      @Pattern(regexp = "DISH|SETMEAL") String itemType,
      @Positive long itemId,
      Map<String, String> flavors,
      @Min(-50) @Max(50) int delta) {}

  public record Selection(
      @Positive long storeId,
      @PositiveOrZero long cartVersion,
      @NotNull @Pattern(regexp = "DELIVERY|PICKUP") String fulfillment,
      Long addressId,
      Long couponId) {}

  public record Checkout(
      @NotEmpty @Size(max = 20) List<@Valid Selection> stores,
      Long platformCouponId,
      @Size(max = 64) String quoteHash,
      @Size(max = 100) String remark) {}

  public record Action(
      @PositiveOrZero long version,
      @Size(max = 200) String reason,
      Long courierId,
      @Size(max = 12) String code) {}

  public record Promotion(
      @NotNull @Pattern(regexp = "DISH|SETMEAL") String itemType,
      @Positive long itemId,
      @Min(1) @Max(9999999) long priceCents,
      @NotNull LocalDateTime startsAt,
      @NotNull LocalDateTime endsAt,
      boolean enabled) {}

  public record Coupon(
      @NotBlank @Size(max = 80) String name,
      @NotNull @Pattern(regexp = "REDUCTION|DISCOUNT") String kind,
      @Min(0) @Max(999999999) long thresholdCents,
      @Positive long value,
      @Positive Long capCents,
      @Min(1) @Max(1000000) int total,
      @Min(1) @Max(100) int perUser,
      @NotNull LocalDateTime startsAt,
      @NotNull LocalDateTime endsAt,
      boolean enabled) {}

  public record RefundLine(@Positive long detailId, @Min(1) @Max(50) int quantity) {}

  public record Aftersale(
      @NotNull @Pattern(regexp = "FULL|PARTIAL|CANCEL") String kind,
      @NotBlank @Size(max = 200) String reason,
      @Size(max = 50) List<@Valid RefundLine> items,
      @Size(max = 3) List<String> evidenceIds) {}

  public record Decision(
      @PositiveOrZero long version, boolean approve, @NotBlank @Size(max = 200) String reason) {}

  public record Review(@Min(1) @Max(5) int rating, @NotBlank @Size(max = 1000) String content) {}

  public record Text(@NotBlank @Size(max = 1000) String text) {}
}
