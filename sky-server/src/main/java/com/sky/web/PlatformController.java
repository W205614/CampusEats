package com.sky.web;

import com.sky.api.Commands;
import com.sky.business.AddressService;
import com.sky.business.UploadService;
import com.sky.platform.*;
import com.sky.security.Actor;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "campus.legacy-api",
    havingValue = "false",
    matchIfMissing = true)
@RequestMapping("/api/v1")
public class PlatformController {
  @PostMapping("/user/stores/{storeId}/cart/clear")
  public void clearCart(@PathVariable long storeId, @Valid @RequestBody Commands.Version input) {
    cart.clearCurrent(storeId, input.version());
  }

  @PostMapping("/user/orders/{id}/reorder")
  public Object reorder(@PathVariable long id, @Valid @RequestBody Commands.Version input) {
    return cart.reorder(id, input.version());
  }

  @PostMapping("/user/orders/{id}/reminder")
  public void reminder(@PathVariable long id) {
    fulfillment.reminder(id);
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

  private final DirectoryService directory;
  private final PlatformScope scope;
  private final StoreCatalog catalog;
  private final StoreCart cart;
  private final CheckoutService checkout;
  private final MarketingService marketing;
  private final FulfillmentService fulfillment;
  private final AftersaleService aftersale;
  private final EvidenceService evidence;
  private final DiscoveryService discovery;
  private final PlatformJobs jobs;
  private final AddressService addresses;
  private final UploadService uploads;
  private final DiscoveryPages discoveryPages;

  public PlatformController(
      DirectoryService directory,
      PlatformScope scope,
      StoreCatalog catalog,
      StoreCart cart,
      CheckoutService checkout,
      MarketingService marketing,
      FulfillmentService fulfillment,
      AftersaleService aftersale,
      EvidenceService evidence,
      DiscoveryService discovery,
      PlatformJobs jobs,
      AddressService addresses,
      UploadService uploads,
      DiscoveryPages discoveryPages) {
    this.directory = directory;
    this.scope = scope;
    this.catalog = catalog;
    this.cart = cart;
    this.checkout = checkout;
    this.marketing = marketing;
    this.fulfillment = fulfillment;
    this.aftersale = aftersale;
    this.evidence = evidence;
    this.discovery = discovery;
    this.jobs = jobs;
    this.addresses = addresses;
    this.uploads = uploads;
    this.discoveryPages = discoveryPages;
  }

  @GetMapping("/user/campuses")
  public Object campuses() {
    return directory.campuses(false);
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

  @GetMapping("/user/stores")
  public Object stores(
      @RequestParam(required = false) Long campusId, @RequestParam(required = false) String query) {
    return directory.stores(campusId, query, false);
  }

  @GetMapping("/user/stores/page")
  public DiscoveryPages.Page storePage(
      @RequestParam(required = false) Long campusId,
      @RequestParam(required = false) String query,
      @RequestParam(required = false) Boolean open,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return discoveryPages.stores(campusId, query, open, cursor, limit, false);
  }

  @GetMapping("/merchant/stores/page")
  public DiscoveryPages.Page merchantStorePage(
      @RequestParam(required = false) Long campusId,
      @RequestParam(required = false) String query,
      @RequestParam(required = false) Boolean open,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return discoveryPages.stores(campusId, query, open, cursor, limit, true);
  }

  @GetMapping("/user/stores/{id}")
  public Object storeDetail(@PathVariable long id) {
    return discoveryPages.store(id);
  }

  @GetMapping("/user/search/page")
  public DiscoveryPages.Page searchPage(
      @RequestParam long campusId,
      @RequestParam(required = false) String query,
      @RequestParam(required = false) Long categoryId,
      @RequestParam(required = false) Boolean open,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return discoveryPages.search(campusId, query, categoryId, open, cursor, limit);
  }

  @GetMapping("/merchant/stores")
  public Object adminStores() {
    return directory.stores(null, null, true);
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

  @GetMapping("/user/campuses/{campusId}/buildings")
  public Object buildings(@PathVariable long campusId) {
    return directory.buildings(campusId, false);
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

  @GetMapping("/merchant/stores/{storeId}/couriers")
  public Object couriers(@PathVariable long storeId) {
    scope.merchant(storeId, false);
    return scope.db.rows(
        "SELECT id,name FROM employee WHERE role='DELIVERER' AND status=1 AND campus_id=? ORDER BY"
            + " id",
        scope.store(storeId, false).get("campus_id"));
  }

  @PostMapping({"/platform/staff", "/merchant/staff"})
  public Object staffSave(@Valid @RequestBody PlatformCommands.Staff input) {
    return Map.of("id", directory.staff(null, input));
  }

  @PutMapping({"/platform/staff/{id}", "/merchant/staff/{id}"})
  public void staffUpdate(@PathVariable long id, @Valid @RequestBody PlatformCommands.Staff input) {
    directory.staff(id, input);
  }

  @GetMapping("/user/stores/{storeId}/categories")
  public Object categories(@PathVariable long storeId) {
    return catalog.categories(storeId, false);
  }

  @GetMapping("/merchant/stores/{storeId}/categories")
  public Object adminCategories(@PathVariable long storeId) {
    return catalog.categories(storeId, true);
  }

  @PostMapping("/merchant/stores/{storeId}/categories")
  public Object category(@PathVariable long storeId, @Valid @RequestBody Commands.Category input) {
    return Map.of("id", catalog.category(storeId, null, input));
  }

  @PutMapping("/merchant/stores/{storeId}/categories/{id}")
  public void categoryUpdate(
      @PathVariable long storeId,
      @PathVariable long id,
      @Valid @RequestBody Commands.Category input) {
    catalog.category(storeId, id, input);
  }

  @GetMapping("/user/stores/{storeId}/products")
  public Object products(
      @PathVariable long storeId,
      @RequestParam(required = false) String query,
      @RequestParam(required = false) Long categoryId) {
    return catalog.products(storeId, false, query, categoryId);
  }

  @GetMapping("/merchant/stores/{storeId}/products")
  public Object adminProducts(@PathVariable long storeId) {
    return catalog.products(storeId, true, null, null);
  }

  @PostMapping("/merchant/stores/{storeId}/products/{type}")
  public Object product(
      @PathVariable long storeId,
      @PathVariable String type,
      @Valid @RequestBody Commands.Product input) {
    return Map.of("id", catalog.save(storeId, type, null, input));
  }

  @PutMapping("/merchant/stores/{storeId}/products/{type}/{id}")
  public void productUpdate(
      @PathVariable long storeId,
      @PathVariable String type,
      @PathVariable long id,
      @Valid @RequestBody Commands.Product input) {
    catalog.save(storeId, type, id, input);
  }

  @GetMapping("/merchant/stores/{storeId}/quotas")
  public Object quotas(@PathVariable long storeId, @RequestParam LocalDate date) {
    return catalog.quotas(storeId, date);
  }

  @PutMapping("/merchant/stores/{storeId}/quotas")
  public void quota(
      @PathVariable long storeId,
      @RequestParam LocalDate date,
      @Valid @RequestBody Commands.Quota input) {
    catalog.quota(storeId, date, input);
  }

  @PostMapping("/merchant/stores/{storeId}/uploads")
  public Object productUpload(@PathVariable long storeId, @RequestParam MultipartFile file) {
    scope.merchant(storeId, true);
    return Map.of("url", uploads.uploadProduct(file));
  }

  @GetMapping("/user/stores/{storeId}/cart")
  public Object cart(@PathVariable long storeId) {
    return cart.view(storeId);
  }

  @GetMapping("/user/carts")
  public Object carts() {
    return cart.all();
  }

  @PostMapping("/user/stores/{storeId}/cart/items")
  public Object cartChange(
      @PathVariable long storeId, @Valid @RequestBody PlatformCommands.CartChange input) {
    return cart.change(storeId, input);
  }

  @GetMapping("/user/addresses")
  public Object addresses() {
    return addresses.list(Actor.user().id());
  }

  @PostMapping("/user/addresses")
  public Object address(@Valid @RequestBody Commands.Address input) {
    return Map.of("id", addresses.save(Actor.user().id(), null, input));
  }

  @PutMapping("/user/addresses/{id}")
  public Object addressUpdate(@PathVariable long id, @Valid @RequestBody Commands.Address input) {
    return Map.of("id", addresses.save(Actor.user().id(), id, input));
  }

  @DeleteMapping("/user/addresses/{id}")
  public void addressDelete(@PathVariable long id) {
    addresses.remove(Actor.user().id(), id);
  }

  @PostMapping("/user/addresses/{id}/default")
  public void addressDefault(@PathVariable long id) {
    addresses.makeDefault(Actor.user().id(), id);
  }

  @PostMapping("/user/checkouts/preview")
  public CheckoutService.Quote preview(@Valid @RequestBody PlatformCommands.Checkout input) {
    return checkout.preview(input);
  }

  @PostMapping("/user/checkouts")
  public Object submit(
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody PlatformCommands.Checkout input) {
    return checkout.submit(key, input);
  }

  @GetMapping("/user/checkouts")
  public Object checkouts(@RequestParam(required = false) Long beforeId, @RequestParam(required = false) Integer limit) {
    return checkout.list(beforeId, limit);
  }

  @GetMapping("/user/checkouts/by-request/{key}")
  public Object checkoutKey(@PathVariable String key) {
    return checkout.byKey(key);
  }

  @GetMapping("/user/checkouts/{id}")
  public Object checkout(@PathVariable long id) {
    return checkout.detail(id);
  }

  @PostMapping("/user/checkouts/{id}/pay")
  public Object pay(@PathVariable long id, @Valid @RequestBody Commands.Version input) {
    return checkout.pay(id, input.version());
  }

  @PostMapping("/user/checkouts/{id}/cancel")
  public void checkoutCancel(@PathVariable long id, @Valid @RequestBody Commands.Version input) {
    checkout.cancel(id, input.version());
  }

  @GetMapping("/user/orders")
  public Object userOrders(@RequestParam(required = false) Long beforeId, @RequestParam(required = false) Integer limit) {
    return fulfillment.orders(null, beforeId, limit);
  }

  @GetMapping("/merchant/stores/{storeId}/orders")
  public Object storeOrders(
      @PathVariable long storeId, @RequestParam(required = false) Long beforeId, @RequestParam(required = false) Integer limit) {
    return fulfillment.orders(storeId, beforeId, limit);
  }

  @GetMapping("/courier/orders")
  public Object courierOrders(@RequestParam(required = false) Long beforeId, @RequestParam(required = false) Integer limit) {
    return fulfillment.orders(null, beforeId, limit);
  }

  @GetMapping({"/user/orders/{id}", "/merchant/orders/{id}", "/courier/orders/{id}"})
  public Object order(@PathVariable long id) {
    return fulfillment.detail(id);
  }

  @PostMapping({
    "/user/orders/{id}/actions/{action}",
    "/merchant/orders/{id}/actions/{action}",
    "/courier/orders/{id}/actions/{action}"
  })
  public Object orderAction(
      @PathVariable long id,
      @PathVariable String action,
      @Valid @RequestBody PlatformCommands.Action input) {
    return action.equals("verify")
        ? fulfillment.verify(id, input)
        : fulfillment.action(id, action, input);
  }

  @PostMapping("/platform/orders/{id}/reset-pickup")
  public void resetPickup(@PathVariable long id) {
    fulfillment.resetCode(id);
  }

  @PostMapping("/platform/orders/{id}/unassign")
  public Object unassign(@PathVariable long id, @Valid @RequestBody PlatformCommands.Action input) {
    return fulfillment.action(id, "unassign", input);
  }

  @PostMapping("/platform/orders/{id}/reassign")
  public Object reassign(
      @PathVariable long id, @Valid @RequestBody PlatformCommands.Reassign input) {
    return fulfillment.reassign(id, input);
  }

  @PostMapping("/platform/orders/{id}/exception-cancel")
  public Object exceptionCancel(
      @PathVariable long id, @Valid @RequestBody PlatformCommands.ExceptionCancel input) {
    return fulfillment.exceptionCancel(id, input);
  }

  @GetMapping("/courier/pool")
  public Object pool() {
    return fulfillment.pool();
  }

  @PostMapping("/courier/tasks/{id}/claim")
  public void claimTask(@PathVariable long id, @Valid @RequestBody Commands.Version input) {
    fulfillment.claim(id, input.version());
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

  @PostMapping("/user/orders/{id}/aftersales")
  public Object apply(@PathVariable long id, @Valid @RequestBody PlatformCommands.Aftersale input) {
    return Map.of("id", aftersale.apply(id, input));
  }

  @GetMapping("/user/aftersales")
  public Object userAftersales(
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute HistoryQuery query) {
    return aftersale.list(null, false, query);
  }

  @GetMapping("/merchant/stores/{storeId}/aftersales")
  public Object merchantAftersales(
      @PathVariable long storeId,
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute HistoryQuery query) {
    return aftersale.list(storeId, false, query);
  }

  @GetMapping("/platform/aftersales")
  public Object platformAftersales(
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute HistoryQuery query) {
    return aftersale.list(null, true, query);
  }

  @PostMapping("/merchant/aftersales/{id}/decision")
  public void decide(@PathVariable long id, @Valid @RequestBody PlatformCommands.Decision input) {
    aftersale.decide(id, input, false);
  }

  @PostMapping("/platform/aftersales/{id}/decision")
  public void rule(@PathVariable long id, @Valid @RequestBody PlatformCommands.Decision input) {
    aftersale.decide(id, input, true);
  }

  @PostMapping("/user/aftersales/{id}/appeal")
  public void appeal(@PathVariable long id, @Valid @RequestBody Commands.Version input) {
    aftersale.appeal(id, input.version());
  }

  @PostMapping("/user/aftersales/{id}/withdraw")
  public void withdraw(@PathVariable long id, @Valid @RequestBody Commands.Version input) {
    aftersale.withdraw(id, input.version());
  }

  @PostMapping("/user/orders/{id}/evidence")
  public Object uploadEvidence(@PathVariable long id, @RequestParam MultipartFile file)
      throws java.io.IOException {
    return Map.of("id", evidence.upload(id, file));
  }

  @GetMapping(value = "/evidence/{id}", produces = MediaType.IMAGE_PNG_VALUE)
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
      description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(
          mediaType = MediaType.IMAGE_PNG_VALUE,
          schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "binary")))
  public void evidence(@PathVariable String id, jakarta.servlet.http.HttpServletResponse response)
      throws java.io.IOException {
    try (var read = evidence.open(id)) {
      response.setContentType(MediaType.IMAGE_PNG_VALUE);
      response.setContentLengthLong(read.size());
      response.setHeader("Cache-Control", "no-store");
      response.setHeader("X-Content-Type-Options", "nosniff");
      read.transferTo(response.getOutputStream());
    }
  }

  @GetMapping("/platform/refunds")
  public Object refunds(
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute HistoryQuery query) {
    return jobs.refunds(null, query);
  }

  @GetMapping("/merchant/stores/{storeId}/refunds")
  public Object storeRefunds(
      @PathVariable long storeId,
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute HistoryQuery query) {
    return jobs.refunds(storeId, query);
  }

  @PostMapping("/merchant/refunds/{id}/retry")
  public void refundRetry(@PathVariable long id) {
    jobs.retry(id);
  }

  @GetMapping("/user/search")
  public Object search(
      @RequestParam long campusId,
      @RequestParam(required = false) String query,
      @RequestParam(required = false) Long categoryId,
      @RequestParam(required = false) Boolean open) {
    return discovery.search(campusId, query, categoryId, open);
  }

  @GetMapping("/user/favorites")
  public Object favorites() {
    return discovery.favorites();
  }

  @PostMapping("/user/stores/{storeId}/favorites/{type}/{id}")
  public void favorite(
      @PathVariable long storeId, @PathVariable String type, @PathVariable long id) {
    discovery.favorite(storeId, type, id, true);
  }

  @DeleteMapping("/user/stores/{storeId}/favorites/{type}/{id}")
  public void unfavorite(
      @PathVariable long storeId, @PathVariable String type, @PathVariable long id) {
    discovery.favorite(storeId, type, id, false);
  }

  @PostMapping("/user/orders/{id}/review")
  public Object review(@PathVariable long id, @Valid @RequestBody PlatformCommands.Review input) {
    return Map.of("id", discovery.review(id, input));
  }

  @GetMapping("/user/stores/{storeId}/reviews")
  public Object reviews(@PathVariable long storeId) {
    return discovery.reviews(storeId, false);
  }

  @GetMapping("/merchant/stores/{storeId}/reviews")
  public Object adminReviews(@PathVariable long storeId) {
    return discovery.reviews(storeId, true);
  }

  @PostMapping("/merchant/reviews/{id}/reply")
  public void reply(@PathVariable long id, @Valid @RequestBody PlatformCommands.Text input) {
    discovery.reply(id, input.text());
  }

  @PostMapping("/platform/reviews/{id}/hide")
  public void hide(@PathVariable long id, @Valid @RequestBody PlatformCommands.Text input) {
    discovery.hide(id, input.text());
  }

  @GetMapping({"/platform/reports", "/merchant/reports"})
  public Object report(
      @RequestParam(required = false) Long campusId,
      @RequestParam(required = false) Long storeId,
      @RequestParam LocalDate begin,
      @RequestParam LocalDate end) {
    return discovery.report(campusId, storeId, begin, end);
  }

  @GetMapping(
      value = {"/platform/reports/export", "/merchant/reports/export"},
      produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
  public byte[] export(
      @RequestParam(required = false) Long campusId,
      @RequestParam(required = false) Long storeId,
      @RequestParam LocalDate begin,
      @RequestParam LocalDate end) {
    return discovery.export(campusId, storeId, begin, end);
  }

  @GetMapping("/platform/audit")
  public Object audit() {
    scope.platform();
    return scope.db.rows("SELECT * FROM audit_log ORDER BY id DESC LIMIT 200");
  }

  @GetMapping("/merchant/stores/{storeId}/audit")
  public Object storeAudit(@PathVariable long storeId) {
    scope.merchant(storeId, true);
    return scope.db.rows(
        "SELECT * FROM audit_log WHERE store_id=? ORDER BY id DESC LIMIT 200", storeId);
  }
}
