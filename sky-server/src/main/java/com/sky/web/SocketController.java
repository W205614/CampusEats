package com.sky.web;

import com.sky.websocket.TicketService;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class SocketController {
  private final TicketService tickets;

  public SocketController(TicketService tickets) {
    this.tickets = tickets;
  }

  @PostMapping("/api/v1/admin/ws-ticket")
  public Map<String, Object> ticket() {
    return tickets.issue();
  }
}
