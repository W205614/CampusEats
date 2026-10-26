package com.sky.security;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** A read-only, loopback-only credential for the host's bounded maintenance script. */
@Component
public final class OperationsAccess {
  private static final Set<String> LOOPBACK = Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1");
  private final byte[] expected;

  public OperationsAccess(@Value("${campus.jwt-secret:}") String secret) {
    if (secret == null || secret.isBlank()) {
      expected = new byte[0];
      return;
    }
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      expected = HexFormat.of().formatHex(mac.doFinal(
          "campuseats:operations-read:v1".getBytes(StandardCharsets.UTF_8))).getBytes(StandardCharsets.US_ASCII);
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("Cannot initialize operations credential", ex);
    }
  }

  public Actor authenticate(HttpServletRequest request) {
    if (!request.getMethod().equals("GET")
        || !request.getRequestURI().equals("/actuator/health/operations")
        || !LOOPBACK.contains(request.getRemoteAddr())
        || request.getHeader("Authorization") != null) return null;
    String key = request.getHeader("X-Campus-Operations-Key");
    if (key == null || key.length() != 64
        || !MessageDigest.isEqual(expected, key.getBytes(StandardCharsets.US_ASCII))) return null;
    return new Actor(0, "ADMIN", "PLATFORM_ADMIN", "operations-read", false);
  }
}
