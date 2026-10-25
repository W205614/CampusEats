package com.sky.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sky.api.Commands.Login;
import com.sky.common.BusinessException;
import com.sky.infra.Json;
import com.sky.web.AuthController;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.web.filter.ForwardedHeaderFilter;
import tools.jackson.databind.ObjectMapper;

class PublicReadLimitsTest {
  @Test
  void forwardedClientsGetIndependentPublicReadBudgetsAndRetryHeader() throws Exception {
    var limits = mock(RateLimits.class);
    var filter = new AuthFilter(mock(AuthService.class), new ErrorWriter(new Json(new ObjectMapper())),
        limits, new SimpleMeterRegistry());
    doThrow(new BusinessException(429, "RATE_LIMITED", "操作过于频繁"))
        .when(limits).check("search-ip:192.0.2.7", 1200, 60);
    var request = new MockHttpServletRequest("GET", "/api/v1/user/search/page");
    request.setRemoteAddr("172.18.0.4");
    request.addHeader("X-Forwarded-For", "192.0.2.7");
    var response = new MockHttpServletResponse();
    new ForwardedHeaderFilter().doFilter(request, response,
        (req, res) -> filter.doFilter(req, res, (a, b) -> fail("Must throttle")));
    assertEquals(429, response.getStatus());
    assertEquals("60", response.getHeader("Retry-After"));
    assertTrue(response.getContentAsString().contains("RATE_LIMITED"));
    var other = new MockHttpServletRequest("GET", "/api/v1/user/stores/page");
    other.setRemoteAddr("192.0.2.8");
    filter.doFilter(other, new MockHttpServletResponse(), (a, b) -> {});
    verify(limits).check("catalog-ip:192.0.2.8", 3000, 60);
    verify(limits, never()).check("search-ip:172.18.0.4", 1200, 60);
  }

  @Test
  void accountCaseVariantsShareTheExistingAccountBudget() {
    var limits = mock(RateLimits.class);
    doThrow(new BusinessException(429, "RATE_LIMITED", "busy"))
        .when(limits).check(anyString(), eq(10), eq(60));
    var auth = mock(AuthService.class);
    var controller = new AuthController(auth, limits);
    assertThrows(BusinessException.class, () -> controller.login(new Login("Admin", "secret")));
    assertThrows(BusinessException.class, () -> controller.login(new Login("admin", "secret")));
    verify(limits, times(2)).check("login-account:" + com.sky.common.Hashes.sha256("admin"), 10, 60);
    verifyNoInteractions(auth);
  }
}
