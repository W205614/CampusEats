package com.sky.websocket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sky.common.BusinessException;
import com.sky.security.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.*;
import org.springframework.mock.web.*;
import org.springframework.web.filter.ForwardedHeaderFilter;
import org.springframework.web.socket.WebSocketHandler;

class TicketHandshakeTest {
  private final TicketService tickets = mock(TicketService.class);
  private final RateLimits limits = mock(RateLimits.class);
  private final TicketHandshakeInterceptor interceptor = new TicketHandshakeInterceptor(
      List.of("http://localhost:18083"), tickets, limits);
  private final String ticket = UUID.randomUUID().toString();

  @Test
  void invalidAndThrottledHandshakesFailBeforeAdmissionWithRetryInformation() throws Exception {
    var invalid = response();
    assertFalse(handshake(request(), invalid));
    assertEquals(401, invalid.getStatus());
    doThrow(new BusinessException(429, "RATE_LIMITED", "busy"))
        .when(limits).check("ws-handshake-ip:192.0.2.1", 60, 60);
    var limited = response();
    assertFalse(handshake(request(), limited));
    assertEquals(429, limited.getStatus());
    assertEquals("60", limited.getHeader("Retry-After"));
    verify(tickets, times(1)).consume(ticket);
  }

  @Test
  void dependencyFailureIs503AndSuccessfulTicketIsPlacedInAttributes() throws Exception {
    when(tickets.consume(ticket)).thenThrow(new BusinessException(503, "NOTIFICATION_UNAVAILABLE", "busy"));
    var unavailable = response();
    assertFalse(handshake(request(), unavailable));
    assertEquals(503, unavailable.getStatus());
    var actor = new Actor(2, "ADMIN", "SHOP_MANAGER", "session", false, 1L, 1L);
    doReturn(actor).when(tickets).consume(ticket);
    var attributes = new HashMap<String, Object>();
    assertTrue(interceptor.beforeHandshake(new ServletServerHttpRequest(request()),
        new ServletServerHttpResponse(response()), mock(WebSocketHandler.class), attributes));
    assertEquals(actor, attributes.get("actor"));
  }

  @Test
  void sourceBudgetUsesForwardedRequestAddressAndDisallowedOriginNeverConsumesTicket() throws Exception {
    var req = request();
    req.setRemoteAddr("172.18.0.4");
    req.addHeader("X-Forwarded-For", "192.0.2.42");
    new ForwardedHeaderFilter().doFilter(req, response(),
        (a, b) -> interceptor.beforeHandshake(new ServletServerHttpRequest((jakarta.servlet.http.HttpServletRequest) a),
            new ServletServerHttpResponse((jakarta.servlet.http.HttpServletResponse) b), mock(WebSocketHandler.class), new HashMap<>()));
    verify(limits).check("ws-handshake-ip:192.0.2.42", 60, 60);
    verify(limits, never()).check("ws-handshake-ip:172.18.0.4", 60, 60);
    clearInvocations(tickets, limits);
    var forbidden = request();
    forbidden.removeHeader("Origin");
    forbidden.addHeader("Origin", "http://untrusted.invalid");
    var rejected = response();
    assertFalse(handshake(forbidden, rejected));
    assertEquals(403, rejected.getStatus());
    verifyNoInteractions(tickets, limits);
  }

  private MockHttpServletRequest request() {
    var req = new MockHttpServletRequest("GET", "/ws/orders");
    req.setRemoteAddr("192.0.2.1");
    req.addHeader("Origin", "http://localhost:18083");
    req.setQueryString("ticket=" + ticket);
    return req;
  }
  private MockHttpServletResponse response() { return new MockHttpServletResponse(); }
  private boolean handshake(MockHttpServletRequest req, MockHttpServletResponse res) {
    return interceptor.beforeHandshake(new ServletServerHttpRequest(req), new ServletServerHttpResponse(res),
        mock(WebSocketHandler.class), new HashMap<>());
  }
}
