package com.sky.platform;

import static com.sky.common.BusinessException.require;

import com.sky.business.ImageStorage;
import com.sky.infra.Db;
import com.sky.security.Actor;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class EvidenceService {
  private final Path root;
  private final Db db;
  private final PlatformScope scope;
  private final ImageStorage storage;
  private final org.springframework.transaction.support.TransactionTemplate tx;

  public EvidenceService(
      @Value("${campus.uploads}") String root,
      Db db,
      PlatformScope scope,
      ImageStorage storage,
      org.springframework.transaction.PlatformTransactionManager manager) {
    this.root = Path.of(root).toAbsolutePath().normalize().resolve(".private");
    this.db = db;
    this.scope = scope;
    this.storage = storage;
    this.tx = new org.springframework.transaction.support.TransactionTemplate(manager);
    this.tx.setPropagationBehavior(
        org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public String upload(long order, MultipartFile file) throws java.io.IOException {
    long user = Actor.user().id();
    scope.order(order, false);
    try (var image = storage.stage(file)) {
      var published = new java.util.concurrent.atomic.AtomicReference<String>();
      try {
        String id =
            tx.execute(
                status -> {
                  scope.order(order, true);
                  require(
                      db.count(
                              "SELECT COUNT(*) FROM evidence WHERE order_id=? AND user_id=? AND"
                                  + " request_id IS NULL",
                              order,
                              user)
                          < 20,
                      400,
                      "EVIDENCE_LIMIT",
                      "待绑定图片数量已达上限");
                  try {
                    String name = image.publish(true);
                    published.set(name);
                    db.update(
                        "INSERT INTO evidence(id,user_id,order_id,path,created_at)"
                            + " VALUES(?,?,?,?,CURRENT_TIMESTAMP)",
                        name,
                        user,
                        order,
                        name + ".png");
                    return name;
                  } catch (java.io.IOException ex) {
                    throw new com.sky.common.BusinessException(
                        503, "UPLOAD_UNAVAILABLE", "图片存储暂不可用");
                  }
                });
        image.accept();
        return id;
      } catch (RuntimeException ex) {
        // A lost COMMIT acknowledgement must not remove a possibly committed image.
        if (published.get() != null) {
          try {
            if (db.count("SELECT COUNT(*) FROM evidence WHERE id=?", published.get()) > 0)
              image.accept();
          } catch (RuntimeException unknownOutcome) {
            image.accept();
            org.slf4j.LoggerFactory.getLogger(getClass())
                .warn("Evidence commit outcome unknown; retained file for operational review");
          }
        }
        throw ex;
      }
    }
  }

  public byte[] read(String id) throws java.io.IOException {
    require(id.matches("[a-f0-9-]{36}"), 404, "NOT_FOUND", "图片不存在");
    var e = db.one("SELECT * FROM evidence WHERE id=?", id);
    require(e != null, 404, "NOT_FOUND", "图片不存在");
    Actor a = Actor.current();
    require(!a.role().equals("DELIVERER"), 403, "FORBIDDEN", "配送员无权查看售后举证");
    scope.order(Db.id(e, "order_id"), false);
    return Files.readAllBytes(root.resolve(e.get("path").toString()));
  }
}
