package com.campuseats.platform;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.campuseats.api.Commands;
import com.campuseats.infra.Db;
import com.campuseats.security.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(
    properties = {
      "spring.profiles.active=demo",
      "campus.jwt-secret=test-only-secret-thirty-two-bytes-long-value",
      "campus.demo-admin-password=TestAdmin!2026",
      "campus.tasks-enabled=false"
    })
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StoreCartReadIntegrationTest {
  @Container
  static final MySQLContainer mysql =
      new MySQLContainer("mysql:8.4")
          .withDatabaseName("cart_read_test")
          .withUsername("test")
          .withPassword("test")
          .withCreateContainerCmdModifier(c -> c.getHostConfig().withMemory(768L * 1024 * 1024));

  @Container
  static final GenericContainer<?> redis =
      new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", mysql::getJdbcUrl);
    r.add("spring.datasource.username", mysql::getUsername);
    r.add("spring.datasource.password", mysql::getPassword);
    r.add("spring.data.redis.host", redis::getHost);
    r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
  }

  @Autowired Db db;
  @Autowired AuthService auth;
  @Autowired MockMvc mvc;

  @Test
  void getLeavesAnEmptyCartUnwrittenAndTheFirstVersionedMutationStillWorks() throws Exception {
    var login = auth.demo(new Commands.DemoLogin(1));
    String token = login.get("token").toString();
    Object user = login.get("id");
    assertEquals(0, db.count("SELECT COUNT(*) FROM store_cart_state WHERE user_id=?", user));
    mvc.perform(get("/api/v1/user/stores/1/cart").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.cartVersion").value("0"))
        .andExpect(jsonPath("$.data.items").isEmpty());
    assertEquals(0, db.count("SELECT COUNT(*) FROM store_cart_state WHERE user_id=?", user));
    mvc.perform(
            post("/api/v1/user/stores/1/cart/clear")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"version\":0}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/user/stores/1/cart").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.cartVersion").value("1"));
    assertEquals(1, db.count("SELECT COUNT(*) FROM store_cart_state WHERE user_id=?", user));
  }
}
