package com.sky.web;

import com.sky.business.AddressService;
import com.sky.api.Commands;
import com.sky.platform.*;
import com.sky.security.Actor;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "campus.legacy-api",
    havingValue = "false",
    matchIfMissing = true)
@RequestMapping("/api/v1")
public class PlatformCheckoutController {
  private final StoreCart cart;
  private final CheckoutService checkout;
  private final AddressService addresses;

  public PlatformCheckoutController(StoreCart cart, CheckoutService checkout, AddressService addresses) {
    this.cart = cart;
    this.checkout = checkout;
    this.addresses = addresses;
  }

  @PostMapping("/user/stores/{storeId}/cart/clear")
  public void clearCart(@PathVariable long storeId, @Valid @RequestBody Commands.Version input) {
    cart.clearCurrent(storeId, input.version());
  }

  @PostMapping("/user/orders/{id}/reorder")
  public Object reorder(@PathVariable long id, @Valid @RequestBody Commands.Version input) {
    return cart.reorder(id, input.version());
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
}
