package com.sky.websocket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sky.common.BusinessException;
import com.sky.infra.*;
import com.sky.security.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

class TicketServiceTest {
  private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
  @SuppressWarnings("unchecked")
  private final ValueOperations<String, String> values = mock(ValueOperations.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneId.of("Asia/Shanghai"));
  private final RedisAccess access = new RedisAccess(new SimpleMeterRegistry());
  private final Db db = mock(Db.class);
  private final Json json = new Json(new ObjectMapper());
  private final Actor actor = new Actor(3, "ADMIN", "SHOP_MANAGER", "session-3", false, 2L, 1L);

  @BeforeEach
  void setup() {
    when(redis.opsForValue()).thenReturn(values);
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, null));
  }
  @AfterEach
  void cleanup() { SecurityContextHolder.clearContext(); }

  @Test
  void ticketsAreSingleUseAndRecheckRevocationBeforeReturningAnActor() {
    var limits = mock(RateLimits.class);
    var service = new TicketService(redis, json, db, clock, access, limits);
    String ticket = service.issue("192.0.2.1").get("ticket").toString();
    verify(limits).check("ws-ticket-account:3", 30, 60);
    verify(limits).check("ws-ticket-ip:192.0.2.1", 60, 60);
    verify(values).set(eq("campus:ws:" + ticket), anyString(), eq(Duration.ofSeconds(30)));
    when(values.getAndDelete("campus:ws:" + ticket)).thenReturn(json.write(actor)).thenReturn(null);
    when(db.count(anyString(), any(), any(), any())).thenReturn(1L);
    when(db.one(anyString(), any())).thenReturn(Map.of("role", "SHOP_MANAGER", "store_id", 2L, "campus_id", 1L));
    assertEquals(actor, service.consume(ticket));
    assertNull(service.consume(ticket));
    String revoked = UUID.randomUUID().toString();
    when(values.getAndDelete("campus:ws:" + revoked)).thenReturn(json.write(actor));
    when(db.count(anyString(), any(), any(), any())).thenReturn(0L);
    assertNull(service.consume(revoked));
  }

  @Test
  void accountBudgetStopsTheThirtyFirstIssueBeforeCreatingAnotherTicket() {
    var service = new TicketService(redis, json, db, clock, access, new RateLimits(redis, clock, access));
    for (int i = 0; i < 30; i++) service.issue("192.0.2.1");
    var failure = assertThrows(BusinessException.class, () -> service.issue("192.0.2.1"));
    assertEquals(429, failure.status());
    verify(values, times(30)).set(anyString(), anyString(), eq(Duration.ofSeconds(30)));
  }

  @Test
  void sourceBudgetIsSharedAcrossEmployeeAccounts() {
    var service = new TicketService(redis, json, db, clock, access, new RateLimits(redis, clock, access));
    for (int account = 1; account <= 3; account++) {
      var employee = new Actor(account, "ADMIN", "SHOP_MANAGER", "session-" + account, false, 2L, 1L);
      SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(employee, null));
      for (int i = 0; i < 20; i++) service.issue("192.0.2.1");
    }
    assertEquals(429, assertThrows(BusinessException.class, () -> service.issue("192.0.2.1")).status());
    verify(values, times(60)).set(anyString(), anyString(), eq(Duration.ofSeconds(30)));
    assertNotNull(service.issue("192.0.2.2").get("ticket"));
  }

  @Test
  void redisFailureOpensCircuitAndNeverAuthenticatesATicket() {
    var service = new TicketService(redis, json, db, clock, access, mock(RateLimits.class));
    String ticket = UUID.randomUUID().toString();
    when(values.getAndDelete(anyString())).thenThrow(new IllegalStateException("Redis down"));
    assertEquals(503, assertThrows(BusinessException.class, () -> service.consume(ticket)).status());
    assertEquals(503, assertThrows(BusinessException.class, () -> service.consume(ticket)).status());
    assertTrue(access.isOpen());
    verify(values, times(1)).getAndDelete(anyString());
    verifyNoInteractions(db);
    assertEquals(503, assertThrows(BusinessException.class, () -> service.issue("192.0.2.1")).status());
    verify(values, never()).set(anyString(), anyString(), any(Duration.class));
  }

  @Test
  void malformedTicketDoesNotTouchRedisOrDatabase() {
    var service = new TicketService(redis, json, db, clock, access, mock(RateLimits.class));
    assertNull(service.consume(null));
    assertNull(service.consume("not-a-ticket"));
    verifyNoInteractions(values, db);
  }
}
