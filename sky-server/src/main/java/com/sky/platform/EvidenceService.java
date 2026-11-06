package com.sky.platform;

import static com.sky.common.BusinessException.require;

import com.sky.business.ImageStorage;
import com.sky.infra.Db;
import com.sky.security.Actor;
import com.sky.storage.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class EvidenceService {
  private final LocalBlobStore local;
  private final StoredImages objects;
  private final Db db;
  private final PlatformScope scope;
  private final ImageStorage storage;
  private final org.springframework.transaction.support.TransactionTemplate tx;
  private final EvidenceReadLimits reads;

  public EvidenceService(
      @Value("${campus.uploads}") String root,
      Db db,
      PlatformScope scope,
      ImageStorage storage,
      org.springframework.transaction.PlatformTransactionManager manager,
      EvidenceReadLimits reads) {
    this(root, db, scope, storage, manager, reads, null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public EvidenceService(
      @Value("${campus.uploads}") String root,
      Db db,
      PlatformScope scope,
      ImageStorage storage,
      org.springframework.transaction.PlatformTransactionManager manager,
      EvidenceReadLimits reads,
      StoredImages objects) {
    this.local = new LocalBlobStore(Path.of(root));
    this.objects = objects;
    this.db = db;
    this.scope = scope;
    this.storage = storage;
    this.reads = reads;
    this.tx = new org.springframework.transaction.support.TransactionTemplate(manager);
    this.tx.setPropagationBehavior(
        org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public EvidenceService(
      String root,
      Db db,
      PlatformScope scope,
      ImageStorage storage,
      org.springframework.transaction.PlatformTransactionManager manager) {
    this(
        root,
        db,
        scope,
        storage,
        manager,
        new EvidenceReadLimits(java.time.Clock.systemUTC(), 4, 2, 120));
  }

  public String upload(long order, MultipartFile file) throws java.io.IOException {
    long user = Actor.user().id();
    scope.order(order, false);
    requirePendingCapacity(order, user);
    try (var image = storage.stage(file)) {
      // Object I/O precedes the business transaction; the authorization is rechecked under lock.
      String published = image.publish(true);
      try {
        String id =
            tx.execute(
                status -> {
                  scope.order(order, true);
                  requirePendingCapacity(order, user);
                  db.update(
                      "INSERT INTO evidence(id,user_id,order_id,path,created_at)"
                          + " VALUES(?,?,?,?,CURRENT_TIMESTAMP)",
                      published,
                      user,
                      order,
                      published + ".png");
                  return published;
                });
        image.accept();
        return id;
      } catch (RuntimeException ex) {
        // A lost COMMIT acknowledgement must not remove a possibly committed image.
        if (published != null) {
          try {
            if (db.count("SELECT COUNT(*) FROM evidence WHERE id=?", published) > 0) image.accept();
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

  private void requirePendingCapacity(long order, long user) {
    require(
        db.count(
                "SELECT COUNT(*) FROM evidence WHERE order_id=? AND user_id=? AND request_id IS"
                    + " NULL",
                order,
                user)
            < 20,
        400,
        "EVIDENCE_LIMIT",
        "待绑定图片数量已达上限");
  }

  public Read open(String id) throws IOException {
    require(id.matches("[a-f0-9-]{36}"), 404, "NOT_FOUND", "图片不存在");
    var e = db.one("SELECT * FROM evidence WHERE id=?", id);
    require(e != null, 404, "NOT_FOUND", "图片不存在");
    Actor a = Actor.current();
    require(!a.role().equals("DELIVERER"), 403, "FORBIDDEN", "配送员无权查看售后举证");
    scope.order(Db.id(e, "order_id"), false);
    require((id + ".png").equals(e.get("path")), 404, "NOT_FOUND", "图片不存在");
    var lease = reads.acquire(a.type() + ":" + a.id());
    BlobStore.Content content = null;
    try {
      content = objects == null ? local.open("", id + ".png", null, true) : objects.open(id, true);
      long size = content.size();
      require(size > 0 && size <= 5L * 1024 * 1024, 503, "EVIDENCE_UNAVAILABLE", "举证图片暂不可用");
      return new Read(content, size, lease);
    } catch (Exception ex) {
      if (content != null)
        try {
          content.close();
        } catch (IOException ignored) {
        }
      lease.close();
      if (ex instanceof NoSuchFileException)
        throw new com.sky.common.BusinessException(404, "NOT_FOUND", "图片不存在");
      if (ex instanceof IOException)
        throw new com.sky.common.BusinessException(503, "EVIDENCE_UNAVAILABLE", "举证图片暂不可用");
      throw ex;
    }
  }

  public static final class Read implements AutoCloseable {
    private final BlobStore.Content content;
    private final long size;
    private final EvidenceReadLimits.Lease lease;

    private Read(BlobStore.Content content, long size, EvidenceReadLimits.Lease lease) {
      this.content = content;
      this.size = size;
      this.lease = lease;
    }

    public long size() {
      return size;
    }

    /** Synchronous, bounded-memory transfer on the authenticated request thread. */
    public void transferTo(OutputStream output) throws IOException {
      content.transferTo(output);
    }

    @Override
    public void close() throws IOException {
      try {
        content.close();
      } finally {
        lease.close();
      }
    }
  }
}
