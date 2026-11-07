package com.campuseats.storage;

import static com.campuseats.common.BusinessException.require;

import com.campuseats.common.BusinessException;
import com.campuseats.infra.Db;
import com.campuseats.security.Actor;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class StoredImages {
  public static final long MAX_BYTES = 5L * 1024 * 1024;
  private final Db db;
  private final LocalBlobStore local;
  private final BlobStore writer;
  private final TransactionTemplate tx;
  private final long quota;

  public StoredImages(
      Db db,
      @Qualifier("localBlobStore") LocalBlobStore local,
      org.springframework.beans.factory.ObjectProvider<CosBlobStore> cos,
      @Value("${campus.storage.backend:local}") String backend,
      @Value("${campus.upload-quota-bytes:2147483648}") long quota,
      PlatformTransactionManager manager) {
    this.db = db;
    this.local = local;
    require(Set.of("local", "cos").contains(backend), 503, "STORAGE_CONFIGURATION", "未知存储后端");
    this.writer = backend.equals("cos") ? cos.getObject() : local;
    this.quota = quota;
    this.tx = new TransactionTemplate(manager);
    tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public boolean remote() {
    return writer.backend().equals("COS");
  }

  public void publish(String id, Path source, boolean privateFile) throws IOException {
    publish(id, source, privateFile, null);
  }

  public void publish(String id, Path source, boolean privateFile, Long store) throws IOException {
    long bytes = Files.size(source);
    String digest = sha256(source);
    var actor = Actor.current();
    var destination = writer.destination(id, privateFile);
    tx.executeWithoutResult(
        status -> {
          var budget = db.one("SELECT * FROM blob_storage_budget WHERE id=1 FOR UPDATE");
          require(
              Db.id(budget, "used_bytes") <= quota - bytes, 507, "UPLOAD_STORAGE_FULL", "图片存储配额不足");
          require(
              privateFile || Db.id(budget, "public_count") < 10000,
              400,
              "UPLOAD_QUOTA",
              "图片数量达到上限");
          db.update(
              "UPDATE blob_storage_budget SET used_bytes=used_bytes+?,public_count=public_count+?"
                  + " WHERE id=1",
              bytes,
              privateFile ? 0 : 1);
          db.update(
              "INSERT INTO"
                  + " stored_image(id,backend,visibility,byte_size,sha256,owner_type,owner_id,store_id,bucket_name,object_key,state)"
                  + " VALUES(?,?,?,?,?,?,?,?,?,?,'UPLOADING')",
              id,
              writer.backend(),
              privateFile ? "PRIVATE" : "PUBLIC",
              bytes,
              digest,
              actor.type(),
              actor.id(),
              store == null ? actor.storeId() : store,
              destination.bucket(),
              destination.key());
        });
    try {
      var published = writer.put(id, source, privateFile);
      tx.executeWithoutResult(
          status ->
              db.update(
                  "UPDATE stored_image SET"
                      + " bucket_name=?,object_key=?,object_version=?,state='READY' WHERE id=? AND"
                      + " state='UPLOADING'",
                  published.bucket(),
                  published.key(),
                  published.version(),
                  id));
    } catch (RuntimeException | IOException failure) {
      // A timed-out PUT or COMMIT may already have succeeded. Retain the reservation and object.
      try {
        tx.executeWithoutResult(
            status ->
                db.update(
                    "UPDATE stored_image SET state='UNKNOWN' WHERE id=? AND state='UPLOADING'",
                    id));
      } catch (RuntimeException unknown) {
        org.slf4j.LoggerFactory.getLogger(getClass())
            .warn("Stored image outcome unknown; imageId={}", id);
      }
      throw new BusinessException(503, "UPLOAD_UNAVAILABLE", "图片存储结果未确认，请联系维护人员");
    }
  }

  public void unreferenced(String id) {
    try {
      tx.executeWithoutResult(
          status ->
              db.update(
                  "UPDATE stored_image SET state='UNREFERENCED' WHERE id=? AND state='READY'", id));
    } catch (RuntimeException failure) {
      org.slf4j.LoggerFactory.getLogger(getClass())
          .warn("Image reference outcome needs review; imageId={}", id);
    }
  }

  public BlobStore.Content open(String id, boolean privateFile) throws IOException {
    require(id.matches("[a-f0-9-]{36}"), 404, "NOT_FOUND", "图片不存在");
    var image = db.one("SELECT * FROM stored_image WHERE id=?", id);
    BlobStore.Content content;
    if (image == null) content = local.open("", id + ".png", null, privateFile);
    else {
      require(
          image.get("state").equals("READY")
              && image.get("visibility").equals(privateFile ? "PRIVATE" : "PUBLIC"),
          404,
          "NOT_FOUND",
          "图片不存在");
      var reader = image.get("backend").equals("LOCAL") ? local : writer;
      require(
          reader.backend().equals(image.get("backend")),
          503,
          "STORAGE_CONFIGURATION",
          "图片所需存储后端未配置");
      content =
          reader.open(
              (String) image.get("bucket_name"),
              (String) image.get("object_key"),
              (String) image.get("object_version"),
              privateFile);
    }
    if (content.size() <= 0
        || content.size() > MAX_BYTES
        || image != null && content.size() != Db.id(image, "byte_size")) {
      content.close();
      throw new BusinessException(503, "EVIDENCE_UNAVAILABLE", "图片暂不可用");
    }
    return content;
  }

  public List<Map<String, Object>> review(String state) {
    platform();
    require(
        Set.of("UPLOADING", "READY", "UNKNOWN", "UNREFERENCED").contains(state),
        400,
        "INVALID_INPUT",
        "存储状态不正确");
    return db.rows(
        "SELECT"
            + " id,backend,visibility,byte_size,sha256,owner_type,owner_id,store_id,state,created_at"
            + " FROM stored_image WHERE state=? ORDER BY created_at,id LIMIT 100",
        state);
  }

  public void verify(String id) throws IOException {
    platform();
    var image = db.one("SELECT * FROM stored_image WHERE id=?", id);
    require(image != null, 404, "NOT_FOUND", "图片不存在");
    require(
        Set.of("UNKNOWN", "UPLOADING", "READY").contains(image.get("state")),
        409,
        "STORAGE_STATE",
        "未引用文件需要先核查业务归属");
    var reader = image.get("backend").equals("LOCAL") ? local : writer;
    require(
        reader.backend().equals(image.get("backend")), 503, "STORAGE_CONFIGURATION", "图片所需存储后端未配置");
    String version;
    try (var content =
        reader.open(
            (String) image.get("bucket_name"),
            (String) image.get("object_key"),
            (String) image.get("object_version"),
            image.get("visibility").equals("PRIVATE"))) {
      require(
          content.size() == Db.id(image, "byte_size") && content.size() <= MAX_BYTES,
          409,
          "STORAGE_INTEGRITY",
          "图片大小与记录不符");
      version = content.version();
      require(
          !reader.backend().equals("COS")
              || version != null && !version.isBlank() && !version.equals("null"),
          503,
          "STORAGE_VERSIONING_REQUIRED",
          "COS存储桶必须启用版本控制");
      try {
        var digest = MessageDigest.getInstance("SHA-256");
        content.transferTo(new DigestOutputStream(OutputStream.nullOutputStream(), digest));
        require(
            HexFormat.of().formatHex(digest.digest()).equals(image.get("sha256")),
            409,
            "STORAGE_INTEGRITY",
            "图片摘要与记录不符");
      } catch (NoSuchAlgorithmException impossible) {
        throw new IllegalStateException(impossible);
      }
    }
    db.update(
        "UPDATE stored_image SET state='READY',object_version=? WHERE id=? AND state"
            + " IN('UNKNOWN','UPLOADING','READY')",
        version,
        id);
  }

  private static void platform() {
    require(Actor.employee().role().equals("PLATFORM_ADMIN"), 403, "FORBIDDEN", "需要平台管理员权限");
  }

  private static String sha256(Path source) throws IOException {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      try (var input = Files.newInputStream(source)) {
        byte[] buffer = new byte[16 * 1024];
        for (int n; (n = input.read(buffer)) >= 0; ) digest.update(buffer, 0, n);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
