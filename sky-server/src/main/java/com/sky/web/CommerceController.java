package com.sky.web;

import com.sky.api.Commands.*;
import com.sky.api.Views;
import com.sky.business.*;
import com.sky.infra.ViewFactory;
import com.sky.security.Actor;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class CommerceController {
  private final CartService cart;
  private final AddressService addresses;
  private final OrderCommandService commands;
  private final OrderQueryService orders;

  public CommerceController(
      CartService cart,
      AddressService addresses,
      OrderCommandService commands,
      OrderQueryService orders) {
    this.cart = cart;
    this.addresses = addresses;
    this.commands = commands;
    this.orders = orders;
  }

  @GetMapping("/api/v1/user/cart")
  public Cart cart() {
    return cart.view(Actor.user().id());
  }

  @PostMapping("/api/v1/user/cart/items")
  public Cart cartChange(@Valid @RequestBody CartChange input) {
    return cart.change(Actor.user().id(), input);
  }

  @PostMapping("/api/v1/user/cart/clear")
  public Cart clear(@Valid @RequestBody Version input) {
    return cart.clear(Actor.user().id(), input.version());
  }

  @GetMapping("/api/v1/user/addresses")
  public List<Views.AddressView> addresses() {
    return addresses.list(Actor.user().id()).stream().map(ViewFactory::address).toList();
  }

  @PostMapping("/api/v1/user/addresses")
  public Map<String, Object> address(@Valid @RequestBody Address input) {
    return Map.of("id", addresses.save(Actor.user().id(), null, input));
  }

  @PutMapping("/api/v1/user/addresses/{id}")
  public Map<String, Object> addressUpdate(
      @PathVariable long id, @Valid @RequestBody Address input) {
    return Map.of("id", addresses.save(Actor.user().id(), id, input));
  }

  @DeleteMapping("/api/v1/user/addresses/{id}")
  public void addressDelete(@PathVariable long id) {
    addresses.remove(Actor.user().id(), id);
  }

  @PostMapping("/api/v1/user/addresses/{id}/default")
  public void addressDefault(@PathVariable long id) {
    addresses.makeDefault(Actor.user().id(), id);
  }

  @PostMapping("/api/v1/user/checkout/preview")
  public Quote preview(@Valid @RequestBody Preview input) {
    return commands.preview(Actor.user().id(), input);
  }

  @PostMapping("/api/v1/user/orders")
  public Views.OrderView submit(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Submit input) {
    var row = commands.submit(Actor.user().id(), key, input);
    return ViewFactory.order(orders.detail(Actor.user(), com.sky.infra.Db.id(row, "id")));
  }

  @GetMapping("/api/v1/user/orders/by-request/{key}")
  public Views.OrderView byKey(@PathVariable String key) {
    return ViewFactory.order(orders.byKey(Actor.user().id(), key));
  }

  @GetMapping({"/api/v1/user/orders", "/api/v1/admin/orders"})
  public Page<Views.OrderView> orders(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) Integer status,
      @RequestParam(required = false) String query) {
    var result = orders.page(Actor.current(), page, size, status, query);
    return new Page<>(result.total(), result.records().stream().map(ViewFactory::order).toList());
  }

  @GetMapping({"/api/v1/user/orders/{id}", "/api/v1/admin/orders/{id}"})
  public Views.OrderView detail(@PathVariable long id) {
    return ViewFactory.order(orders.detail(Actor.current(), id));
  }

  @PostMapping("/api/v1/user/orders/{id}/pay")
  public Views.OrderView pay(@PathVariable long id, @Valid @RequestBody Version input) {
    commands.pay(Actor.user().id(), id, input.version());
    return ViewFactory.order(orders.detail(Actor.user(), id));
  }

  @PostMapping("/api/v1/user/orders/{id}/cancel")
  public Views.OrderView cancel(@PathVariable long id, @Valid @RequestBody OrderAction input) {
    commands.action(Actor.user(), id, "cancel", input);
    return ViewFactory.order(orders.detail(Actor.user(), id));
  }

  @PostMapping("/api/v1/admin/orders/{id}/{action}")
  public Views.OrderView action(
      @PathVariable long id, @PathVariable String action, @Valid @RequestBody OrderAction input) {
    commands.action(Actor.employee(), id, action, input);
    return ViewFactory.order(orders.detail(Actor.employee(), id));
  }

  @PostMapping("/api/v1/user/orders/{id}/reorder")
  public Map<String, Object> reorder(@PathVariable long id, @Valid @RequestBody Version input) {
    return cart.reorder(Actor.user().id(), id, input.version());
  }

  @PostMapping("/api/v1/user/orders/{id}/reminder")
  public void reminder(@PathVariable long id) {
    commands.reminder(Actor.user().id(), id);
  }

  @GetMapping("/api/v1/admin/audit")
  public List<Map<String, Object>> audit(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
    return orders.audit(page, size);
  }
}
