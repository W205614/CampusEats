package com.sky.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class AuthFilterMetricsTest {
  @Test
  void currentCheckoutCountsEveryOutcomeAndRetiredEntryIsSeparate() throws Exception {
    var registry = new SimpleMeterRegistry();
    var errors = mock(ErrorWriter.class);
    doAnswer(
            i -> {
              ((jakarta.servlet.http.HttpServletResponse) i.getArgument(1))
                  .setStatus(i.getArgument(2));
              return null;
            })
        .when(errors)
        .write(any(), any(), anyInt(), anyString(), anyString());
    var filter = new AuthFilter(mock(AuthService.class), errors, mock(RateLimits.class), registry);
    for (int status : new int[] {200, 409, 503}) {
      var res = new MockHttpServletResponse();
      filter.doFilter(
          new MockHttpServletRequest("POST", "/api/v1/user/checkouts"),
          res,
          (a, b) -> res.setStatus(status));
    }
    filter.doFilter(
        new MockHttpServletRequest("POST", "/api/v1/user/orders"),
        new MockHttpServletResponse(),
        (a, b) -> fail("Retired route must be blocked"));
    for (String result : new String[] {"accepted", "business_rejected", "error"})
      assertEquals(
          1,
          registry
              .get("campus.order.requests")
              .tags("api", "checkout", "result", result)
              .counter()
              .count());
    assertEquals(
        1,
        registry
            .get("campus.order.requests")
            .tags("api", "legacy", "result", "business_rejected")
            .counter()
            .count());
  }
}
