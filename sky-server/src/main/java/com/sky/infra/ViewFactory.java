package com.sky.infra;

import com.sky.api.Views;
import java.math.BigDecimal;
import java.util.*;

public final class ViewFactory {
  private ViewFactory() {}

  private static Long id(Map<String, Object> r, String k) {
    return r.get(k) == null ? null : Long.valueOf(r.get(k).toString());
  }

  private static Integer number(Map<String, Object> r, String k) {
    return r.get(k) == null ? null : ((Number) r.get(k)).intValue();
  }

  private static String text(Map<String, Object> r, String k) {
    return r.get(k) == null ? null : r.get(k).toString();
  }

  private static BigDecimal money(Map<String, Object> r, String k) {
    return r.get(k) == null
        ? null
        : new BigDecimal(r.get(k).toString()).setScale(2, java.math.RoundingMode.HALF_UP);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> list(Map<String, Object> r, String k) {
    return r.get(k) instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
  }

  public static Views.SessionView session(Map<String, Object> r) {
    return new Views.SessionView(
        text(r, "token"),
        id(r, "id"),
        text(r, "name"),
        Views.Role.valueOf(text(r, "role")),
        Db.bool(r, "mustChangePassword"),
        text(r, "expiresAt"));
  }

  @SuppressWarnings("unchecked")
  public static Views.ShopView shop(Map<String, Object> r) {
    return new Views.ShopView(
        Db.bool(r, "open"),
        Db.bool(r, "accepting"),
        money(r, "delivery_fee"),
        money(r, "packaging_fee"),
        (List<String>) r.get("hours"),
        text(r, "phone"));
  }

  public static List<Views.CartItemView> cart(List<Map<String, Object>> rows) {
    return rows.stream().map(ViewFactory::cartItem).toList();
  }

  @SuppressWarnings("unchecked")
  private static Views.CartItemView cartItem(Map<String, Object> r) {
    return new Views.CartItemView(
        id(r, "id"),
        text(r, "item_type"),
        id(r, "item_id"),
        number(r, "quantity"),
        (Map<String, String>) r.get("flavors"),
        text(r, "name"),
        money(r, "price"),
        text(r, "image"),
        number(r, "status"));
  }

  public static Views.CategoryView category(Map<String, Object> r) {
    return new Views.CategoryView(
        id(r, "id"), text(r, "name"), number(r, "type"), number(r, "sort"), number(r, "status"));
  }

  public static Views.BuildingView building(Map<String, Object> r) {
    return new Views.BuildingView(id(r, "id"), text(r, "name"), Db.bool(r, "enabled"));
  }

  public static Views.AddressView address(Map<String, Object> r) {
    return new Views.AddressView(
        id(r, "id"),
        id(r, "building_id"),
        text(r, "building_name"),
        text(r, "room"),
        text(r, "consignee"),
        text(r, "phone"),
        Db.bool(r, "is_default"),
        Db.bool(r, "enabled"));
  }

  public static Views.EmployeeView employee(Map<String, Object> r) {
    return new Views.EmployeeView(
        id(r, "id"),
        text(r, "name"),
        text(r, "username"),
        text(r, "role"),
        number(r, "status"),
        Db.bool(r, "must_change_password"));
  }

  @SuppressWarnings("unchecked")
  public static Views.ProductView product(Map<String, Object> r) {
    var flavors =
        list(r, "flavors").stream()
            .map(
                f ->
                    new Views.FlavorView(
                        id(f, "id"), text(f, "name"), (List<String>) f.get("values")))
            .toList();
    var components =
        list(r, "components").stream()
            .map(
                c ->
                    new Views.ComponentView(
                        id(c, "dish_id"),
                        number(c, "copies"),
                        text(c, "name"),
                        text(c, "current_name"),
                        number(c, "status")))
            .toList();
    return new Views.ProductView(
        id(r, "id"),
        text(r, "name"),
        id(r, "category_id"),
        money(r, "price"),
        text(r, "image"),
        text(r, "description"),
        r.containsKey("status") ? number(r, "status") : 1,
        number(r, "remaining"),
        text(r, "item_type"),
        flavors,
        components,
        number(r, "default_quota"));
  }

  public static Views.OrderView order(Map<String, Object> r) {
    var details =
        list(r, "details").stream()
            .map(
                d ->
                    new Views.DetailView(
                        id(d, "id"),
                        text(d, "name"),
                        number(d, "number"),
                        money(d, "amount"),
                        text(d, "dish_flavor"),
                        text(d, "image")))
            .toList();
    Views.RefundView refund = null;
    if (r.get("refund") instanceof Map<?, ?> raw) {
      @SuppressWarnings("unchecked")
      var f = (Map<String, Object>) raw;
      refund =
          new Views.RefundView(
              id(f, "id"), text(f, "state"), number(f, "attempts"), text(f, "last_error"));
    }
    return new Views.OrderView(
        id(r, "id"),
        text(r, "number"),
        id(r, "version"),
        number(r, "status"),
        number(r, "pay_status"),
        money(r, "amount"),
        r.get("order_time") == null ? null : Db.time(r.get("order_time")),
        text(r, "consignee"),
        text(r, "phone"),
        text(r, "address"),
        text(r, "building_name"),
        text(r, "room"),
        text(r, "remark"),
        id(r, "courier_id"),
        Db.bool(r, "delivery_overdue"),
        details,
        refund,
        text(r, "snapshot_source"),
        text(r, "request_id"),
        money(r, "delivery_fee"),
        money(r, "packaging_fee"));
  }
}
