package com.sky.websocket;

import java.util.*;
import com.sky.security.RateLimits;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class SocketConfiguration implements WebSocketConfigurer {
  private final OrderSocket handler;
  private final TicketService tickets;
  private final List<String> origins;
  private final RateLimits limits;

  public SocketConfiguration(
      OrderSocket handler,
      TicketService tickets,
      @Value("${campus.allowed-origins}") String origins,
      RateLimits limits) {
    this.handler = handler;
    this.tickets = tickets;
    this.origins = Arrays.asList(origins.split(","));
    this.limits = limits;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(handler, "/ws/orders")
        .setAllowedOrigins(origins.toArray(String[]::new))
        .addInterceptors(new TicketHandshakeInterceptor(origins, tickets, limits));
  }
}
