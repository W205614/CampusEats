package com.sky.security;

import com.sky.infra.Json;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ErrorWriter {
  private final Json json;

  public ErrorWriter(Json json) {
    this.json = json;
  }

  public void write(
      HttpServletRequest req, HttpServletResponse res, int status, String code, String message)
      throws IOException {
    res.setStatus(status);
    res.setContentType("application/json;charset=UTF-8");
    var out = new LinkedHashMap<String, Object>();
    out.put("code", code);
    out.put("message", message);
    out.put("data", null);
    out.put("requestId", req.getAttribute("requestId"));
    res.getWriter().write(json.write(out));
  }
}
