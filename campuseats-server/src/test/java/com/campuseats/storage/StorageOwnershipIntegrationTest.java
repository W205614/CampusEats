package com.campuseats.storage;

import static org.junit.jupiter.api.Assertions.*;

import com.campuseats.api.Commands;
import com.campuseats.business.ImageStorage;
import com.campuseats.common.BusinessException;
import com.campuseats.infra.Db;
import com.campuseats.platform.StoreCatalog;
import com.campuseats.security.Actor;
import com.campuseats.web.PlatformAftersaleController;
import com.campuseats.web.PlatformController;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(
    properties = {
      "spring.profiles.active=demo",
      "campus.jwt-secret=storage-test-only-secret-thirty-two-bytes-long",
      "campus.demo-admin-password=TestAdmin!2026",
      "campus.tasks-enabled=false",
      "campus.upload-min-free-bytes=0"
    })
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StorageOwnershipIntegrationTest {
  @Container
  static final MySQLContainer mysql =
      new MySQLContainer("mysql:8.4")
          .withDatabaseName("storage_ownership_test")
          .withUsername("test")
          .withPassword("test")
          .withCreateContainerCmdModifier(c -> c.getHostConfig().withMemory(768L * 1024 * 1024));

  @Container
  static final GenericContainer<?> redis =
      new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

  @TempDir static Path uploads;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", mysql::getJdbcUrl);
    registry.add("spring.datasource.username", mysql::getUsername);
    registry.add("spring.datasource.password", mysql::getPassword);
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    registry.add("campus.uploads", () -> uploads.toString());
  }

  @Autowired Db db;
  @Autowired PlatformController controller;
  @Autowired StoreCatalog catalog;
  @Autowired ImageStorage storage;
  @Autowired PlatformAftersaleController aftersales;
  long store, otherStore, category;

  @BeforeEach
  void setup() {
    long campus = db.insert("INSERT INTO campus(name) VALUES(?)", "图片校区" + UUID.randomUUID());
    store = db.insert("INSERT INTO store(campus_id,name) VALUES(?,'目标店')", campus);
    otherStore = db.insert("INSERT INTO store(campus_id,name) VALUES(?,'另一店')", campus);
    category =
        db.insert(
            "INSERT INTO category(store_id,name,type,sort,status) VALUES(?,'菜品',1,0,1)", store);
    platform();
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void platformAndMerchantUploadsRecordTheAuthorizedTargetStore() throws Exception {
    String platformImage = upload(store);
    var metadata = db.one("SELECT * FROM stored_image WHERE id=?", imageId(platformImage));
    assertEquals(store, Db.id(metadata, "store_id"));
    assertEquals(1L, Db.id(metadata, "owner_id"));
    assertEquals("PUBLIC", metadata.get("visibility"));
    long first = catalog.save(store, "DISH", null, product(platformImage));
    assertEquals(platformImage, db.one("SELECT image FROM dish WHERE id=?", first).get("image"));

    merchant();
    String merchantImage = upload(store);
    metadata = db.one("SELECT * FROM stored_image WHERE id=?", imageId(merchantImage));
    assertEquals(store, Db.id(metadata, "store_id"));
    assertEquals(42L, Db.id(metadata, "owner_id"));
    catalog.save(store, "DISH", first, product(merchantImage));
    assertEquals(merchantImage, db.one("SELECT image FROM dish WHERE id=?", first).get("image"));
  }

  @Test
  void newAndReplacementReferencesRejectPrivateForeignUnknownAndUnfinishedImages()
      throws Exception {
    long existing = catalog.save(store, "DISH", null, product("/demo-images/dish.svg"));
    String privateImage;
    try (var staged = storage.stage(picture())) {
      privateImage = "/uploads/" + staged.publish(true, store) + ".png";
      staged.accept();
    }
    String foreign = upload(otherStore);
    String missing = "/uploads/" + UUID.randomUUID() + ".png";
    String pending = upload(store);
    for (String image : List.of(privateImage, foreign, missing))
      rejectNewAndReplacement(existing, image);
    for (String state : List.of("UPLOADING", "UNKNOWN", "UNREFERENCED")) {
      db.update("UPDATE stored_image SET state=? WHERE id=?", state, imageId(pending));
      rejectNewAndReplacement(existing, pending);
    }
    assertEquals(1, db.count("SELECT COUNT(*) FROM dish WHERE store_id=?", store));
    assertEquals(
        "/demo-images/dish.svg",
        db.one("SELECT image FROM dish WHERE id=?", existing).get("image"));
  }

  @Test
  void unchangedLegacyReferenceComesFromTheLockedProductAndCannotAuthorizeANewReference()
      throws Exception {
    String legacy = "/uploads/" + UUID.randomUUID() + ".png";
    long existing =
        db.insert(
            "INSERT INTO dish(store_id,category_id,name,price,image,status)"
                + " VALUES(?,?,'旧商品',1,?,1)",
            store,
            category,
            legacy);
    catalog.save(store, "DISH", existing, product(legacy));
    assertEquals(legacy, db.one("SELECT image FROM dish WHERE id=?", existing).get("image"));
    assertEquals(
        "INVALID_IMAGE",
        assertThrows(
                BusinessException.class, () -> catalog.save(store, "DISH", null, product(legacy)))
            .code());
    String forged = "/uploads/" + UUID.randomUUID() + ".png";
    rejectNewAndReplacement(existing, forged);
    catalog.save(store, "DISH", existing, product(upload(store)));
    catalog.save(store, "DISH", existing, product("/demo-images/dish.svg"));
    catalog.save(store, "DISH", existing, product(""));
  }

  @Test
  void merchantCannotAllocateAnUploadToAnotherStore() throws Exception {
    merchant();
    long before = db.count("SELECT COUNT(*) FROM stored_image");
    assertEquals(
        "FORBIDDEN", assertThrows(BusinessException.class, () -> upload(otherStore)).code());
    assertEquals(before, db.count("SELECT COUNT(*) FROM stored_image"));
  }

  @Test
  void fullPendingEvidenceAllowanceRejectsBeforeAllocatingObjectsOrStorageBudget()
      throws Exception {
    long order =
        db.insert(
            "INSERT INTO orders(user_id,address_book_id,order_time,amount,store_id,campus_id)"
                + " VALUES(77,0,CURRENT_TIMESTAMP,1,?,1)",
            store);
    for (int index = 0; index < 20; index++) {
      String id = UUID.randomUUID().toString();
      db.update(
          "INSERT INTO evidence(id,user_id,order_id,path,created_at)"
              + " VALUES(?,77,?,?,CURRENT_TIMESTAMP)",
          id,
          order,
          id + ".png");
    }
    act(new Actor(77, "USER", "USER", "fixture", false));
    long objectsBefore = db.count("SELECT COUNT(*) FROM stored_image");
    long bytesBefore = db.count("SELECT used_bytes FROM blob_storage_budget WHERE id=1");
    var failure =
        assertThrows(BusinessException.class, () -> aftersales.uploadEvidence(order, picture()));
    assertEquals("EVIDENCE_LIMIT", failure.code());
    assertEquals(objectsBefore, db.count("SELECT COUNT(*) FROM stored_image"));
    assertEquals(bytesBefore, db.count("SELECT used_bytes FROM blob_storage_budget WHERE id=1"));
    assertEquals(20, db.count("SELECT COUNT(*) FROM evidence WHERE order_id=?", order));
  }

  private void rejectNewAndReplacement(long existing, String image) {
    for (Long id : Arrays.asList(null, existing)) {
      var failure =
          assertThrows(
              BusinessException.class, () -> catalog.save(store, "DISH", id, product(image)));
      assertEquals("INVALID_IMAGE", failure.code());
    }
  }

  private String upload(long target) throws Exception {
    return ((Map<?, ?>) controller.productUpload(target, picture())).get("url").toString();
  }

  private Commands.Product product(String image) {
    return new Commands.Product(
        "商品", category, BigDecimal.ONE, true, "", image, List.of(), List.of());
  }

  private MockMultipartFile picture() throws Exception {
    var output = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
    return new MockMultipartFile("file", "picture.png", "image/png", output.toByteArray());
  }

  private static String imageId(String image) {
    return image.substring("/uploads/".length(), image.length() - 4);
  }

  private void platform() {
    act(new Actor(1, "ADMIN", "PLATFORM_ADMIN", "fixture", false));
  }

  private void merchant() {
    act(new Actor(42, "ADMIN", "SHOP_MANAGER", "fixture", false, store, 1L));
  }

  private static void act(Actor actor) {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, List.of()));
  }
}
