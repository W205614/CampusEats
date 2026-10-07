package com.sky.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class Views {
  private Views() {}

  public enum Role {
    ADMIN,
    OPERATOR,
    DELIVERER,
    USER
  }

  public record SessionView(
      String token,
      Long id,
      String name,
      Role role,
      boolean mustChangePassword,
      String expiresAt) {}

  public record ShopView(
      boolean open,
      boolean accepting,
      BigDecimal deliveryFee,
      BigDecimal packagingFee,
      List<String> hours,
      String phone) {}

  public record FlavorView(Long id, String name, List<String> values) {}

  public record ComponentView(
      Long dishId, Integer copies, String name, String currentName, Integer status) {}

  public record ProductView(
      Long id,
      String name,
      Long categoryId,
      BigDecimal price,
      String image,
      String description,
      Integer status,
      Integer remaining,
      String itemType,
      List<FlavorView> flavors,
      List<ComponentView> components,
      Integer defaultQuota) {}

  public record CategoryView(Long id, String name, Integer type, Integer sort, Integer status) {}

  public record BuildingView(Long id, String name, boolean enabled) {}

  public record AddressView(
      Long id,
      Long buildingId,
      String buildingName,
      String room,
      String consignee,
      String phone,
      boolean isDefault,
      boolean enabled) {}

  public record EmployeeView(
      Long id,
      String name,
      String username,
      String role,
      Integer status,
      boolean mustChangePassword) {}

  public record CartItemView(
      Long id,
      String itemType,
      Long itemId,
      Integer quantity,
      Map<String, String> flavors,
      String name,
      BigDecimal price,
      String image,
      Integer status) {}

  public record DetailView(
      Long id, String name, Integer number, BigDecimal amount, String dishFlavor, String image) {}

  public record RefundView(Long id, String state, Integer attempts, String lastError) {}

  public record OrderView(
      Long id,
      String number,
      Long version,
      Integer status,
      Integer payStatus,
      BigDecimal amount,
      LocalDateTime orderTime,
      String consignee,
      String phone,
      String address,
      String buildingName,
      String room,
      String remark,
      Long courierId,
      boolean deliveryOverdue,
      List<DetailView> details,
      RefundView refund,
      String snapshotSource,
      String requestId,
      BigDecimal deliveryFee,
      BigDecimal packagingFee) {}
}
