package com.sky.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class Commands {
  private Commands() {}

  public record Login(
      @NotBlank @Size(max = 32) String username, @NotBlank @Size(max = 128) String password) {}

  public record DemoLogin(@Min(1) @Max(3) int account) {}

  public record WxLogin(@NotBlank @Size(max = 256) String code) {}

  public record Password(
      @NotBlank @Size(max = 128) String oldPassword,
      @Size(min = 12, max = 128) @NotBlank String password) {}

  public record CartChange(
      @NotNull @PositiveOrZero Long cartVersion,
      @NotBlank @Pattern(regexp = "DISH|SETMEAL") String itemType,
      @NotNull @Positive Long itemId,
      Map<String, String> flavors,
      @Min(-50) @Max(50) int delta) {}

  public record Version(@NotNull @PositiveOrZero Long version) {}

  public record Preview(
      @NotNull @Positive Long addressId, @NotNull @PositiveOrZero Long cartVersion) {}

  public record Submit(
      @NotNull @Positive Long addressId,
      @NotNull @PositiveOrZero Long cartVersion,
      @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String quoteHash,
      @Size(max = 100) String remark) {}

  public record Address(
      @NotNull @Positive Long buildingId,
      @NotBlank @Size(max = 32) String room,
      @NotBlank @Size(max = 32) String consignee,
      @NotBlank @Pattern(regexp = "1[3-9][0-9]{9}") String phone) {}

  public record OrderAction(
      @NotNull @PositiveOrZero Long version,
      @Size(max = 200) String reason,
      @Positive Long courierId) {}

  public record Building(@NotBlank @Size(max = 80) String name, boolean enabled) {}

  public record Shop(
      boolean open,
      @DecimalMin("0.00") @DecimalMax("100.00") @NotNull BigDecimal deliveryFee,
      @DecimalMin("0.00") @DecimalMax("100.00") @NotNull BigDecimal packagingFee,
      @NotEmpty @Size(max = 8)
          List<
                  @Pattern(
                      regexp =
                          "([01][0-9]|2[0-3]):[0-5][0-9]-(?:([01][0-9]|2[0-3]):[0-5][0-9]|24:00)")
                  String>
              hours,
      @Size(max = 20) String phone) {}

  public record Quota(
      @NotNull @Positive Long dishId, @Min(0) @Max(100000) int total, boolean defaultQuota) {}

  public record Employee(
      @NotBlank @Size(max = 32) String username,
      @NotBlank @Size(max = 32) String name,
      @Pattern(regexp = "ADMIN|OPERATOR|DELIVERER") @NotBlank String role,
      boolean enabled,
      @Size(min = 12, max = 128) String password) {}

  public record Category(
      @NotBlank @Size(max = 32) String name,
      @Min(1) @Max(2) int type,
      @Min(0) int sort,
      boolean enabled) {}

  public record Flavor(
      @NotBlank @Size(max = 32) String name,
      @NotEmpty List<@NotBlank @Size(max = 32) String> values) {}

  public record Component(@NotNull @Positive Long dishId, @Min(1) @Max(50) int copies) {}

  public record Product(
      @NotBlank @Size(max = 32) String name,
      @NotNull @Positive Long categoryId,
      @DecimalMin("0.01") @DecimalMax("99999.99") @NotNull BigDecimal price,
      boolean enabled,
      @Size(max = 255) String description,
      @Size(max = 255) String image,
      List<@jakarta.validation.Valid Flavor> flavors,
      List<@jakarta.validation.Valid Component> components) {}

  public record ApiResponse<T>(String code, String message, T data, String requestId) {}

  public record Page<T>(long total, List<T> records) {}

  public record Cart(long cartVersion, List<Views.CartItemView> items) {}

  public record Quote(
      long cartVersion,
      List<Views.CartItemView> items,
      String subtotal,
      String deliveryFee,
      String packagingFee,
      String total,
      String quoteHash,
      String businessDate) {}
}
