package com.sky.platform;

import static com.sky.common.BusinessException.require;

import com.sky.common.Hashes;
import com.sky.infra.Db;
import com.sky.security.Actor;
import java.util.*;
import org.springframework.stereotype.Component;

/** Filter- and identity-bound keyset pages shared by bounded operational reads. */
@Component
public class CursorPages {
  @io.swagger.v3.oas.annotations.media.Schema(requiredProperties = {"records", "hasMore"})
  public record Page<T>(List<T> records, String nextCursor, boolean hasMore) {}

  public record Request(String cursor, Integer limit, String state, String query) {
    public int size() {
      return CursorPages.size(limit);
    }

    public String text() {
      require(query == null || query.length() <= 80, 400, "INVALID_QUERY", "关键词最多80字");
      return query == null ? "" : query.strip();
    }
  }

  private final DiscoveryCursor signer;

  public CursorPages(DiscoveryCursor signer) {
    this.signer = signer;
  }

  public static int size(Integer limit) {
    require(limit == null || limit >= 1 && limit <= 200, 400, "INVALID_LIMIT", "每页允许1至200条");
    return limit == null ? 50 : limit;
  }

  public String scope(String kind, Object... filters) {
    Actor a = Actor.current();
    return Hashes.sha256(
        kind
            + "|"
            + a.type()
            + ":"
            + a.id()
            + ":"
            + a.role()
            + ":"
            + a.storeId()
            + ":"
            + a.campusId()
            + "|"
            + Arrays.deepToString(filters));
  }

  public long before(String cursor, String scope) {
    return cursor == null ? Long.MAX_VALUE : signer.decode(cursor, scope).id();
  }

  public DiscoveryCursor.Position position(String cursor, String scope) {
    return signer.decode(cursor, scope, 2);
  }

  public <T> Page<T> finish(
      List<T> fetched, int size, String scope, java.util.function.ToLongFunction<T> id) {
    boolean more = fetched.size() > size;
    var rows = List.copyOf(fetched.subList(0, Math.min(size, fetched.size())));
    return new Page<>(
        rows,
        more
            ? signer.encode(
                scope, new DiscoveryCursor.Position(0, 0, id.applyAsLong(rows.getLast())))
            : null,
        more);
  }

  public Page<Map<String, Object>> finish(List<Map<String, Object>> rows, int size, String scope) {
    return finish(rows, size, scope, r -> Db.id(r, "id"));
  }

  public String encode(String scope, DiscoveryCursor.Position p) {
    return signer.encode(scope, p);
  }
}
