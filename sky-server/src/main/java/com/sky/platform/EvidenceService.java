package com.sky.platform;

import static com.sky.common.BusinessException.require;

import com.sky.business.ImageStorage;
import com.sky.infra.Db;
import com.sky.security.Actor;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
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
  private final EvidenceReadLimits reads;

  @org.springframework.beans.factory.annotation.Autowired
  public EvidenceService(
      @Value("${campus.uploads}") String root,
      Db db,
      PlatformScope scope,
      ImageStorage storage,
      org.springframework.transaction.PlatformTransactionManager manager,
      EvidenceReadLimits reads) {
    this.root = Path.of(root).toAbsolutePath().normalize().resolve(".private");
    this.db = db;
    this.scope = scope;
    this.storage = storage;
    this.reads = reads;
    this.tx = new org.springframework.transaction.support.TransactionTemplate(manager);
    this.tx.setPropagationBehavior(
        org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public EvidenceService(String root, Db db, PlatformScope scope, ImageStorage storage,
      org.springframework.transaction.PlatformTransactionManager manager) {
    this(root, db, scope, storage, manager,
        new EvidenceReadLimits(java.time.Clock.systemUTC(), 4, 2, 120));
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

  public Read open(String id) throws IOException {
    require(id.matches("[a-f0-9-]{36}"), 404, "NOT_FOUND", "图片不存在");
    var e = db.one("SELECT * FROM evidence WHERE id=?", id);
    require(e != null, 404, "NOT_FOUND", "图片不存在");
    Actor a = Actor.current();
    require(!a.role().equals("DELIVERER"), 403, "FORBIDDEN", "配送员无权查看售后举证");
    scope.order(Db.id(e, "order_id"), false);
    require((id + ".png").equals(e.get("path")), 404, "NOT_FOUND", "图片不存在");
    var lease = reads.acquire(a.type() + ":" + a.id());
    SeekableByteChannel channel = null;
    try {
      var file = root.resolve(id + ".png");
      require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS), 404, "NOT_FOUND", "图片不存在");
      channel = Files.newByteChannel(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
      long size = channel.size();
      require(size > 0 && size <= 5L * 1024 * 1024,
          503, "EVIDENCE_UNAVAILABLE", "举证图片暂不可用");
      return new Read(channel, size, lease);
    } catch (Exception ex) {
      if (channel != null) try { channel.close(); } catch (IOException ignored) {}
      lease.close();
      if (ex instanceof NoSuchFileException)
        throw new com.sky.common.BusinessException(404, "NOT_FOUND", "图片不存在");
      if (ex instanceof IOException)
        throw new com.sky.common.BusinessException(503, "EVIDENCE_UNAVAILABLE", "举证图片暂不可用");
      throw ex;
    }
  }

  public static final class Read implements AutoCloseable {
    private final SeekableByteChannel channel;
    private final long size;
    private final EvidenceReadLimits.Lease lease;

    private Read(SeekableByteChannel channel, long size, EvidenceReadLimits.Lease lease) {
      this.channel = channel;
      this.size = size;
      this.lease = lease;
    }

    public long size() {
      return size;
    }

    /** Synchronous, bounded-memory transfer on the authenticated request thread. */
    public void transferTo(OutputStream output) throws IOException {
      var buffer = ByteBuffer.allocate(16 * 1024);
      long remaining = size;
      while (remaining > 0) {
        buffer.clear();
        buffer.limit((int) Math.min(buffer.capacity(), remaining));
        int count = channel.read(buffer);
        if (count < 0) throw new EOFException("Evidence file changed during transfer");
        output.write(buffer.array(), 0, count);
        remaining -= count;
      }
    }

    @Override
    public void close() throws IOException {
      try {
        channel.close();
      } finally {
        lease.close();
      }
    }
  }
}
