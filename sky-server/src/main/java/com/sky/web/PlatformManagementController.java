package com.sky.web;

import com.sky.api.Commands;
import com.sky.platform.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "campus.legacy-api",
    havingValue = "false",
    matchIfMissing = true)
@RequestMapping("/api/v1")
public class PlatformManagementController {
  private final DirectoryService directory;
  private final PlatformScope scope;
  private final MarketingService marketing;
  private final DiscoveryService discovery;
  private final PlatformJobs jobs;
  private final AuditHistory auditHistory;

  public PlatformManagementController(DirectoryService directory, PlatformScope scope, MarketingService marketing, DiscoveryService discovery, PlatformJobs jobs, AuditHistory auditHistory) {
    this.directory = directory;
    this.scope = scope;
    this.marketing = marketing;
    this.discovery = discovery;
    this.jobs = jobs;
    this.auditHistory = auditHistory;
  }

  @GetMapping("/platform/events")
  public Object events(
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute HistoryQuery query) {
    return jobs.events(null, query);
  }

  @GetMapping("/merchant/stores/{storeId}/events")
  public Object storeEvents(
      @PathVariable long storeId,
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute HistoryQuery query) {
    return jobs.events(storeId, query);
  }

  @PostMapping("/merchant/events/{id}/retry")
  public void eventRetry(@PathVariable long id) {
    jobs.retryEvent(id);
  }

  @GetMapping("/platform/campuses")
  public Object adminCampuses() {
    return directory.campuses(true);
  }

  @PostMapping("/platform/campuses")
  public Object campus(@Valid @RequestBody PlatformCommands.Campus input) {
    return Map.of("id", directory.campus(null, input));
  }

  @PutMapping("/platform/campuses/{id}")
  public void campusUpdate(
      @PathVariable long id, @Valid @RequestBody PlatformCommands.Campus input) {
    directory.campus(id, input);
  }

  @PostMapping("/platform/stores")
  public Object store(@Valid @RequestBody PlatformCommands.Store input) {
    return Map.of("id", directory.store(null, input));
  }

  @PutMapping("/merchant/stores/{storeId}")
  public void storeUpdate(
      @PathVariable long storeId, @Valid @RequestBody PlatformCommands.Store input) {
    directory.store(storeId, input);
  }

  @GetMapping("/platform/campuses/{campusId}/buildings")
  public Object adminBuildings(@PathVariable long campusId) {
    return directory.buildings(campusId, true);
  }

  @PostMapping("/platform/campuses/{campusId}/buildings")
  public Object building(@PathVariable long campusId, @Valid @RequestBody Commands.Building input) {
    return Map.of("id", directory.building(campusId, null, input));
  }

  @PutMapping("/platform/campuses/{campusId}/buildings/{id}")
  public void buildingUpdate(
      @PathVariable long campusId,
      @PathVariable long id,
      @Valid @RequestBody Commands.Building input) {
    directory.building(campusId, id, input);
  }

  @GetMapping("/platform/staff")
  public Object staff() {
    return directory.staff(null);
  }

  @GetMapping("/merchant/stores/{storeId}/staff")
  public Object storeStaff(@PathVariable long storeId) {
    return directory.staff(storeId);
  }

  @PostMapping(value = "/platform/staff")
  @io.swagger.v3.oas.annotations.Operation(operationId = "staffSave")
  public Object staffSave(@Valid @RequestBody PlatformCommands.Staff input) {
    return Map.of("id", directory.staff(null, input));
  }

  @PostMapping(value = "/merchant/staff")
  @io.swagger.v3.oas.annotations.Operation(operationId = "staffSave_1")
  public Object merchantStaffSave(@Valid @RequestBody PlatformCommands.Staff input) {
    return Map.of("id", directory.staff(null, input));
  }

  @PutMapping(value = "/platform/staff/{id}")
  @io.swagger.v3.oas.annotations.Operation(operationId = "staffUpdate")
  public void staffUpdate(@PathVariable long id, @Valid @RequestBody PlatformCommands.Staff input) {
    directory.staff(id, input);
  }

  @PutMapping(value = "/merchant/staff/{id}")
  @io.swagger.v3.oas.annotations.Operation(operationId = "staffUpdate_1")
  public void merchantStaffUpdate(@PathVariable long id, @Valid @RequestBody PlatformCommands.Staff input) {
    directory.staff(id, input);
  }

  @GetMapping("/user/coupons")
  public Object platformCoupons() {
    return marketing.coupons(null, false);
  }

  @GetMapping("/user/stores/{storeId}/coupons")
  public Object storeCoupons(@PathVariable long storeId) {
    scope.store(storeId, true);
    return marketing.coupons(storeId, false);
  }

  @PostMapping("/user/coupons/{id}/claim")
  public Object claimCoupon(@PathVariable long id) {
    return Map.of("id", marketing.claim(id));
  }

  @GetMapping("/user/wallet")
  public Object wallet() {
    return marketing.wallet();
  }

  @GetMapping("/platform/coupons")
  public Object adminPlatformCoupons() {
    return marketing.coupons(null, true);
  }

  @GetMapping("/merchant/stores/{storeId}/coupons")
  public Object adminStoreCoupons(@PathVariable long storeId) {
    return marketing.coupons(storeId, true);
  }

  @PostMapping("/platform/coupons")
  public Object platformCoupon(@Valid @RequestBody PlatformCommands.Coupon input) {
    return Map.of("id", marketing.coupon(null, null, input));
  }

  @PostMapping("/merchant/stores/{storeId}/coupons")
  public Object storeCoupon(
      @PathVariable long storeId, @Valid @RequestBody PlatformCommands.Coupon input) {
    return Map.of("id", marketing.coupon(storeId, null, input));
  }

  @PutMapping("/platform/coupons/{id}")
  public void platformCouponUpdate(
      @PathVariable long id, @Valid @RequestBody PlatformCommands.Coupon input) {
    marketing.coupon(null, id, input);
  }

  @PutMapping("/merchant/stores/{storeId}/coupons/{id}")
  public void storeCouponUpdate(
      @PathVariable long storeId,
      @PathVariable long id,
      @Valid @RequestBody PlatformCommands.Coupon input) {
    marketing.coupon(storeId, id, input);
  }

  @PostMapping("/platform/coupons/{id}/stop")
  public void stopPlatformCoupon(@PathVariable long id) {
    marketing.stop(null, id);
  }

  @PostMapping("/merchant/stores/{storeId}/coupons/{id}/stop")
  public void stopStoreCoupon(@PathVariable long storeId, @PathVariable long id) {
    marketing.stop(storeId, id);
  }

  @GetMapping("/merchant/stores/{storeId}/promotions")
  public Object promotions(@PathVariable long storeId) {
    return marketing.promotions(storeId);
  }

  @PostMapping("/merchant/stores/{storeId}/promotions")
  public Object promotion(
      @PathVariable long storeId, @Valid @RequestBody PlatformCommands.Promotion input) {
    return Map.of("id", marketing.promotion(storeId, null, input));
  }

  @PutMapping("/merchant/stores/{storeId}/promotions/{id}")
  public void promotionUpdate(
      @PathVariable long storeId,
      @PathVariable long id,
      @Valid @RequestBody PlatformCommands.Promotion input) {
    marketing.promotion(storeId, id, input);
  }

  @PostMapping("/user/orders/{id}/review")
  public Object review(@PathVariable long id, @Valid @RequestBody PlatformCommands.Review input) {
    return Map.of("id", discovery.review(id, input));
  }

  @GetMapping("/user/stores/{storeId}/reviews")
  public DiscoveryService.PublicReviews reviews(@PathVariable long storeId,
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute ReviewQuery query) {
    return discovery.publicReviews(storeId, query);
  }

  @GetMapping("/merchant/stores/{storeId}/reviews")
  public DiscoveryService.MerchantReviews adminReviews(@PathVariable long storeId,
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute ReviewQuery query) {
    return discovery.merchantReviews(storeId, query);
  }

  @PostMapping("/merchant/reviews/{id}/reply")
  public void reply(@PathVariable long id, @Valid @RequestBody PlatformCommands.Text input) {
    discovery.reply(id, input.text());
  }

  @PostMapping("/platform/reviews/{id}/hide")
  public void hide(@PathVariable long id, @Valid @RequestBody PlatformCommands.Text input) {
    discovery.hide(id, input.text());
  }

  @GetMapping(value = "/platform/reports")
  @io.swagger.v3.oas.annotations.Operation(operationId = "report")
  public Object report(
      @RequestParam(required = false) Long campusId,
      @RequestParam(required = false) Long storeId,
      @RequestParam LocalDate begin,
      @RequestParam LocalDate end) {
    return discovery.report(campusId, storeId, begin, end);
  }

  @GetMapping(value = "/merchant/reports")
  @io.swagger.v3.oas.annotations.Operation(operationId = "report_1")
  public Object merchantReport(
      @RequestParam(required = false) Long campusId,
      @RequestParam(required = false) Long storeId,
      @RequestParam LocalDate begin,
      @RequestParam LocalDate end) {
    return discovery.report(campusId, storeId, begin, end);
  }

  @GetMapping(value = "/platform/reports/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
  @io.swagger.v3.oas.annotations.Operation(operationId = "export")
  public byte[] export(
      @RequestParam(required = false) Long campusId,
      @RequestParam(required = false) Long storeId,
      @RequestParam LocalDate begin,
      @RequestParam LocalDate end) {
    return discovery.export(campusId, storeId, begin, end);
  }

  @GetMapping(value = "/merchant/reports/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
  @io.swagger.v3.oas.annotations.Operation(operationId = "export_1")
  public byte[] merchantExport(
      @RequestParam(required = false) Long campusId,
      @RequestParam(required = false) Long storeId,
      @RequestParam LocalDate begin,
      @RequestParam LocalDate end) {
    return discovery.export(campusId, storeId, begin, end);
  }

  @GetMapping("/platform/audit")
  public List<AuditHistory.Entry> audit(
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute AuditQuery query) {
    return auditHistory.list(null, query);
  }

  @GetMapping("/merchant/stores/{storeId}/audit")
  public List<AuditHistory.Entry> storeAudit(@PathVariable long storeId,
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute AuditQuery query) {
    return auditHistory.list(storeId, query);
  }
}
