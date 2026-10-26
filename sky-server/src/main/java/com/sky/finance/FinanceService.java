package com.sky.finance;

import static com.sky.common.BusinessException.require;

import com.sky.common.Hashes;
import com.sky.infra.Db;
import com.sky.platform.DiscoveryCursor;
import com.sky.platform.PlatformScope;
import com.sky.security.Actor;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class FinanceService {
  private final Db db;
  private final Clock clock;
  private final PlatformScope scope;
  private final DiscoveryCursor cursors;

  public FinanceService(Db db, Clock clock, PlatformScope scope, DiscoveryCursor cursors) {
    this.db = db;
    this.clock = clock;
    this.scope = scope;
    this.cursors = cursors;
  }

  public FinanceViews.Page<FinanceViews.FinanceLedgerLine> ledger(
      Long store, LocalDate begin, LocalDate end, String type, String cursor, Integer limit) {
    authorize(store);
    range(begin, end, false);
    require(
        type == null || Set.of("PAYMENT", "REFUND").contains(type),
        400,
        "INVALID_STATE",
        "流水类型不正确");
    String binding = binding("ledger", store, begin, end, type);
    long before = position(cursor, binding);
    var args = new ArrayList<Object>();
    args.add(before);
    String where = " WHERE l.id<?";
    if (store != null) {
      where += " AND l.store_id=?";
      args.add(store);
    }
    if (begin != null) {
      where += " AND l.occurred_at>=? AND l.occurred_at<?";
      args.add(begin.atStartOfDay());
      args.add(end.plusDays(1).atStartOfDay());
    }
    if (type != null) {
      where += " AND l.source_type=?";
      args.add(type);
    }
    int size = limit(limit);
    args.add(size + 1);
    var rows =
        db.rows(
            "SELECT l.*,s.name store_name FROM finance_ledger l JOIN store s ON s.id=l.store_id"
                + where
                + " ORDER BY l.id DESC LIMIT ?",
            args.toArray());
    return page(rows, size, binding, this::ledgerLine);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public FinanceViews.FinancePreview preview(long store, LocalDate day) {
    authorize(store);
    day = day == null ? now().toLocalDate() : day;
    require(!day.isAfter(now().toLocalDate()), 400, "INVALID_DATE", "不能预览未来结算");
    var period = period(day);
    var totals = totals(store, period.start, period.end);
    long opening =
        db.count(
            "SELECT COALESCE(SUM(paid_cents-refunded_cents),0) FROM finance_opening WHERE"
                + " store_id=?",
            store);
    long paid = Db.id(totals, "paid"), refunded = Db.id(totals, "refunded");
    return new FinanceViews.FinancePreview(
        store,
        day,
        enabledAt(),
        period.start,
        period.end,
        opening,
        paid,
        refunded,
        paid - refunded,
        Db.id(totals, "entries"),
        0,
        0,
        paid < refunded,
        period.start.isAfter(day.atStartOfDay()),
        now());
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public FinanceViews.FinanceSettlement create(long store, LocalDate day) {
    scope.platform();
    require(
        day != null && day.isBefore(now().toLocalDate()),
        400,
        "SETTLEMENT_NOT_CLOSED",
        "只能生成已结束日期的模拟结算单");
    // Serializes same-store generation; business payments never acquire this lock after their
    // ledger write.
    require(
        db.one("SELECT id FROM store WHERE id=? FOR UPDATE", store) != null,
        404,
        "NOT_FOUND",
        "店铺不存在");
    var existing =
        db.one(
            "SELECT f.*,s.name store_name FROM finance_settlement f JOIN store s ON s.id=f.store_id"
                + " WHERE f.store_id=? AND f.business_date=?",
            store,
            day);
    if (existing != null) return settlement(existing);
    var period = period(day);
    var totals = totals(store, period.start, period.end);
    long paid = Db.id(totals, "paid"), refunded = Db.id(totals, "refunded");
    long id =
        db.insert(
            "INSERT INTO"
                + " finance_settlement(store_id,business_date,period_start,period_end,paid_cents,refunded_cents,net_cents,entry_count,created_at)"
                + " VALUES(?,?,?,?,?,?,?,?,?)",
            store,
            day,
            period.start,
            period.end,
            paid,
            refunded,
            paid - refunded,
            Db.id(totals, "entries"),
            now());
    db.update(
        "INSERT INTO finance_settlement_entry(settlement_id,ledger_id) SELECT ?,id FROM"
            + " finance_ledger WHERE store_id=? AND occurred_at>=? AND occurred_at<?",
        id,
        store,
        period.start,
        period.end);
    require(
        db.count("SELECT COUNT(*) FROM finance_settlement_entry WHERE settlement_id=?", id)
            == Db.id(totals, "entries"),
        409,
        "SETTLEMENT_CHANGED",
        "结算快照发生变化，请重试");
    scope.audit("SIMULATED_SETTLEMENT", id, store, "生成模拟结算；不发生实际资金划转");
    org.springframework.transaction.support.TransactionSynchronizationManager
        .registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
              @Override
              public void afterCommit() {
                org.slf4j.LoggerFactory.getLogger(FinanceService.class)
                    .info(
                        "Simulated settlement committed; settlementId={} storeId={}"
                            + " businessDate={}",
                        id,
                        store,
                        day);
              }
            });
    return detail(id, store);
  }

  public FinanceViews.Page<FinanceViews.FinanceSettlement> settlements(
      Long store, LocalDate begin, LocalDate end, String cursor, Integer limit) {
    authorize(store);
    range(begin, end, false);
    String binding = binding("settlements", store, begin, end, null);
    var args = new ArrayList<Object>();
    args.add(position(cursor, binding));
    String where = " WHERE f.id<?";
    if (store != null) {
      where += " AND f.store_id=?";
      args.add(store);
    }
    if (begin != null) {
      where += " AND f.business_date>=? AND f.business_date<=?";
      args.add(begin);
      args.add(end);
    }
    int size = limit(limit);
    args.add(size + 1);
    return page(
        db.rows(
            "SELECT f.*,s.name store_name FROM finance_settlement f JOIN store s ON s.id=f.store_id"
                + where
                + " ORDER BY f.id DESC LIMIT ?",
            args.toArray()),
        size,
        binding,
        this::settlement);
  }

  public FinanceViews.FinanceSettlement detail(long id, Long expectedStore) {
    var row =
        db.one(
            "SELECT f.*,s.name store_name FROM finance_settlement f JOIN store s ON s.id=f.store_id"
                + " WHERE f.id=?",
            id);
    require(
        row != null && (expectedStore == null || Db.id(row, "store_id") == expectedStore),
        404,
        "NOT_FOUND",
        "结算单不存在");
    if (expectedStore == null) scope.platform();
    else authorize(expectedStore);
    return settlement(row);
  }

  public FinanceViews.Page<FinanceViews.FinanceLedgerLine> settlementEntries(
      long id, Long store, String cursor, Integer limit) {
    var statement = detail(id, store);
    String binding = binding("settlement-entries:" + id, statement.storeId(), null, null, null);
    int size = limit(limit);
    return page(
        db.rows(
            "SELECT l.*,s.name store_name FROM finance_settlement_entry e"
                + " JOIN finance_ledger l ON l.id=e.ledger_id JOIN store s ON s.id=l.store_id"
                + " WHERE e.settlement_id=? AND l.id<? ORDER BY l.id DESC LIMIT ?",
            id,
            position(cursor, binding),
            size + 1),
        size,
        binding,
        this::ledgerLine);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public FinanceViews.FinanceReconciliation reconcile(Long store, LocalDate begin, LocalDate end) {
    scope.platform();
    if (store != null) scope.store(store, false);
    range(begin, end, true);
    String storeFilter = store == null ? "" : " AND o.store_id=?";
    Object[] storeArgs = store == null ? new Object[] {} : new Object[] {store};
    String balances =
        "SELECT o.id order_id,o.store_id,o.paid_cents order_paid,o.refunded_cents"
            + " order_refunded,COALESCE(f.paid_cents,0)+COALESCE(l.paid,0)"
            + " ledger_paid,COALESCE(f.refunded_cents,0)+COALESCE(l.refunded,0) ledger_refunded"
            + " FROM orders o LEFT JOIN finance_opening f ON f.order_id=o.id LEFT JOIN (SELECT"
            + " order_id,SUM(CASE WHEN source_type='PAYMENT' THEN amount_cents ELSE 0 END)"
            + " paid,SUM(CASE WHEN source_type='REFUND' THEN -amount_cents ELSE 0 END) refunded"
            + " FROM finance_ledger GROUP BY order_id) l ON l.order_id=o.id WHERE 1=1"
            + storeFilter;
    String differences =
        "SELECT * FROM ("
            + balances
            + ") b WHERE order_paid<>ledger_paid OR order_refunded<>ledger_refunded";
    long differenceCount = db.count("SELECT COUNT(*) FROM (" + differences + ") d", storeArgs);
    var rows = db.rows(differences + " ORDER BY order_id LIMIT 200", storeArgs);
    var totals =
        db.one(
            "SELECT COALESCE(SUM(order_paid-order_refunded),0) order_net,"
                + "COALESCE(SUM(ledger_paid-ledger_refunded),0) ledger_net FROM ("
                + balances
                + ") b",
            storeArgs);
    long opening =
        db.count(
            "SELECT COALESCE(SUM(paid_cents-refunded_cents),0) FROM finance_opening"
                + (store == null ? "" : " WHERE store_id=?"),
            storeArgs);
    var args = new ArrayList<Object>();
    args.add(begin.atStartOfDay());
    args.add(end.plusDays(1).atStartOfDay());
    if (store != null) args.add(store);
    var period =
        db.one(
            "SELECT COALESCE(SUM(CASE WHEN source_type='PAYMENT' THEN amount_cents ELSE 0 END),0)"
                + " paid,COALESCE(SUM(CASE WHEN source_type='REFUND' THEN -amount_cents ELSE 0"
                + " END),0) refunded FROM finance_ledger WHERE occurred_at>=? AND occurred_at<?"
                + (store == null ? "" : " AND store_id=?"),
            args.toArray());
    return new FinanceViews.FinanceReconciliation(
        store,
        begin,
        end,
        Db.id(period, "paid"),
        Db.id(period, "refunded"),
        opening,
        Db.id(totals, "order_net"),
        Db.id(totals, "ledger_net"),
        differenceCount,
        rows.stream()
            .map(
                r ->
                    new FinanceViews.FinanceDifference(
                        Db.id(r, "order_id"),
                        Db.id(r, "store_id"),
                        Db.id(r, "order_paid"),
                        Db.id(r, "order_refunded"),
                        Db.id(r, "ledger_paid"),
                        Db.id(r, "ledger_refunded")))
            .toList(),
        differenceCount > rows.size(),
        now());
  }

  private record Period(LocalDateTime start, LocalDateTime end) {}

  private Period period(LocalDate day) {
    var cutover = enabledAt();
    LocalDateTime end = day.plusDays(1).atStartOfDay();
    require(end.isAfter(cutover), 400, "BEFORE_FINANCE_START", "此日期早于模拟流水启用时间，请查看期初余额");
    return new Period(day.atStartOfDay().isBefore(cutover) ? cutover : day.atStartOfDay(), end);
  }

  private Map<String, Object> totals(long store, LocalDateTime start, LocalDateTime end) {
    return db.one(
        "SELECT COUNT(*) entries,COALESCE(SUM(CASE WHEN source_type='PAYMENT' THEN amount_cents"
            + " ELSE 0 END),0) paid,COALESCE(SUM(CASE WHEN source_type='REFUND' THEN -amount_cents"
            + " ELSE 0 END),0) refunded FROM finance_ledger WHERE store_id=? AND occurred_at>=? AND"
            + " occurred_at<?",
        store,
        start,
        end);
  }

  private LocalDateTime enabledAt() {
    return Db.time(db.one("SELECT captured_at FROM finance_cutover WHERE id=1").get("captured_at"));
  }

  private void authorize(Long store) {
    if (store == null) scope.platform();
    else scope.merchant(store, true);
  }

  private void range(LocalDate begin, LocalDate end, boolean required) {
    require(
        !required && begin == null && end == null
            || begin != null
                && end != null
                && !end.isBefore(begin)
                && java.time.temporal.ChronoUnit.DAYS.between(begin, end) < 366,
        400,
        "INVALID_RANGE",
        "日期必须成对填写且范围最多366天");
  }

  private int limit(Integer limit) {
    int size = limit == null ? 50 : limit;
    require(size >= 1 && size <= 200, 400, "INVALID_LIMIT", "每页允许1至200条");
    return size;
  }

  private String binding(String kind, Long store, LocalDate begin, LocalDate end, String type) {
    Actor a = Actor.employee();
    return Hashes.sha256(
        kind + ":" + a.type() + ":" + a.id() + ":" + a.role() + ":" + store + ":" + begin + ":"
            + end + ":" + type);
  }

  private long position(String cursor, String binding) {
    long id = cursors.decode(cursor, binding).id();
    return id == 0 ? Long.MAX_VALUE : id;
  }

  private <T> FinanceViews.Page<T> page(
      List<Map<String, Object>> rows,
      int size,
      String binding,
      Function<Map<String, Object>, T> mapper) {
    boolean more = rows.size() > size;
    var selected = rows.subList(0, Math.min(size, rows.size()));
    String next =
        more
            ? cursors.encode(
                binding, new DiscoveryCursor.Position(0, 0, Db.id(selected.getLast(), "id")))
            : null;
    return new FinanceViews.Page<>(selected.stream().map(mapper).toList(), next, more);
  }

  private FinanceViews.FinanceLedgerLine ledgerLine(Map<String, Object> row) {
    return new FinanceViews.FinanceLedgerLine(
        Db.id(row, "id"),
        row.get("source_type").toString(),
        Db.id(row, "source_id"),
        Db.id(row, "order_id"),
        Db.id(row, "store_id"),
        row.get("store_name").toString(),
        Db.id(row, "amount_cents"),
        Db.time(row.get("occurred_at")));
  }

  private FinanceViews.FinanceSettlement settlement(Map<String, Object> row) {
    long net = Db.id(row, "net_cents");
    return new FinanceViews.FinanceSettlement(
        Db.id(row, "id"),
        Db.id(row, "store_id"),
        row.get("store_name").toString(),
        LocalDate.parse(row.get("business_date").toString()),
        Db.time(row.get("period_start")),
        Db.time(row.get("period_end")),
        Db.id(row, "paid_cents"),
        Db.id(row, "refunded_cents"),
        net,
        Db.id(row, "entry_count"),
        net < 0,
        Db.time(row.get("created_at")));
  }

  private LocalDateTime now() {
    return LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Shanghai")));
  }
}
