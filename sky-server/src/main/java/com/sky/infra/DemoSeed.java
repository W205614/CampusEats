package com.sky.infra;

import com.sky.security.AuthService;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo")
public class DemoSeed implements ApplicationRunner {
  private final Db db;
  private final AuthService auth;
  private final String password;
  private final Clock clock;

  public DemoSeed(
      Db db,
      AuthService auth,
      Clock clock,
      @Value("${campus.demo-admin-password}") String password) {
    this.db = db;
    this.auth = auth;
    this.password = password;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (db.count("SELECT COUNT(*) FROM employee") == 0) {
      com.sky.common.BusinessException.require(
          password.length() >= 12, 503, "DEMO_PASSWORD", "首次演示部署需要DEMO_ADMIN_PASSWORD");
      db.update(
          "INSERT INTO"
              + " employee(id,name,username,password,role,status,phone,sex,id_number,must_change_password,create_time,update_time)"
              + " VALUES(1,'管理员','admin',?,'ADMIN',1,'','','',false,?,?)",
          auth.encodePassword(password),
          LocalDateTime.now(clock),
          LocalDateTime.now(clock));
    }
    if (db.count("SELECT COUNT(*) FROM category") == 0) {
      db.update(
          "INSERT INTO category(id,type,name,sort,status,create_time,update_time)"
              + " VALUES(1,1,'校园热餐',1,1,?,?),(2,2,'实惠套餐',2,1,?,?)",
          LocalDateTime.now(clock),
          LocalDateTime.now(clock),
          LocalDateTime.now(clock),
          LocalDateTime.now(clock));
      db.update(
          "INSERT INTO dish(id,name,category_id,price,image,status,default_quota)"
              + " VALUES(1,'宫保鸡丁饭',1,16.00,'/demo-images/dish.svg',1,100),(2,'番茄鸡蛋饭',1,12.00,'/demo-images/dish.svg',1,100),(3,'紫菜蛋花汤',1,4.00,'/demo-images/dish.svg',1,100)");
      db.update(
          "INSERT INTO dish_flavor(dish_id,name,value) VALUES(1,'辣度','[\"不辣\",\"微辣\",\"中辣\"]')");
      db.update(
          "INSERT INTO setmeal(id,name,category_id,price,status,image)"
              + " VALUES(1,'鸡丁饭加汤',2,18.00,1,'/demo-images/dish.svg')");
      db.update(
          "INSERT INTO setmeal_dish(setmeal_id,dish_id,name,price,copies)"
              + " VALUES(1,1,'宫保鸡丁饭',16.00,1),(1,3,'紫菜蛋花汤',4.00,1)");
    }
    if (db.count("SELECT COUNT(*) FROM campus_building") == 0)
      db.update("INSERT INTO campus_building(name) VALUES('一号宿舍楼'),('二号宿舍楼'),('图书馆')");
  }
}
