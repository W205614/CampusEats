package com.sky.web;

import com.sky.platform.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CollectionController {
  private final CollectionPages service;

  public CollectionController(CollectionPages service) {
    this.service = service;
  }

  @GetMapping("/platform/staff/page")
  public CursorPages.Page<CollectionPages.Staff> staff(@ModelAttribute CursorPages.Request q) {
    return service.staff(null, q);
  }

  @GetMapping("/merchant/stores/{storeId}/staff/page")
  public CursorPages.Page<CollectionPages.Staff> staff(
      @PathVariable long storeId, @ModelAttribute CursorPages.Request q) {
    return service.staff(storeId, q);
  }

  @GetMapping("/user/coupons/page")
  public CursorPages.Page<CollectionPages.Coupon> userCoupons(
      @ModelAttribute CursorPages.Request q) {
    return service.coupons(null, false, q);
  }

  @GetMapping("/user/stores/{storeId}/coupons/page")
  public CursorPages.Page<CollectionPages.Coupon> userCoupons(
      @PathVariable long storeId, @ModelAttribute CursorPages.Request q) {
    return service.coupons(storeId, false, q);
  }

  @GetMapping("/platform/coupons/page")
  public CursorPages.Page<CollectionPages.Coupon> coupons(@ModelAttribute CursorPages.Request q) {
    return service.coupons(null, true, q);
  }

  @GetMapping("/merchant/stores/{storeId}/coupons/page")
  public CursorPages.Page<CollectionPages.Coupon> coupons(
      @PathVariable long storeId, @ModelAttribute CursorPages.Request q) {
    return service.coupons(storeId, true, q);
  }

  @GetMapping("/user/wallet/page")
  public CursorPages.Page<CollectionPages.Wallet> wallet(@ModelAttribute CursorPages.Request q) {
    return service.wallet(q);
  }

  @GetMapping("/merchant/stores/{storeId}/promotions/page")
  public CursorPages.Page<CollectionPages.Promotion> promotions(
      @PathVariable long storeId, @ModelAttribute CursorPages.Request q) {
    return service.promotions(storeId, q);
  }

  @GetMapping("/user/favorites/page")
  public CursorPages.Page<CollectionPages.Favorite> favorites(
      @ModelAttribute CursorPages.Request q) {
    return service.favorites(q);
  }
}
