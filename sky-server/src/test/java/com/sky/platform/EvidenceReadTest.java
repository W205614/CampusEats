package com.sky.platform;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.sky.business.ImageStorage;
import com.sky.common.BusinessException;
import com.sky.infra.Db;
import com.sky.security.Actor;
import com.sky.web.PlatformController;
import com.sky.web.ApiAdvice;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;

class EvidenceReadTest {
  private static final String ID = "12345678-1234-1234-1234-123456789abc";
  @TempDir Path root;
  private final Db db = mock(Db.class);
  private final PlatformScope scope = mock(PlatformScope.class);
  private EvidenceService service;

  @BeforeEach
  void setup() throws IOException {
    Files.createDirectories(root.resolve(".private"));
    when(db.one(anyString(), eq(ID))).thenReturn(Map.of("order_id", 5L, "path", ID + ".png"));
    act(1, "USER");
    service = new EvidenceService(root.toString(), db, scope, mock(ImageStorage.class),
        mock(PlatformTransactionManager.class), new EvidenceReadLimits(Clock.systemUTC(), 1, 1, 120));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void largeEvidenceStreamsInFixedChunksAndControllerDoesNotWrapImageInJson() throws Exception {
    // A large on-disk fixture; production should never allocate an image-sized byte array.
    long size = 4L * 1024 * 1024 + 17;
    try (var out = Files.newOutputStream(file())) {
      byte[] block = new byte[8192];
      Arrays.fill(block, (byte) 73);
      for (long remaining = size; remaining > 0; remaining -= Math.min(block.length, remaining))
        out.write(block, 0, (int) Math.min(block.length, remaining));
    }
    long[] transferred = {0};
    try (var read = service.open(ID)) {
      assertEquals(size, read.size());
      read.transferTo(new OutputStream() {
        @Override public void write(int value) { fail("Expected bounded block writes"); }
        @Override public void write(byte[] bytes, int offset, int length) {
          assertTrue(length > 0 && length <= 16 * 1024);
          for (int i = offset; i < offset + length; i++) assertEquals((byte) 73, bytes[i]);
          transferred[0] += length;
        }
      });
    }
    assertEquals(size, transferred[0]);
    Files.write(file(), new byte[] {1, 2, 3});
    var mvc = MockMvcBuilders.standaloneSetup(controller())
        .setControllerAdvice(new ApiAdvice(new org.springframework.mock.web.MockHttpServletRequest()))
        .build();
    var response = mvc.perform(get("/api/v1/evidence/" + ID)).andReturn().getResponse();
    assertEquals(200, response.getStatus());
    assertEquals("image/png", response.getContentType());
    assertEquals("no-store", response.getHeader("Cache-Control"));
    assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
    assertEquals("3", response.getHeader("Content-Length"));
    assertArrayEquals(new byte[] {1, 2, 3}, response.getContentAsByteArray());
    verify(scope, times(2)).order(5, false);
  }

  @Test
  void authenticationAndOrderScopeAreCheckedBeforeAnyReadAdmission() throws Exception {
    Files.write(file(), new byte[] {1});
    try (var held = service.open(ID)) {
      act(2, "USER");
      doThrow(new BusinessException(404, "NOT_FOUND", "订单不存在")).when(scope).order(5, false);
      assertEquals("NOT_FOUND", assertThrows(BusinessException.class, () -> service.open(ID)).code());
      act(3, "DELIVERER");
      assertEquals("FORBIDDEN", assertThrows(BusinessException.class, () -> service.open(ID)).code());
      SecurityContextHolder.clearContext();
      assertEquals("UNAUTHORIZED", assertThrows(BusinessException.class, () -> service.open(ID)).code());
    }
  }

  @Test
  void missingOrInvalidFilesNeverCommitSuccessAndReleaseAdmission() throws Exception {
    var response = new org.springframework.mock.web.MockHttpServletResponse();
    assertEquals("NOT_FOUND", assertThrows(BusinessException.class,
        () -> controller().evidence(ID, response)).code());
    assertFalse(response.isCommitted());
    assertNull(response.getContentType());
    Files.write(file(), new byte[0]);
    assertEquals("EVIDENCE_UNAVAILABLE", assertThrows(BusinessException.class, () -> service.open(ID)).code());
    Files.write(file(), new byte[] {1});
    try (var read = service.open(ID)) { assertEquals(1, read.size()); }
    when(db.one(anyString(), eq(ID))).thenReturn(Map.of("order_id", 5L, "path", "../outside.png"));
    assertEquals("NOT_FOUND", assertThrows(BusinessException.class, () -> service.open(ID)).code());
  }

  @Test
  void disconnectedClientReleasesFileAndGlobalAdmission() throws Exception {
    Files.write(file(), new byte[] {1, 2, 3});
    var response = mock(HttpServletResponse.class);
    when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
      @Override public boolean isReady() { return true; }
      @Override public void setWriteListener(WriteListener listener) {}
      @Override public void write(int value) throws IOException { throw new IOException("client disconnected"); }
    });
    assertThrows(IOException.class, () -> controller().evidence(ID, response));
    try (var reopened = service.open(ID)) { assertEquals(3, reopened.size()); }
  }

  @Test
  void globalAndAccountConcurrencyHaveIndependentBoundariesAndCloseIsIdempotent() {
    var limits = new EvidenceReadLimits(Clock.systemUTC(), 2, 1, 120);
    var first = limits.acquire("USER:1");
    assertEquals("EVIDENCE_BUSY", assertThrows(BusinessException.class,
        () -> limits.acquire("USER:1")).code());
    var second = limits.acquire("USER:2");
    assertEquals("EVIDENCE_BUSY", assertThrows(BusinessException.class,
        () -> limits.acquire("USER:3")).code());
    first.close(); first.close();
    var third = limits.acquire("USER:3");
    assertEquals("EVIDENCE_BUSY", assertThrows(BusinessException.class,
        () -> limits.acquire("USER:4")).code());
    second.close(); third.close();
    try (var next = limits.acquire("USER:1")) { assertNotNull(next); }
  }

  @Test
  void accountBudgetSurvivesPermitReleaseAndResetsAtWindowBoundary() {
    var now = new AtomicLong(1);
    var clock = new Clock() {
      @Override public ZoneId getZone() { return ZoneOffset.UTC; }
      @Override public Clock withZone(ZoneId zone) { return this; }
      @Override public Instant instant() { return Instant.ofEpochMilli(now.get()); }
    };
    var limits = new EvidenceReadLimits(clock, 2, 1, 2);
    limits.acquire("USER:1").close(); limits.acquire("USER:1").close();
    assertEquals("RATE_LIMITED", assertThrows(BusinessException.class,
        () -> limits.acquire("USER:1")).code());
    limits.acquire("USER:2").close();
    now.set(60001);
    limits.acquire("USER:1").close();
  }

  @Test
  void limiterAccountRegistryIsBoundedAndReclaimsExpiredInactiveEntries() {
    var now = new AtomicLong(1);
    var clock = new Clock() {
      @Override public ZoneId getZone() { return ZoneOffset.UTC; }
      @Override public Clock withZone(ZoneId zone) { return this; }
      @Override public Instant instant() { return Instant.ofEpochMilli(now.get()); }
    };
    var limits = new EvidenceReadLimits(clock, 2, 1, 120);
    for (int i = 0; i < 10000; i++) limits.acquire("USER:" + i).close();
    assertEquals("EVIDENCE_BUSY", assertThrows(BusinessException.class,
        () -> limits.acquire("USER:new")).code());
    now.set(60001);
    limits.acquire("USER:new").close();
  }

  private Path file() { return root.resolve(".private").resolve(ID + ".png"); }

  private PlatformController controller() {
    return new PlatformController(null, null, null, null, null, null, null, null,
        service, null, null, null, null, null);
  }

  private static void act(long id, String role) {
    var actor = new Actor(id, role.equals("USER") ? "USER" : "ADMIN", role, "test", false);
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(actor, null, List.of()));
  }
}
