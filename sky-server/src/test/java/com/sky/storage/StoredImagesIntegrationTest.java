package com.sky.storage;

import static org.junit.jupiter.api.Assertions.*;

import com.sky.common.BusinessException;
import com.sky.infra.Db;
import com.sky.security.Actor;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
class StoredImagesIntegrationTest {
  @Container
  static final MySQLContainer mysql =
      new MySQLContainer("mysql:8.4")
          .withDatabaseName("storage_test")
          .withUsername("test")
          .withPassword("test");

  @TempDir Path root;
  private Db db;
  private StoredImages service;

  @BeforeEach
  void setup() {
    var dataSource =
        new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
    db = new Db(new JdbcTemplate(dataSource));
    db.jdbc.execute("DROP TABLE IF EXISTS stored_image");
    db.jdbc.execute("DROP TABLE IF EXISTS blob_storage_budget");
    db.jdbc.execute("DROP TABLE IF EXISTS evidence");
    db.jdbc.execute("DROP TABLE IF EXISTS dish");
    db.jdbc.execute("DROP TABLE IF EXISTS setmeal");
    new ResourceDatabasePopulator(new ClassPathResource("db/migration/V22__blob_storage.sql"))
        .execute(dataSource);
    db.jdbc.execute("CREATE TABLE evidence(id VARCHAR(36) PRIMARY KEY)");
    db.jdbc.execute("CREATE TABLE dish(id BIGINT PRIMARY KEY,image VARCHAR(100))");
    db.jdbc.execute("CREATE TABLE setmeal(id BIGINT PRIMARY KEY,image VARCHAR(100))");
    service =
        new StoredImages(
            db,
            new LocalBlobStore(root.resolve("objects")),
            new StaticListableBeanFactory().getBeanProvider(CosBlobStore.class),
            "local",
            100,
            new DataSourceTransactionManager(dataSource));
    var actor = new Actor(7, "ADMIN", "PLATFORM_ADMIN", "fixture", false);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, List.of()));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void recordsImmutableMetadataAndEnforcesDatabaseQuotaAcrossServiceInstances() throws Exception {
    var source = root.resolve("staged.png");
    Files.write(source, new byte[60]);
    String id = UUID.randomUUID().toString();
    service.publish(id, source, true);
    var record = db.one("SELECT * FROM stored_image WHERE id=?", id);
    assertEquals("READY", record.get("state"));
    assertEquals("PRIVATE", record.get("visibility"));
    assertEquals(7L, Db.id(record, "owner_id"));
    assertEquals(64, record.get("sha256").toString().length());
    assertEquals(60L, db.count("SELECT used_bytes FROM blob_storage_budget WHERE id=1"));
    try (var content = service.open(id, true)) {
      assertEquals(60, content.size());
    }
    assertEquals(
        "NOT_FOUND", assertThrows(BusinessException.class, () -> service.open(id, false)).code());
    var secondInstance =
        new StoredImages(
            db,
            new LocalBlobStore(root.resolve("objects")),
            new StaticListableBeanFactory().getBeanProvider(CosBlobStore.class),
            "local",
            100,
            new DataSourceTransactionManager(db.jdbc.getDataSource()));
    assertEquals(
        "UPLOAD_STORAGE_FULL",
        assertThrows(
                BusinessException.class,
                () -> secondInstance.publish(UUID.randomUUID().toString(), source, false))
            .code());
    assertEquals(1, db.count("SELECT COUNT(*) FROM stored_image"));
    service.verify(id);
  }

  @Test
  void unknownPublicationKeepsReservationAndCanBeVerifiedWithoutReuploading() throws Exception {
    var source = root.resolve("staged.png");
    Files.write(source, new byte[] {1, 2, 3});
    String id = UUID.randomUUID().toString();
    service.publish(id, source, true);
    db.update("UPDATE stored_image SET state='UNKNOWN' WHERE id=?", id);
    assertEquals(
        "NOT_FOUND", assertThrows(BusinessException.class, () -> service.open(id, true)).code());
    service.verify(id);
    assertEquals("READY", db.one("SELECT state FROM stored_image WHERE id=?", id).get("state"));
    assertEquals(3, db.count("SELECT used_bytes FROM blob_storage_budget WHERE id=1"));
    db.update("UPDATE stored_image SET state='UNKNOWN' WHERE id=?", id);
    Files.write(root.resolve("objects/.private/" + id + ".png"), new byte[] {9, 9, 9});
    assertEquals(
        "STORAGE_INTEGRITY",
        assertThrows(BusinessException.class, () -> service.verify(id)).code());
    assertEquals("UNKNOWN", db.one("SELECT state FROM stored_image WHERE id=?", id).get("state"));
    service.unreferenced(id);
    assertEquals("UNKNOWN", db.one("SELECT state FROM stored_image WHERE id=?", id).get("state"));
  }

  @Test
  void remoteWriteWithLostAcknowledgementRecoversFromRecordedObjectOnAnotherInstance()
      throws Exception {
    var remoteBytes = new AtomicReference<byte[]>();
    var writes = new AtomicInteger();
    var fixture =
        new CosHttpFixture(
            request -> {
              if (request.method().equals("PUT")) {
                writes.incrementAndGet();
                remoteBytes.set(request.body());
                byte[] error =
                    "<Error><Code>InternalError</Code><Message>fixture lost response</Message></Error>"
                        .getBytes();
                return new CosHttpFixture.Response(500, Map.of(), error);
              } else {
                var bytes = remoteBytes.get();
                return new CosHttpFixture.Response(
                    200, Map.of("x-cos-version-id", "recovered-version"), bytes);
              }
            });
    var client = CosBlobStoreTest.client(fixture.port());
    try {
      var cos = new CosBlobStore(client, CosBlobStoreTest.PRODUCT, CosBlobStoreTest.EVIDENCE);
      var beans = new StaticListableBeanFactory();
      beans.addBean("cos", cos);
      var manager = new DataSourceTransactionManager(db.jdbc.getDataSource());
      var first =
          new StoredImages(
              db,
              new LocalBlobStore(root.resolve("first")),
              beans.getBeanProvider(CosBlobStore.class),
              "cos",
              100,
              manager);
      var second =
          new StoredImages(
              db,
              new LocalBlobStore(root.resolve("second")),
              beans.getBeanProvider(CosBlobStore.class),
              "cos",
              100,
              manager);
      var source = root.resolve("staged.png");
      Files.write(source, new byte[] {7, 8, 9});
      String id = UUID.randomUUID().toString();
      assertEquals(
          "UPLOAD_UNAVAILABLE",
          assertThrows(BusinessException.class, () -> first.publish(id, source, true)).code());
      var record = db.one("SELECT * FROM stored_image WHERE id=?", id);
      assertEquals("UNKNOWN", record.get("state"));
      assertEquals(CosBlobStoreTest.EVIDENCE, record.get("bucket_name"));
      assertEquals("images/" + id + ".png", record.get("object_key"));
      assertEquals(3, db.count("SELECT used_bytes FROM blob_storage_budget WHERE id=1"));
      second.verify(id);
      assertEquals(
          "recovered-version",
          db.one("SELECT object_version FROM stored_image WHERE id=?", id).get("object_version"));
      try (var content = second.open(id, true)) {
        var output = new ByteArrayOutputStream();
        content.transferTo(output);
        assertArrayEquals(new byte[] {7, 8, 9}, output.toByteArray());
      }
      assertEquals(1, writes.get());
      assertFalse(Files.exists(root.resolve("second")));
    } finally {
      client.shutdown();
      fixture.close();
    }
  }

  @Test
  void productionGuardRequiresCosAndRejectsReadyLocalObjects() throws Exception {
    var localGuard =
        new ProductionStorageGuard(
            db, new MockEnvironment().withProperty("campus.storage.backend", "local"));
    assertEquals(
        "PRODUCTION_STORAGE",
        assertThrows(BusinessException.class, () -> localGuard.run(null)).code());
    var cosGuard =
        new ProductionStorageGuard(
            db, new MockEnvironment().withProperty("campus.storage.backend", "cos"));
    assertDoesNotThrow(() -> cosGuard.run(null));
    var source = root.resolve("staged.png");
    Files.write(source, new byte[] {1});
    service.publish(UUID.randomUUID().toString(), source, false);
    assertEquals(
        "PRODUCTION_LOCAL_ASSETS",
        assertThrows(BusinessException.class, () -> cosGuard.run(null)).code());
  }

  @Test
  void productionGuardChecksEvidenceAndBothProductTablesForUnmigratedReferences() throws Exception {
    var guard =
        new ProductionStorageGuard(
            db, new MockEnvironment().withProperty("campus.storage.backend", "cos"));
    String id = UUID.randomUUID().toString();
    db.update("INSERT INTO evidence(id) VALUES(?)", id);
    assertEquals(
        "PRODUCTION_LOCAL_ASSETS",
        assertThrows(BusinessException.class, () -> guard.run(null)).code());
    db.update("DELETE FROM evidence");
    for (String table : List.of("dish", "setmeal")) {
      db.update("INSERT INTO " + table + "(id,image) VALUES(1,?)", "/uploads/" + id + ".png");
      assertEquals(
          "PRODUCTION_LOCAL_ASSETS",
          assertThrows(BusinessException.class, () -> guard.run(null)).code());
      db.update("DELETE FROM " + table);
    }
    var source = root.resolve("staged.png");
    Files.write(source, new byte[] {1});
    service.publish(id, source, true);
    db.update(
        "UPDATE stored_image SET"
            + " backend='COS',bucket_name='evidence-1234567890',object_key=?,object_version='fixture-version'"
            + " WHERE id=?",
        "images/" + id + ".png",
        id);
    db.update("INSERT INTO evidence(id) VALUES(?)", id);
    assertDoesNotThrow(() -> guard.run(null));
  }

  @Test
  void productionGuardDoesNotTreatPartialMigrationOrWrongVisibilityAsAReadyReference()
      throws Exception {
    var guard =
        new ProductionStorageGuard(
            db, new MockEnvironment().withProperty("campus.storage.backend", "cos"));
    var source = root.resolve("staged.png");
    Files.write(source, new byte[] {1});
    String evidence = UUID.randomUUID().toString();
    service.publish(evidence, source, true);
    db.update(
        "UPDATE stored_image SET"
            + " backend='COS',state='UNKNOWN',bucket_name='evidence-1234567890',object_key=?,object_version='fixture-version'"
            + " WHERE id=?",
        "images/" + evidence + ".png",
        evidence);
    db.update("INSERT INTO evidence(id) VALUES(?)", evidence);
    assertEquals(503, assertThrows(BusinessException.class, () -> guard.run(null)).status());
    db.update("UPDATE stored_image SET state='READY' WHERE id=?", evidence);
    assertDoesNotThrow(() -> guard.run(null));
    String product = UUID.randomUUID().toString();
    service.publish(product, source, false);
    db.update(
        "UPDATE stored_image SET"
            + " backend='COS',state='UPLOADING',bucket_name='products-1234567890',object_key=?,object_version='fixture-version'"
            + " WHERE id=?",
        "images/" + product + ".png",
        product);
    db.update("INSERT INTO dish(id,image) VALUES(1,?)", "/uploads/" + product + ".png");
    assertEquals(503, assertThrows(BusinessException.class, () -> guard.run(null)).status());
    db.update("UPDATE stored_image SET state='READY',visibility='PRIVATE' WHERE id=?", product);
    assertEquals(503, assertThrows(BusinessException.class, () -> guard.run(null)).status());
    db.update("UPDATE stored_image SET visibility='PUBLIC' WHERE id=?", product);
    assertDoesNotThrow(() -> guard.run(null));
  }
}
