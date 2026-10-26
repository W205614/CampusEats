package com.sky.platform;

import static com.sky.common.BusinessException.require;

import com.sky.infra.*;
import com.sky.security.Actor;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiscoveryService {
  private final Db db;
  private final PlatformScope scope;
  private final StoreCatalog catalog;
  private final Clock clock;
  private final DirectoryService directory;
  private final ReportQueryService reports;

  public DiscoveryService(
      Db db,
      PlatformScope scope,
      StoreCatalog catalog,
      Clock clock,
      DirectoryService directory,
      ReportQueryService reports) {
    this.db = db;
    this.scope = scope;
    this.catalog = catalog;
    this.clock = clock;
    this.directory = directory;
    this.reports = reports;
  }

  public List<Map<String, Object>> search(long campus, String query, Long category, Boolean open) {
    require(query == null || query.length() <= 80, 400, "INVALID_QUERY", "搜索关键词过长");
    LocalDateTime at = LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Shanghai")));
    var stores =
        db.rows(
            "SELECT s.* FROM store s JOIN campus c ON c.id=s.campus_id WHERE s.campus_id=?"
                + " AND s.enabled=true AND c.enabled=true"
                + (Boolean.TRUE.equals(open) ? " AND s.open=true" : "")
                + " ORDER BY s.id LIMIT 200",
            campus);
    var ids =
        stores.stream()
            .filter(s -> open == null || directory.accepting(s, at) == open)
            .map(s -> Db.id(s, "id"))
            .toList();
    return catalog.search(ids, query, category, at);
  }

  public List<Map<String, Object>> favorites() {
    return db.rows(
        "SELECT f.*,s.name store_name,CASE WHEN f.target_type='STORE' THEN s.name WHEN"
            + " f.target_type='DISH' THEN d.name ELSE m.name END name FROM favorite f JOIN store s"
            + " ON s.id=f.store_id LEFT JOIN dish d ON f.target_type='DISH' AND d.id=f.target_id"
            + " AND d.store_id=f.store_id LEFT JOIN setmeal m ON f.target_type='SETMEAL' AND"
            + " m.id=f.target_id AND m.store_id=f.store_id WHERE f.user_id=? ORDER BY"
            + " f.store_id,f.target_type,f.target_id LIMIT 200",
        Actor.user().id());
  }

  @Transactional
  public void favorite(long store, String type, long id, boolean enabled) {
    long user = Actor.user().id();
    if (enabled) {
      scope.store(store, true);
      if (!type.equals("STORE")) catalog.product(store, type, id, false);
      else require(store == id, 400, "INVALID_FAVORITE", "店铺收藏参数不正确");
      db.update(
          "INSERT IGNORE INTO favorite(user_id,target_type,target_id,store_id) VALUES(?,?,?,?)",
          user,
          type,
          id,
          store);
    } else
      db.update(
          "DELETE FROM favorite WHERE user_id=? AND target_type=? AND target_id=? AND store_id=?",
          user,
          type,
          id,
          store);
  }

  @Transactional
  public long review(long order, PlatformCommands.Review input) {
    long user = Actor.user().id();
    var o = scope.order(order, true);
    require(Db.integer(o, "status") == 5, 409, "REVIEW_STATE", "完成订单后才可评价");
    require(
        db.count("SELECT COUNT(*) FROM review WHERE order_id=?", order) == 0,
        409,
        "REVIEW_EXISTS",
        "该订单已评价");
    return db.insert(
        "INSERT INTO review(order_id,user_id,store_id,rating,content,created_at)"
            + " VALUES(?,?,?,?,?,?)",
        order,
        user,
        o.get("store_id"),
        input.rating(),
        input.content(),
        LocalDateTime.now(clock));
  }

  public record ReviewStats(long total, java.math.BigDecimal rating) {}

  public record PublicReview(
      long id, int rating, String content, String reply, LocalDateTime createdAt) {}

  public record MerchantReview(
      long id,
      long orderId,
      int rating,
      String content,
      String reply,
      boolean hidden,
      String hideReason,
      LocalDateTime createdAt) {}

  public record PublicReviews(ReviewStats stats, List<PublicReview> records) {}

  public record MerchantReviews(ReviewStats stats, List<MerchantReview> records) {}

  public PublicReviews publicReviews(long store, ReviewQuery query) {
    // This public read follows the same enabled campus/store boundary as discovery.
    scope.store(store, true);
    var rows = reviewRows(store, query, false);
    return new PublicReviews(
        reviewStats(store),
        rows.stream()
            .map(
                r ->
                    new PublicReview(
                        Db.id(r, "id"),
                        Db.integer(r, "rating"),
                        r.get("content").toString(),
                        (String) r.get("reply"),
                        Db.time(r.get("created_at"))))
            .toList());
  }

  public MerchantReviews merchantReviews(long store, ReviewQuery query) {
    scope.merchant(store, false);
    var rows = reviewRows(store, query, true);
    return new MerchantReviews(
        reviewStats(store),
        rows.stream()
            .map(
                r ->
                    new MerchantReview(
                        Db.id(r, "id"),
                        Db.id(r, "order_id"),
                        Db.integer(r, "rating"),
                        r.get("content").toString(),
                        (String) r.get("reply"),
                        Db.bool(r, "hidden"),
                        (String) r.get("hide_reason"),
                        Db.time(r.get("created_at"))))
            .toList());
  }

  private List<Map<String, Object>> reviewRows(long store, ReviewQuery query, boolean admin) {
    int limit = query.pageLimit();
    var args = new ArrayList<Object>();
    args.add(store);
    if (query.beforeId() != null) args.add(query.beforeId());
    args.add(limit);
    return db.rows(
        "SELECT id,rating,content,reply,created_at"
            + (admin ? ",order_id,hidden,hide_reason" : "")
            + " FROM review WHERE store_id=?"
            + (admin ? "" : " AND hidden=false")
            + (query.beforeId() == null ? "" : " AND id<?")
            + " ORDER BY id DESC LIMIT ?",
        args.toArray());
  }

  private ReviewStats reviewStats(long store) {
    var stats =
        db.one(
            "SELECT COUNT(*) total,COALESCE(AVG(rating),0) rating FROM review"
                + " WHERE store_id=? AND hidden=false",
            store);
    return new ReviewStats(
        Db.id(stats, "total"), new java.math.BigDecimal(stats.get("rating").toString()));
  }

  @Transactional
  public void reply(long id, String text) {
    var r = db.one("SELECT * FROM review WHERE id=? FOR UPDATE", id);
    require(r != null, 404, "NOT_FOUND", "评价不存在");
    scope.merchant(Db.id(r, "store_id"), false);
    require(r.get("reply") == null, 409, "REPLY_EXISTS", "每条评价只能回复一次");
    db.update("UPDATE review SET reply=? WHERE id=?", text, id);
    scope.audit("REVIEW_REPLY", id, Db.id(r, "store_id"), "商家回复评价");
  }

  @Transactional
  public void hide(long id, String reason) {
    scope.platform();
    require(reason.length() <= 200, 400, "INVALID_REASON", "隐藏原因最多200字");
    var r = db.one("SELECT * FROM review WHERE id=? FOR UPDATE", id);
    require(r != null, 404, "NOT_FOUND", "评价不存在");
    db.update("UPDATE review SET hidden=true,hide_reason=? WHERE id=?", reason, id);
    scope.audit("REVIEW_HIDE", id, Db.id(r, "store_id"), reason);
  }

  public Map<String, Object> report(Long campus, Long store, LocalDate begin, LocalDate end) {
    return reports.snapshot(campus, store, begin, end);
  }

  public ReportQueryService.ReportSnapshot reportView(
      Long campus, Long store, LocalDate begin, LocalDate end) {
    return reports.view(campus, store, begin, end);
  }

  public byte[] export(Long campus, Long store, LocalDate begin, LocalDate end) {
    return reports.export(campus, store, begin, end);
  }
}
