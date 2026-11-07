package com.campuseats.storage;

import static com.campuseats.common.BusinessException.require;

import com.campuseats.infra.Db;
import org.springframework.boot.*;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionStorageGuard implements ApplicationRunner {
  private final Db db;
  private final Environment environment;

  public ProductionStorageGuard(Db db, Environment environment) {
    this.db = db;
    this.environment = environment;
  }

  @Override
  public void run(ApplicationArguments args) {
    require(
        "cos".equals(environment.getProperty("campus.storage.backend")),
        503,
        "PRODUCTION_STORAGE",
        "生产环境必须使用共享COS存储");
    long legacy =
        db.count("SELECT COUNT(*) FROM stored_image WHERE backend='LOCAL' AND state='READY'");
    legacy +=
        db.count(
            "SELECT COUNT(*) FROM evidence e LEFT JOIN stored_image s ON s.id=e.id AND"
                + " s.backend='COS' AND s.state='READY' AND s.visibility='PRIVATE' WHERE s.id IS"
                + " NULL");
    for (String table : java.util.List.of("dish", "setmeal"))
      legacy +=
          db.count(
              "SELECT COUNT(*) FROM "
                  + table
                  + " p LEFT JOIN stored_image s ON"
                  + " s.id=SUBSTRING_INDEX(SUBSTRING_INDEX(p.image,'/',-1),'.',1) AND"
                  + " s.backend='COS' AND s.state='READY' AND s.visibility='PUBLIC' WHERE p.image"
                  + " LIKE '/uploads/%' AND s.id IS NULL");
    require(legacy == 0, 503, "PRODUCTION_LOCAL_ASSETS", "生产数据库仍引用本地图片，请使用独立生产库或完成文件迁移后再启动多实例");
  }
}
