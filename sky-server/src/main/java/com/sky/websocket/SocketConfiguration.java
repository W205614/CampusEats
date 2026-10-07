package com.sky.websocket;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.*;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

@Configuration
@EnableWebSocket
public class SocketConfiguration implements WebSocketConfigurer {
  private final OrderSocket handler;
  private final TicketService tickets;
  private final List<String> origins;

  public SocketConfiguration(
      OrderSocket handler,
      TicketService tickets,
      @Value("${campus.allowed-origins}") String origins) {
    this.handler = handler;
    this.tickets = tickets;
    this.origins = Arrays.asList(origins.split(","));
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(handler, "/ws/orders")
        .setAllowedOrigins(origins.toArray(String[]::new))
        .addInterceptors(
            new HandshakeInterceptor() {
              @Override
              public boolean beforeHandshake(
                  ServerHttpRequest req,
                  ServerHttpResponse res,
                  WebSocketHandler h,
                  Map<String, Object> attributes) {
                String origin = req.getHeaders().getOrigin();
                if (!origins.contains(origin)) return false;
                String ticket =
                    UriComponentsBuilder.fromUri(req.getURI())
                        .build()
                        .getQueryParams()
                        .getFirst("ticket");
                if (ticket == null || ticket.length() > 40) return false;
                var actor = tickets.consume(ticket);
                if (actor == null) return false;
                attributes.put("actor", actor);
                return true;
              }

              @Override
              public void afterHandshake(
                  ServerHttpRequest req,
                  ServerHttpResponse res,
                  WebSocketHandler h,
                  Exception ex) {}
            });
  }
}
