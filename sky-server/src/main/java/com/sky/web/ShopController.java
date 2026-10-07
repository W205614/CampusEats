package com.sky.web;

import com.sky.api.Commands.*;
import com.sky.api.Views;
import com.sky.business.*;
import com.sky.infra.ViewFactory;
import com.sky.security.Actor;
import jakarta.validation.Valid;
import java.time.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class ShopController {
  private final ShopService shop;
  private final QuotaService quotas;

  public ShopController(ShopService shop, QuotaService quotas) {
    this.shop = shop;
    this.quotas = quotas;
  }

  @GetMapping({"/api/v1/user/shop", "/api/v1/admin/shop"})
  public Views.ShopView shop() {
    return ViewFactory.shop(shop.view());
  }

  @PutMapping("/api/v1/admin/shop")
  public void configure(@Valid @RequestBody Shop input) {
    shop.configure(input);
  }

  @GetMapping("/api/v1/user/buildings")
  public List<Views.BuildingView> buildings() {
    return shop.buildings(false).stream().map(ViewFactory::building).toList();
  }

  @GetMapping("/api/v1/admin/buildings")
  public List<Views.BuildingView> adminBuildings() {
    Actor.admin();
    return shop.buildings(true).stream().map(ViewFactory::building).toList();
  }

  @PostMapping("/api/v1/admin/buildings")
  public Map<String, Object> building(@Valid @RequestBody Building input) {
    return Map.of("id", shop.building(null, input));
  }

  @PutMapping("/api/v1/admin/buildings/{id}")
  public void buildingUpdate(@PathVariable long id, @Valid @RequestBody Building input) {
    shop.building(id, input);
  }

  @GetMapping("/api/v1/admin/quotas")
  public List<Map<String, Object>> quotas(@RequestParam LocalDate date) {
    Actor.admin();
    return quotas.list(date);
  }

  @PutMapping("/api/v1/admin/quotas")
  public void quota(@RequestParam LocalDate date, @Valid @RequestBody Quota input) {
    quotas.configure(date, input);
  }
}
