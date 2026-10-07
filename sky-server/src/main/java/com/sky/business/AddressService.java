package com.sky.business;

import static com.sky.common.BusinessException.require;

import com.sky.api.Commands.Address;
import com.sky.infra.Db;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddressService {
  private final Db db;

  public AddressService(Db db) {
    this.db = db;
  }

  public List<Map<String, Object>> list(long user) {
    var rows =
        db.rows(
            "SELECT a.id,a.building_id,b.name"
                + " building_name,a.room,a.consignee,a.phone,(a.id=u.default_address_id)"
                + " is_default,b.enabled FROM address_book a JOIN user u ON u.id=a.user_id LEFT"
                + " JOIN campus_building b ON b.id=a.building_id WHERE a.user_id=? ORDER BY a.id"
                + " DESC",
            user);
    for (var row : rows) row.put("is_default", Db.bool(row, "is_default"));
    return rows;
  }

  public Map<String, Object> resolve(long user, long id, boolean lock) {
    var row =
        db.one(
            "SELECT a.*,b.name building_name,b.enabled FROM address_book a JOIN campus_building b"
                + " ON b.id=a.building_id WHERE a.id=? AND a.user_id=?"
                + (lock ? " FOR SHARE" : ""),
            id,
            user);
    require(row != null, 404, "ADDRESS_NOT_FOUND", "请选择有效的校园地址");
    require(Db.bool(row, "enabled"), 409, "OUTSIDE_DELIVERY", "该楼栋暂不配送");
    return row;
  }

  @Transactional
  public long save(long user, Long id, Address input) {
    var building = db.one("SELECT * FROM campus_building WHERE id=? FOR SHARE", input.buildingId());
    require(
        building != null && Db.bool(building, "enabled"), 400, "OUTSIDE_DELIVERY", "请选择配送范围内的楼栋");
    if (id == null) {
      long created =
          db.insert(
              "INSERT INTO address_book(user_id,building_id,room,consignee,phone,detail,is_default)"
                  + " VALUES(?,?,?,?,?,?,0)",
              user,
              input.buildingId(),
              input.room(),
              input.consignee(),
              input.phone(),
              input.room());
      db.update(
          "UPDATE user SET default_address_id=? WHERE id=? AND default_address_id IS NULL",
          created,
          user);
      return created;
    }
    require(
        db.update(
                "UPDATE address_book SET building_id=?,room=?,consignee=?,phone=?,detail=? WHERE"
                    + " id=? AND user_id=?",
                input.buildingId(),
                input.room(),
                input.consignee(),
                input.phone(),
                input.room(),
                id,
                user)
            == 1,
        404,
        "NOT_FOUND",
        "地址不存在");
    return id;
  }

  @Transactional
  public void remove(long user, long id) {
    db.one("SELECT id FROM user WHERE id=? FOR UPDATE", user);
    require(
        db.update("DELETE FROM address_book WHERE id=? AND user_id=?", id, user) == 1,
        404,
        "NOT_FOUND",
        "地址不存在");
    db.update(
        "UPDATE user SET default_address_id=NULL WHERE id=? AND default_address_id=?", user, id);
  }

  @Transactional
  public void makeDefault(long user, long id) {
    db.one("SELECT id FROM user WHERE id=? FOR UPDATE", user);
    resolve(user, id, false);
    db.update("UPDATE user SET default_address_id=? WHERE id=?", id, user);
  }
}
