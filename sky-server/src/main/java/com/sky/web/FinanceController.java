package com.sky.web;

import com.sky.finance.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;

@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "campus.legacy-api",
    havingValue = "false",
    matchIfMissing = true)
@RequestMapping("/api/v1")
public class FinanceController {
  public record Generate(@Positive long storeId, @NotNull LocalDate businessDate) {}

  private final FinanceService service;

  public FinanceController(FinanceService service) {
    this.service = service;
  }

  @GetMapping("/platform/ledger/page")
  public FinanceViews.Page<FinanceViews.FinanceLedgerLine> ledger(
      @RequestParam(required = false) Long storeId,
      @RequestParam(required = false) LocalDate begin,
      @RequestParam(required = false) LocalDate end,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return service.ledger(storeId, begin, end, type, cursor, limit);
  }

  @GetMapping("/merchant/stores/{storeId}/ledger/page")
  public FinanceViews.Page<FinanceViews.FinanceLedgerLine> merchantLedger(
      @PathVariable long storeId,
      @RequestParam(required = false) LocalDate begin,
      @RequestParam(required = false) LocalDate end,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return service.ledger(storeId, begin, end, type, cursor, limit);
  }

  @GetMapping("/platform/settlements/preview")
  public FinanceViews.FinancePreview preview(
      @RequestParam long storeId, @RequestParam(required = false) LocalDate date) {
    return service.preview(storeId, date);
  }

  @GetMapping("/merchant/stores/{storeId}/settlements/preview")
  public FinanceViews.FinancePreview merchantPreview(
      @PathVariable long storeId, @RequestParam(required = false) LocalDate date) {
    return service.preview(storeId, date);
  }

  @PostMapping("/platform/settlements")
  public FinanceViews.FinanceSettlement generate(@Valid @RequestBody Generate input) {
    return service.create(input.storeId(), input.businessDate());
  }

  @GetMapping("/platform/settlements/page")
  public FinanceViews.Page<FinanceViews.FinanceSettlement> statements(
      @RequestParam(required = false) Long storeId,
      @RequestParam(required = false) LocalDate begin,
      @RequestParam(required = false) LocalDate end,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return service.settlements(storeId, begin, end, cursor, limit);
  }

  @GetMapping("/merchant/stores/{storeId}/settlements/page")
  public FinanceViews.Page<FinanceViews.FinanceSettlement> merchantStatements(
      @PathVariable long storeId,
      @RequestParam(required = false) LocalDate begin,
      @RequestParam(required = false) LocalDate end,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return service.settlements(storeId, begin, end, cursor, limit);
  }

  @GetMapping("/platform/settlements/{id}")
  public FinanceViews.FinanceSettlement statement(@PathVariable long id) {
    return service.detail(id, null);
  }

  @GetMapping("/merchant/stores/{storeId}/settlements/{id}")
  public FinanceViews.FinanceSettlement merchantStatement(
      @PathVariable long storeId, @PathVariable long id) {
    return service.detail(id, storeId);
  }

  @GetMapping("/platform/settlements/{id}/entries/page")
  public FinanceViews.Page<FinanceViews.FinanceLedgerLine> entries(
      @PathVariable long id,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return service.settlementEntries(id, null, cursor, limit);
  }

  @GetMapping("/merchant/stores/{storeId}/settlements/{id}/entries/page")
  public FinanceViews.Page<FinanceViews.FinanceLedgerLine> merchantEntries(
      @PathVariable long storeId,
      @PathVariable long id,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return service.settlementEntries(id, storeId, cursor, limit);
  }

  @GetMapping("/platform/finance/reconciliation")
  public FinanceViews.FinanceReconciliation reconcile(
      @RequestParam(required = false) Long storeId,
      @RequestParam LocalDate begin,
      @RequestParam LocalDate end) {
    return service.reconcile(storeId, begin, end);
  }
}
