package com.sky.web;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;

/** Explicit public contracts; presence preserves role-specific omissions and legacy nulls. */
public final class PlatformViews {
  private PlatformViews() {}

  public interface Projected {
    @JsonIgnore
    @Schema(hidden = true)
    Set<String> presentFields();
  }

  public record StorePageResponse(
      List<StoreResponse> records, String nextCursor, boolean hasMore) {}

  public record SearchPageResponse(
      List<ProductResponse> records, String nextCursor, boolean hasMore) {}

  public record StoreResponse(
      String id,
      String campusId,
      String name,
      Boolean enabled,
      Boolean open,
      String deliveryFee,
      String packagingFee,
      List<String> hours,
      String phone,
      String version,
      String campusName,
      Boolean accepting,
      @JsonIgnore @Schema(hidden = true) Set<String> presentFields)
      implements Projected {}

  public record FlavorResponse(
      String id,
      String name,
      List<String> values,
      @JsonIgnore @Schema(hidden = true) Set<String> presentFields)
      implements Projected {}

  public record ComponentResponse(
      String id,
      String setmealId,
      String dishId,
      String name,
      String price,
      Integer copies,
      String currentName,
      Integer status,
      @JsonIgnore @Schema(hidden = true) Set<String> presentFields)
      implements Projected {}

  public record PromotionResponse(
      String id,
      String priceCents,
      String endsAt,
      @JsonIgnore @Schema(hidden = true) Set<String> presentFields)
      implements Projected {}

  public record ProductResponse(
      String id,
      String name,
      String categoryId,
      String price,
      String image,
      String description,
      Integer status,
      String createTime,
      String updateTime,
      String createUser,
      String updateUser,
      Integer defaultQuota,
      String storeId,
      Integer categoryStatus,
      @Schema(allowableValues = {"DISH", "SETMEAL"}) String itemType,
      String storeName,
      List<FlavorResponse> flavors,
      List<ComponentResponse> components,
      Integer remaining,
      PromotionResponse promotion,
      @JsonIgnore @Schema(hidden = true) Set<String> presentFields)
      implements Projected {}

  public record OrderDetailResponse(
      String id,
      String name,
      String image,
      String orderId,
      String dishId,
      String setmealId,
      String dishFlavor,
      Integer number,
      String amount,
      String lineTotalCents,
      Integer refundedQuantity,
      Integer reservedQuantity,
      @JsonIgnore @Schema(hidden = true) Set<String> presentFields)
      implements Projected {}

  public record DeliveryResponse(
      String id,
      String orderId,
      String campusId,
      String courierId,
      String state,
      String version,
      @JsonIgnore @Schema(hidden = true) Set<String> presentFields)
      implements Projected {}

  public record OrderAftersaleResponse(
      String id,
      String orderId,
      String userId,
      String storeId,
      String kind,
      String state,
      String reason,
      String decision,
      String amountCents,
      String feeCents,
      Boolean appealed,
      String createdAt,
      String version,
      @JsonIgnore @Schema(hidden = true) Set<String> presentFields)
      implements Projected {}

  public record RefundResponse(
      String id,
      String orderId,
      String aftersaleId,
      String storeId,
      String amountCents,
      String state,
      Integer attempts,
      String nextAttemptAt,
      String leaseUntil,
      String lastError,
      String createdAt,
      String succeededAt,
      String successTimeSource,
      @JsonIgnore @Schema(hidden = true) Set<String> presentFields)
      implements Projected {}

  public record OrderResponse(
      String id,
      String number,
      Integer status,
      String userId,
      String addressBookId,
      String orderTime,
      String checkoutTime,
      Integer payMethod,
      Integer payStatus,
      String amount,
      String remark,
      String phone,
      String address,
      String userName,
      String consignee,
      String cancelReason,
      String rejectionReason,
      String cancelTime,
      String estimatedDeliveryTime,
      Boolean deliveryStatus,
      String deliveryTime,
      Integer packAmount,
      Integer tablewareNumber,
      Boolean tablewareStatus,
      String requestId,
      String version,
      String businessDate,
      String snapshotSource,
      String buildingName,
      String room,
      String deliveryFee,
      String packagingFee,
      String payExpiresAt,
      String deliveryStartedAt,
      String courierId,
      Boolean deliveryOverdue,
      String storeId,
      String campusId,
      String checkoutId,
      String fulfillment,
      Boolean ready,
      String completedAt,
      String paidCents,
      String refundedCents,
      String reservedRefundCents,
      String shopDiscountCents,
      String platformDiscountCents,
      String pickupCode,
      Integer pickupAttempts,
      String storeName,
      String campusName,
      List<OrderDetailResponse> details,
      DeliveryResponse delivery,
      List<OrderAftersaleResponse> aftersales,
      List<RefundResponse> refunds,
      Boolean reviewed,
      @JsonIgnore @Schema(hidden = true) Set<String> presentFields)
      implements Projected {}

  public record MerchantOrderResponse(
      String id,
      String number,
      Integer status,
      String userId,
      String addressBookId,
      String orderTime,
      String checkoutTime,
      Integer payMethod,
      Integer payStatus,
      String amount,
      String remark,
      String phone,
      String address,
      String userName,
      String consignee,
      String cancelReason,
      String rejectionReason,
      String cancelTime,
      String estimatedDeliveryTime,
      Boolean deliveryStatus,
      String deliveryTime,
      Integer packAmount,
      Integer tablewareNumber,
      Boolean tablewareStatus,
      String requestId,
      String version,
      String businessDate,
      String snapshotSource,
      String buildingName,
      String room,
      String deliveryFee,
      String packagingFee,
      String payExpiresAt,
      String deliveryStartedAt,
      String courierId,
      Boolean deliveryOverdue,
      String storeId,
      String campusId,
      String checkoutId,
      String fulfillment,
      Boolean ready,
      String completedAt,
      String paidCents,
      String refundedCents,
      String reservedRefundCents,
      String shopDiscountCents,
      String platformDiscountCents,
      String pickupCode,
      Integer pickupAttempts,
      String storeName,
      String campusName,
      List<OrderDetailResponse> details,
      DeliveryResponse delivery,
      List<OrderAftersaleResponse> aftersales,
      List<RefundResponse> refunds,
      Boolean reviewed,
      @JsonIgnore @Schema(hidden = true) Set<String> presentFields)
      implements Projected {}

  public record CourierDetailResponse(
      String id,
      String name,
      Integer number,
      String image,
      String dishFlavor,
      @JsonIgnore @Schema(hidden = true) Set<String> presentFields)
      implements Projected {}

  public record CourierOrderResponse(
      String id,
      String number,
      Integer status,
      String version,
      String storeId,
      String campusId,
      String storeName,
      String campusName,
      String fulfillment,
      Boolean ready,
      String orderTime,
      String buildingName,
      String phone,
      String address,
      String room,
      String consignee,
      String deliveryStartedAt,
      String deliveryTime,
      String completedAt,
      Boolean deliveryOverdue,
      String courierId,
      DeliveryResponse delivery,
      List<CourierDetailResponse> details,
      @JsonIgnore @Schema(hidden = true) Set<String> presentFields)
      implements Projected {}
}
