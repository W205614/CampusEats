package com.sky.platform;

import static com.sky.common.BusinessException.require;

import com.sky.common.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Versioned, filter-bound keyset positions; cursors never grant access. */
@Component
public class DiscoveryCursor {
  public record Position(long storeId, int rank, long id) {
    static final Position START = new Position(0, 0, 0);
  }

  private final byte[] key;

  public DiscoveryCursor(@Value("${campus.jwt-secret}") String secret) {
    key = sign(secret.getBytes(StandardCharsets.UTF_8), "campuseats:discovery:v1");
  }

  public String encode(String scope, Position position) {
    String body = "1|" + scope + "|" + position.storeId() + "|" + position.rank() + "|" + position.id();
    String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(body.getBytes(StandardCharsets.UTF_8));
    return payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(key, payload));
  }

  public Position decode(String cursor, String scope) {
    if (cursor == null) return Position.START;
    try {
      require(!cursor.isBlank() && cursor.length() <= 512, 400, "INVALID_CURSOR", "分页游标不正确");
      String[] token = cursor.split("\\.", -1);
      require(token.length == 2, 400, "INVALID_CURSOR", "分页游标不正确");
      byte[] provided = Base64.getUrlDecoder().decode(token[1]);
      require(MessageDigest.isEqual(sign(key, token[0]), provided), 400, "INVALID_CURSOR", "分页游标不正确");
      String[] fields = new String(Base64.getUrlDecoder().decode(token[0]), StandardCharsets.UTF_8).split("\\|", -1);
      require(fields.length == 5 && fields[0].equals("1") && fields[1].equals(scope),
          400, "INVALID_CURSOR", "筛选或账号已变化，请重新加载");
      var position = new Position(Long.parseLong(fields[2]), Integer.parseInt(fields[3]), Long.parseLong(fields[4]));
      require(position.storeId() >= 0 && position.id() > 0 && (position.rank() == 0 || position.rank() == 1),
          400, "INVALID_CURSOR", "分页游标不正确");
      return position;
    } catch (BusinessException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new BusinessException(400, "INVALID_CURSOR", "分页游标不正确");
    }
  }

  private static byte[] sign(byte[] key, String text) {
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return mac.doFinal(text.getBytes(StandardCharsets.UTF_8));
    } catch (java.security.GeneralSecurityException ex) {
      throw new IllegalStateException("Cannot sign discovery cursor", ex);
    }
  }
}
