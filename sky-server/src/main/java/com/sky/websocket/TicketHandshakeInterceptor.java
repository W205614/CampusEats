package com.sky.websocket;

import com.sky.common.BusinessException;
import com.sky.security.RateLimits;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.*;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/** Request admission runs before ticket consumption; connection counts protect a later stage. */
final class TicketHandshakeInterceptor implements HandshakeInterceptor {
  private final List<String> origins;
  private final TicketService tickets;
  private final RateLimits limits;

  TicketHandshakeInterceptor(List<String> origins, TicketService tickets, RateLimits limits) {
    this.origins = List.copyOf(origins);
    this.tickets = tickets;
    this.limits = limits;
  }

  @Override
  public boolean beforeHandshake(ServerHttpRequest req, ServerHttpResponse res,
      WebSocketHandler handler, Map<String, Object> attributes) {
    if (!origins.contains(req.getHeaders().getOrigin())) {
      res.setStatusCode(HttpStatus.FORBIDDEN);
      return false;
    }
    try {
      var remote = req.getRemoteAddress();
      String address = remote == null || remote.getAddress() == null
          ? "unknown" : remote.getAddress().getHostAddress();
      limits.check("ws-handshake-ip:" + address, 60, 60);
      String ticket = UriComponentsBuilder.fromUri(req.getURI()).build().getQueryParams().getFirst("ticket");
      var actor = tickets.consume(ticket);
      if (actor == null) {
        res.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
      }
      attributes.put("actor", actor);
      return true;
    } catch (BusinessException ex) {
      res.setStatusCode(HttpStatus.valueOf(ex.status()));
      if (ex.status() == 429) res.getHeaders().set("Retry-After", "60");
      return false;
    } catch (IllegalArgumentException ex) {
      res.setStatusCode(HttpStatus.BAD_REQUEST);
      return false;
    }
  }

  @Override
  public void afterHandshake(ServerHttpRequest req, ServerHttpResponse res,
      WebSocketHandler handler, Exception ex) {}
}
