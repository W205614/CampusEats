package com.sky.security;

import static com.sky.common.BusinessException.require;

import com.sky.api.Commands.Password;
import com.sky.common.Hashes;
import com.sky.infra.Db;
import com.sky.platform.DiscoveryCursor;
import com.sky.platform.PlatformScope;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Local test accounts reuse user ownership, session revocation and password hashing. */
@Service
public class LocalUserService {
  @io.swagger.v3.oas.annotations.media.Schema(name = "LocalUserLogin")
  public record Login(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.-]{2,31}") String username,
      @NotBlank @Size(max = 128) String password) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "LocalUserCreate")
  public record Create(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.-]{2,31}") String username,
      @NotBlank @Size(max = 32) String name,
      @NotBlank @Size(min = 12, max = 128) String password) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "LocalUserUpdate")
  public record Update(@NotBlank @Size(max = 32) String name, boolean enabled) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "LocalUserResetPassword")
  public record Reset(@NotBlank @Size(min = 12, max = 128) String password) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "LocalUserView")
  public record UserView(
      long id,
      String username,
      String name,
      boolean enabled,
      boolean mustChangePassword,
      LocalDateTime createdAt) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "LocalUsersPage")
  public record Page(List<UserView> records, String nextCursor, boolean hasMore) {}

  public record LocalProfile(boolean localAccount, String username) {}

  private final Db db;
  private final AuthService auth;
  private final PlatformScope scope;
  private final DiscoveryCursor cursors;
  private final Clock clock;
  private final String missingAccountHash;

  public LocalUserService(
      Db db, AuthService auth, PlatformScope scope, DiscoveryCursor cursors, Clock clock) {
    this.db = db;
    this.auth = auth;
    this.scope = scope;
    this.cursors = cursors;
    this.clock = clock;
    this.missingAccountHash = auth.encodePassword(UUID.randomUUID().toString());
  }

  public static String username(String username) {
    return username.strip().toLowerCase(Locale.ROOT);
  }

  public LocalProfile profile() {
    var account =
        db.one("SELECT username FROM local_user_account WHERE user_id=?", Actor.user().id());
    return new LocalProfile(
        account != null, account == null ? null : account.get("username").toString());
  }

  @Transactional
  public Map<String, Object> login(Login input) {
    var credential =
        db.one(
            "SELECT * FROM local_user_account WHERE username=? FOR UPDATE",
            username(input.username()));
    boolean matches =
        auth.matches(
            input.password(),
            credential == null ? missingAccountHash : credential.get("password_hash").toString());
    var user =
        credential == null
            ? null
            : db.one("SELECT * FROM user WHERE id=? FOR UPDATE", credential.get("user_id"));
    require(
        credential != null && matches && user != null && Db.integer(user, "status") == 1,
        401,
        "BAD_CREDENTIALS",
        "用户名或密码错误");
    return auth.issue("USER", Db.id(user, "id"));
  }

  @Transactional
  public UserView create(Create input) {
    scope.platform();
    String name = username(input.username());
    // The unique key is the final guard when different administrators create concurrently.
    require(
        db.count("SELECT COUNT(*) FROM local_user_account WHERE username=?", name) == 0,
        409,
        "USERNAME_EXISTS",
        "该账号已存在");
    String encoded = auth.encodePassword(input.password());
    long user =
        db.insert("INSERT INTO user(name,create_time) VALUES(?,?)", input.name().strip(), now());
    try {
      db.update(
          "INSERT INTO local_user_account(user_id,username,password_hash,created_at)"
              + " VALUES(?,?,?,?)",
          user,
          name,
          encoded,
          now());
    } catch (org.springframework.dao.DuplicateKeyException ex) {
      throw new com.sky.common.BusinessException(409, "USERNAME_EXISTS", "该账号已存在");
    }
    scope.audit("LOCAL_USER_CREATE", user, null, "创建独立测试用户");
    return view(user);
  }

  @Transactional
  public UserView update(long id, Update input) {
    scope.platform();
    lock(id);
    db.update(
        "UPDATE user SET name=?,status=?,auth_version=auth_version+1 WHERE id=?",
        input.name().strip(),
        input.enabled() ? 1 : 0,
        id);
    revoke(id);
    scope.audit("LOCAL_USER_UPDATE", id, null, input.enabled() ? "更新测试用户" : "停用测试用户");
    return view(id);
  }

  @Transactional
  public UserView reset(long id, Reset input) {
    scope.platform();
    lock(id);
    db.update(
        "UPDATE local_user_account SET password_hash=?,must_change_password=true WHERE user_id=?",
        auth.encodePassword(input.password()),
        id);
    db.update("UPDATE user SET auth_version=auth_version+1 WHERE id=?", id);
    revoke(id);
    scope.audit("LOCAL_USER_RESET", id, null, "重置测试用户密码");
    return view(id);
  }

  @Transactional
  public void change(Password input) {
    long user = Actor.user().id();
    var account = lock(user);
    require(
        auth.matches(input.oldPassword(), account.get("password_hash").toString()),
        400,
        "BAD_PASSWORD",
        "原密码错误");
    require(
        !input.oldPassword().equals(input.password()), 400, "PASSWORD_UNCHANGED", "新密码不能与原密码相同");
    db.update(
        "UPDATE local_user_account SET password_hash=?,must_change_password=false WHERE user_id=?",
        auth.encodePassword(input.password()),
        user);
    db.update("UPDATE user SET auth_version=auth_version+1 WHERE id=?", user);
    revoke(user);
  }

  public Page page(String query, Boolean enabled, String cursor, Integer limit) {
    Actor actor = scope.platform();
    require(query == null || query.length() <= 80, 400, "INVALID_QUERY", "查询内容过长");
    int size = limit == null ? 50 : limit;
    require(size >= 1 && size <= 200, 400, "INVALID_LIMIT", "每页数量须为1至200");
    String text = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
    String range = Hashes.sha256("local-users|" + actor.id() + "|" + enabled + "|" + text);
    long before = cursors.decode(cursor, range).id();
    var args = new ArrayList<Object>();
    args.add(text);
    String filter = "";
    if (enabled != null) {
      filter += " AND u.status=?";
      args.add(enabled ? 1 : 0);
    }
    if (before != 0) {
      filter += " AND u.id<?";
      args.add(before);
    }
    args.add(size + 1);
    var rows =
        db.rows(
            "SELECT u.id,u.name,u.status,a.username,a.must_change_password,a.created_at FROM"
                + " local_user_account a JOIN user u ON u.id=a.user_id WHERE"
                + " LOCATE(?,LOWER(CONCAT(a.username,' ',u.name)))>0"
                + filter
                + " ORDER BY u.id DESC LIMIT ?",
            args.toArray());
    boolean more = rows.size() > size;
    var selected = rows.subList(0, Math.min(size, rows.size())).stream().map(this::view).toList();
    return new Page(
        selected,
        more
            ? cursors.encode(range, new DiscoveryCursor.Position(0, 0, selected.getLast().id()))
            : null,
        more);
  }

  private Map<String, Object> lock(long id) {
    var account = db.one("SELECT * FROM local_user_account WHERE user_id=? FOR UPDATE", id);
    require(account != null, 404, "NOT_FOUND", "独立测试用户不存在");
    require(
        db.one("SELECT id FROM user WHERE id=? FOR UPDATE", id) != null, 404, "NOT_FOUND", "用户不存在");
    return account;
  }

  private UserView view(long id) {
    return view(
        db.one(
            "SELECT u.id,u.name,u.status,a.username,a.must_change_password,a.created_at FROM user u"
                + " JOIN local_user_account a ON a.user_id=u.id WHERE u.id=?",
            id));
  }

  private UserView view(Map<String, Object> row) {
    return new UserView(
        Db.id(row, "id"),
        row.get("username").toString(),
        row.get("name").toString(),
        Db.integer(row, "status") == 1,
        Db.bool(row, "must_change_password"),
        Db.time(row.get("created_at")));
  }

  private void revoke(long id) {
    db.update(
        "UPDATE auth_session SET revoked=true WHERE subject_type='USER' AND subject_id=?", id);
  }

  private LocalDateTime now() {
    return LocalDateTime.now(clock);
  }
}
