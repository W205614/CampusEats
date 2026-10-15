package com.sky.platform;

import com.sky.infra.Db;
import com.sky.security.AuthService;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo")
@Order(20)
public class PlatformDemoSeed implements ApplicationRunner {
  private final Db db;
  private final AuthService auth;
  private final String password;

  public PlatformDemoSeed(
      Db db, AuthService auth, @Value("${campus.demo-admin-password}") String password) {
    this.db = db;
    this.auth = auth;
    this.password = password;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    db.update("INSERT IGNORE INTO campus(id,name) VALUES(2,'东校区')");
    db.update(
        "INSERT IGNORE INTO store(id,campus_id,name) VALUES(2,1,'校园轻食'),(3,2,'东区食堂'),(4,2,'东区面馆')");
    for (long campus : List.of(1L, 2L))
      for (String name : List.of("一号宿舍楼", "二号宿舍楼", "图书馆"))
        db.update("INSERT IGNORE INTO campus_building(campus_id,name) VALUES(?,?)", campus, name);
    for (long store : List.of(2L, 3L, 4L)) {
      if (db.count("SELECT COUNT(*) FROM category WHERE store_id=?", store) == 0) {
        long category =
            db.insert(
                "INSERT INTO category(store_id,name,type,sort,status) VALUES(?,'校园热餐',1,1,1)",
                store);
        db.insert(
            "INSERT INTO dish(store_id,name,category_id,price,status,default_quota,image)"
                + " VALUES(?,'番茄鸡蛋饭',?,12,1,100,'/demo-images/dish.svg')",
            store,
            category);
      }
    }
    for (long store : List.of(1L, 2L, 3L, 4L)) {
      long campus = store <= 2 ? 1 : 2;
      seed("manager" + store, "店长" + store, "SHOP_MANAGER", store, campus);
      seed("operator" + store, "运营员" + store, "OPERATOR", store, campus);
    }
    for (long campus : List.of(1L, 2L)) {
      seed("courier" + campus, "配送员" + campus, "DELIVERER", null, campus);
      seed("courier" + campus + "b", "配送员" + campus + "B", "DELIVERER", null, campus);
    }
  }

  private void seed(String username, String name, String role, Long store, long campus) {
    if (db.count("SELECT COUNT(*) FROM employee WHERE username=?", username) == 0)
      db.insert(
          "INSERT INTO"
              + " employee(name,username,password,role,store_id,campus_id,status,phone,sex,id_number,must_change_password)"
              + " VALUES(?,?,?,?,?,?,1,'','','',true)",
          name,
          username,
          auth.encodePassword(password),
          role,
          store,
          campus);
  }
}
