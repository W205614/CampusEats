package com.sky.business;

import static com.sky.common.BusinessException.require;

import com.sky.api.Commands.*;
import com.sky.common.Hashes;
import com.sky.infra.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class PricingService {
  public record Prepared(
      Quote quote,
      Map<String, Object> address,
      Map<Long, Integer> quantities,
      List<Map<String, Object>> items) {}

  private final Db db;
  private final Json json;
  private final CartService cart;
  private final ShopService shop;
  private final AddressService addresses;
  private final Clock clock;

  public PricingService(
      Db db, Json json, CartService cart, ShopService shop, AddressService addresses, Clock clock) {
    this.db = db;
    this.json = json;
    this.cart = cart;
    this.shop = shop;
    this.addresses = addresses;
    this.clock = clock;
  }

  public Prepared prepare(long user, long address, long version, boolean lock) {
    var settings = shop.settings(lock);
    require(shop.accepting(settings), 409, "SHOP_CLOSED", "当前不在营业时间");
    var delivery = addresses.resolve(user, address, lock);
    var items = cart.items(user);
    require(!items.isEmpty(), 409, "EMPTY_CART", "购物车为空");
    var setIds =
        items.stream()
            .filter(x -> x.get("item_type").equals("SETMEAL"))
            .map(x -> Db.id(x, "item_id"))
            .distinct()
            .sorted()
            .toList();
    var dishIds = new TreeSet<Long>();
    for (var item : items)
      if (item.get("item_type").equals("DISH")) dishIds.add(Db.id(item, "item_id"));
    List<Map<String, Object>> components = new ArrayList<>();
    if (!setIds.isEmpty()) {
      var setRows =
          db.named.queryForList(
              "SELECT * FROM setmeal WHERE id IN (:ids) ORDER BY id" + (lock ? " FOR SHARE" : ""),
              Map.of("ids", setIds));
      require(setRows.size() == setIds.size(), 409, "ITEM_UNAVAILABLE", "套餐已删除");
      components =
          db.named.queryForList(
              "SELECT * FROM setmeal_dish WHERE setmeal_id IN (:ids) ORDER BY dish_id",
              Map.of("ids", setIds));
      for (var component : components) dishIds.add(Db.id(component, "dish_id"));
    }
    if (!dishIds.isEmpty())
      db.named.queryForList(
          "SELECT id FROM dish WHERE id IN (:ids) ORDER BY id" + (lock ? " FOR SHARE" : ""),
          Map.of("ids", dishIds));
    items = cart.items(user, lock);
    TreeMap<Long, Integer> quantities = new TreeMap<>();
    BigDecimal subtotal = BigDecimal.ZERO;
    int count = 0;
    for (var item : items) {
      String type = String.valueOf(item.get("item_type"));
      long id = Db.id(item, "item_id");
      @SuppressWarnings("unchecked")
      var flavor = (Map<String, String>) item.get("flavors");
      cart.validate(type, id, flavor);
      int qty = Db.integer(item, "quantity");
      BigDecimal price = (BigDecimal) item.get("price");
      subtotal = subtotal.add(price.multiply(BigDecimal.valueOf(qty)));
      count += qty;
      if (type.equals("DISH")) quantities.merge(id, qty, Integer::sum);
      else
        for (var c : components)
          if (Db.id(c, "setmeal_id") == id)
            quantities.merge(Db.id(c, "dish_id"), qty * Db.integer(c, "copies"), Integer::sum);
    }
    BigDecimal deliveryFee = (BigDecimal) settings.get("delivery_fee");
    BigDecimal packaging =
        ((BigDecimal) settings.get("packaging_fee")).multiply(BigDecimal.valueOf(count));
    BigDecimal total = subtotal.add(deliveryFee).add(packaging);
    require(total.compareTo(new BigDecimal("99999999.99")) <= 0, 400, "AMOUNT_LIMIT", "订单金额超出范围");
    String businessDate = LocalDate.now(clock).toString();
    String fingerprint =
        json.write(
            List.of(
                user,
                address,
                version,
                delivery.get("building_name"),
                delivery.get("room"),
                delivery.get("consignee"),
                delivery.get("phone"),
                items,
                deliveryFee,
                packaging,
                settings.get("version"),
                businessDate));
    var quote =
        new Quote(
            version,
            ViewFactory.cart(items),
            money(subtotal),
            money(deliveryFee),
            money(packaging),
            money(total),
            Hashes.sha256(fingerprint),
            businessDate);
    return new Prepared(quote, delivery, quantities, items);
  }

  private String money(BigDecimal amount) {
    return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
  }
}
