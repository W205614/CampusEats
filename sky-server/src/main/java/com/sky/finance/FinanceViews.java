package com.sky.finance;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.*;
import java.util.List;

/** Simulated cashflow views. No payout or external channel is represented by these records. */
public final class FinanceViews {
  private FinanceViews() {}

  @Schema(requiredProperties = {"records", "hasMore"})
  public record Page<T>(
      List<T> records, @Schema(nullable = true) String nextCursor, boolean hasMore) {}

  @Schema(
      requiredProperties = {
        "id",
        "sourceType",
        "sourceId",
        "orderId",
        "storeId",
        "storeName",
        "amountCents",
        "occurredAt"
      })
  public record FinanceLedgerLine(
      long id,
      String sourceType,
      long sourceId,
      long orderId,
      long storeId,
      String storeName,
      long amountCents,
      LocalDateTime occurredAt) {}

  @Schema(
      requiredProperties = {
        "storeId",
        "businessDate",
        "enabledAt",
        "periodStart",
        "periodEnd",
        "openingNetCents",
        "paidCents",
        "refundedCents",
        "netCents",
        "entryCount",
        "commissionCents",
        "channelFeeCents",
        "carryForward",
        "partialDay",
        "generatedAt"
      })
  public record FinancePreview(
      long storeId,
      LocalDate businessDate,
      LocalDateTime enabledAt,
      LocalDateTime periodStart,
      LocalDateTime periodEnd,
      long openingNetCents,
      long paidCents,
      long refundedCents,
      long netCents,
      long entryCount,
      long commissionCents,
      long channelFeeCents,
      boolean carryForward,
      boolean partialDay,
      LocalDateTime generatedAt) {}

  @Schema(
      requiredProperties = {
        "id",
        "storeId",
        "storeName",
        "businessDate",
        "periodStart",
        "periodEnd",
        "paidCents",
        "refundedCents",
        "netCents",
        "entryCount",
        "carryForward",
        "createdAt"
      })
  public record FinanceSettlement(
      long id,
      long storeId,
      String storeName,
      LocalDate businessDate,
      LocalDateTime periodStart,
      LocalDateTime periodEnd,
      long paidCents,
      long refundedCents,
      long netCents,
      long entryCount,
      boolean carryForward,
      LocalDateTime createdAt) {}

  @Schema(
      requiredProperties = {
        "orderId",
        "storeId",
        "orderPaidCents",
        "orderRefundedCents",
        "ledgerPaidCents",
        "ledgerRefundedCents"
      })
  public record FinanceDifference(
      long orderId,
      long storeId,
      long orderPaidCents,
      long orderRefundedCents,
      long ledgerPaidCents,
      long ledgerRefundedCents) {}

  @Schema(
      requiredProperties = {
        "begin",
        "end",
        "periodPaidCents",
        "periodRefundedCents",
        "openingNetCents",
        "currentOrderNetCents",
        "ledgerNetCents",
        "differenceCount",
        "differences",
        "hasMoreDifferences",
        "generatedAt"
      })
  public record FinanceReconciliation(
      Long storeId,
      LocalDate begin,
      LocalDate end,
      long periodPaidCents,
      long periodRefundedCents,
      long openingNetCents,
      long currentOrderNetCents,
      long ledgerNetCents,
      long differenceCount,
      List<FinanceDifference> differences,
      boolean hasMoreDifferences,
      LocalDateTime generatedAt) {}
}
