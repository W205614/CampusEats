package com.sky.payment;

import static com.sky.common.BusinessException.require;
import static com.sky.payment.WechatPayChannel.*;

import com.sky.infra.*;
import com.sky.platform.PlatformScope;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.apache.commons.csv.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ChannelFinance {
  public record Merchant(String subMchid, boolean enabled, long version) {}

  public record BillLine(String reference, String kind, long cents, long feeCents) {}

  private final Db db;
  private final PlatformScope scope;
  private final Clock clock;
  private final WechatPayChannel channel;
  private final WechatPaySettings settings;
  private final TransactionTemplate tx;

  public ChannelFinance(
      Db db,
      PlatformScope scope,
      Clock clock,
      WechatPayChannel channel,
      WechatPaySettings settings,
      PlatformTransactionManager manager) {
    this.db = db;
    this.scope = scope;
    this.clock = clock;
    this.channel = channel;
    this.settings = settings;
    tx = new TransactionTemplate(manager);
    tx.setIsolationLevel(
        org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
  }

  public Merchant merchant(long store) {
    scope.merchant(store, true);
    var row =
        db.one(
            "SELECT sub_mchid,enabled,version FROM merchant_payment_config WHERE store_id=?",
            store);
    return row == null
        ? new Merchant("", false, 0)
        : new Merchant(
            row.get("sub_mchid").toString(), Db.bool(row, "enabled"), Db.id(row, "version"));
  }

  public void merchant(long store, Merchant input) {
    scope.platform();
    require(input.subMchid().matches("[0-9]{6,32}"), 400, "INVALID_MERCHANT", "子商户号格式不正确");
    tx.executeWithoutResult(
        s -> {
          scope.store(store, false);
          var existing =
              db.one("SELECT * FROM merchant_payment_config WHERE store_id=? FOR UPDATE", store);
          if (existing == null) {
            require(input.version() == 0, 409, "VERSION_CONFLICT", "收款配置已变化");
            db.update(
                "INSERT INTO merchant_payment_config(store_id,sub_mchid,enabled,updated_at)"
                    + " VALUES(?,?,?,?)",
                store,
                input.subMchid(),
                input.enabled(),
                now());
          } else
            require(
                db.update(
                        "UPDATE merchant_payment_config SET"
                            + " sub_mchid=?,enabled=?,version=version+1,updated_at=? WHERE"
                            + " store_id=? AND version=?",
                        input.subMchid(),
                        input.enabled(),
                        now(),
                        store,
                        input.version())
                    == 1,
                409,
                "VERSION_CONFLICT",
                "收款配置已变化");
          scope.audit("MERCHANT_PAYMENT_CONFIG", store, store, "更新店铺子商户收款配置");
        });
  }

  public void jointCoupon(long coupon, List<Long> stores) {
    scope.platform();
    require(
        !stores.isEmpty() && stores.size() <= 200 && new HashSet<>(stores).size() == stores.size(),
        400,
        "JOINT_COUPON_SCOPE",
        "请选择不重复的参与店铺");
    tx.executeWithoutResult(
        s -> {
          var own =
              db.one("SELECT * FROM coupon WHERE id=? AND store_id IS NULL FOR UPDATE", coupon);
          require(own != null, 404, "NOT_FOUND", "跨店联合券不存在");
          require(Db.integer(own, "claimed") == 0, 409, "COUPON_IMMUTABLE", "已领取优惠不能更改参与商家");
          for (long store : stores) scope.store(store, true);
          db.update("DELETE FROM joint_coupon_store WHERE coupon_id=?", coupon);
          for (long store : stores)
            db.update(
                "INSERT INTO joint_coupon_store(coupon_id,store_id) VALUES(?,?)", coupon, store);
          scope.audit("JOINT_COUPON_FUNDING", coupon, null, "参与商家共同承担优惠，平台不补贴");
        });
  }

  public List<Map<String, Object>> jointCoupon(long coupon) {
    scope.platform();
    return db.rows(
        "SELECT store_id FROM joint_coupon_store WHERE coupon_id=? ORDER BY store_id", coupon);
  }

  public Object reconcile(long store, LocalDate day) throws IOException {
    var merchant = merchant(store);
    require(!merchant.subMchid().isBlank(), 409, "MERCHANT_PAYMENT_UNAVAILABLE", "请先绑定子商户");
    require(day.isBefore(LocalDate.now(clock)), 400, "BILL_DATE", "只能对账已结束日期");
    var merchants = new LinkedHashSet<String>();
    merchants.add(merchant.subMchid());
    for (var p :
        db.rows(
            "SELECT DISTINCT sub_mchid FROM payment_channel_order WHERE store_id=? AND"
                + " success_time>=? AND success_time<?",
            store,
            day.atStartOfDay(),
            day.plusDays(1).atStartOfDay())) merchants.add(p.get("sub_mchid").toString());
    for (var p :
        db.rows(
            "SELECT DISTINCT r.sub_mchid FROM channel_refund r JOIN payment_channel_order p ON"
                + " p.intent_id=r.intent_id AND p.out_trade_no=r.out_trade_no WHERE p.store_id=?"
                + " AND r.accepted_at>=? AND r.accepted_at<?",
            store,
            day.atStartOfDay(),
            day.plusDays(1).atStartOfDay())) merchants.add(p.get("sub_mchid").toString());
    var lines = new ArrayList<BillLine>();
    var archive = new StringBuilder();
    for (String sub : merchants) {
      Map<String, Object> bill;
      try {
        bill =
            channel.request(
                "GET",
                "/v3/bill/tradebill?bill_date=" + day + "&bill_type=ALL&sub_mchid=" + sub,
                Map.of());
      } catch (com.wechat.pay.java.core.exception.ServiceException ex) {
        if (!ex.getErrorCode().equals("NO_BILL_EXIST")) throw ex;
        archive.append(sub).append(":NO_BILL_EXIST\n");
        continue;
      }
      byte[] content = channel.download(text(bill, "download_url"));
      String hashType = text(bill, "hash_type");
      require(Set.of("SHA1", "SHA256").contains(hashType), 502, "BILL_HASH", "不支持的渠道账单摘要算法");
      String actual =
          java.util.HexFormat.of()
              .formatHex(digest(hashType.equals("SHA1") ? "SHA-1" : "SHA-256", content));
      require(actual.equalsIgnoreCase(text(bill, "hash_value")), 502, "BILL_HASH", "渠道账单摘要不匹配");
      String csv = new String(content, StandardCharsets.UTF_8);
      lines.addAll(parse(csv, settings.merchantId, sub));
      archive.append(sub).append('\n').append(csv).append('\n');
      require(archive.length() <= 8 * 1024 * 1024, 502, "BILL_SIZE", "单店日账单超过处理上限");
    }
    String csv = archive.toString();
    byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
    require(bytes.length <= 16 * 1024 * 1024 - 1, 502, "BILL_SIZE", "单店日账单超过存储上限");
    return tx.execute(
        s -> {
          var old =
              db.one(
                  "SELECT * FROM channel_reconciliation WHERE store_id=? AND business_date=? FOR"
                      + " UPDATE",
                  store,
                  day);
          if (old != null) return summary(Db.id(old, "id"), store);
          long id =
              db.insert(
                  "INSERT INTO"
                      + " channel_reconciliation(store_id,business_date,state,bill_sha256,raw_csv,created_at)"
                      + " VALUES(?,?,'CHECKING',?,?,?)",
                  store,
                  day,
                  java.util.HexFormat.of().formatHex(digest("SHA-256", bytes)),
                  csv,
                  now());
          var expected = new LinkedHashMap<String, Long>();
          for (var p :
              db.rows(
                  "SELECT out_trade_no,amount_cents FROM payment_channel_order WHERE store_id=? AND"
                      + " success_time>=? AND success_time<? AND amount_cents>0 AND transaction_id"
                      + " IS NOT NULL",
                  store,
                  day.atStartOfDay(),
                  day.plusDays(1).atStartOfDay()))
            expected.put("PAYMENT:" + p.get("out_trade_no"), Db.id(p, "amount_cents"));
          // Trade bills record refunds on channel acceptance, independently of eventual bank
          // arrival.
          for (var r :
              db.rows(
                  "SELECT r.out_refund_no,r.amount_cents FROM channel_refund r JOIN"
                      + " payment_channel_order p ON p.intent_id=r.intent_id AND"
                      + " p.out_trade_no=r.out_trade_no WHERE p.store_id=? AND r.provider_id IS NOT"
                      + " NULL AND r.accepted_at>=? AND r.accepted_at<?",
                  store,
                  day.atStartOfDay(),
                  day.plusDays(1).atStartOfDay()))
            expected.put("REFUND:" + r.get("out_refund_no"), Db.id(r, "amount_cents"));
          long fees = 0;
          var seen = new HashSet<String>();
          for (var line : lines) {
            String key = line.kind() + ":" + line.reference();
            require(seen.add(key), 502, "BILL_DUPLICATE", "渠道账单重复交易记录");
            fees += line.feeCents();
            Long local = expected.remove(key);
            if (local == null || local != line.cents())
              difference(
                  id,
                  store,
                  line.reference(),
                  local == null ? "EXTERNAL_ONLY" : "AMOUNT_MISMATCH",
                  local,
                  line.cents());
          }
          for (var missing : expected.entrySet())
            difference(id, store, missing.getKey(), "LOCAL_ONLY", missing.getValue(), null);
          long differences =
              db.count("SELECT COUNT(*) FROM channel_difference WHERE reconciliation_id=?", id);
          db.update(
              "UPDATE channel_reconciliation SET"
                  + " state=?,trade_count=?,difference_count=?,fee_cents=? WHERE id=?",
              differences == 0 ? "MATCHED" : "DIFFERENCES",
              lines.size(),
              differences,
              fees,
              id);
          scope.audit("CHANNEL_RECONCILE", id, store, "渠道账单核对，差异数" + differences);
          return summary(id, store);
        });
  }

  static List<BillLine> parse(String csv) throws IOException {
    return parse(csv, null, null);
  }

  static List<BillLine> parse(String csv, String merchant, String sub) throws IOException {
    var lines = new ArrayList<BillLine>();
    try (var parser =
        CSVFormat.DEFAULT
            .builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .get()
            .parse(new StringReader(csv.replace("\uFEFF", "")))) {
      var headers = parser.getHeaderMap();
      for (String required : List.of("商户订单号", "交易状态", "商户退款单号", "手续费"))
        require(headers.containsKey(required), 502, "BILL_FORMAT", "渠道账单缺少列：" + required);
      String payment = headers.containsKey("订单金额") ? "订单金额" : "应结订单金额";
      String refund = headers.containsKey("申请退款金额") ? "申请退款金额" : "退款金额";
      require(
          headers.containsKey(payment) && headers.containsKey(refund),
          502,
          "BILL_FORMAT",
          "渠道账单缺少交易金额列");
      for (var record : parser) {
        if (clean(record.get(0)).equals("总交易单数")) break;
        if (merchant != null)
          require(
              headers.containsKey("商户号")
                  && headers.containsKey("特约商户号")
                  && clean(record.get("商户号")).equals(merchant)
                  && clean(record.get("特约商户号")).equals(sub),
              502,
              "BILL_MERCHANT",
              "渠道账单收款主体不匹配");
        String state = clean(record.get("交易状态"));
        if (state.equals("SUCCESS"))
          lines.add(
              new BillLine(
                  reference(record.get("商户订单号")),
                  "PAYMENT",
                  tradeCents(record.get(payment)),
                  cents(record.get("手续费"))));
        else if (state.equals("REFUND"))
          lines.add(
              new BillLine(
                  reference(record.get("商户退款单号")),
                  "REFUND",
                  tradeCents(record.get(refund)),
                  cents(record.get("手续费"))));
        else throw new com.sky.common.BusinessException(502, "BILL_FORMAT", "渠道账单出现未支持的交易状态，请人工核对");
      }
    } catch (IllegalArgumentException | ArithmeticException ex) {
      throw new com.sky.common.BusinessException(502, "BILL_FORMAT", "渠道账单格式或金额不正确");
    }
    return lines;
  }

  private static String clean(String value) {
    return value.strip().replaceFirst("^`", "");
  }

  private static String reference(String value) {
    String ref = clean(value);
    require(!ref.isBlank() && ref.length() <= 64, 502, "BILL_FORMAT", "渠道单号缺失或过长");
    return ref;
  }

  private static long cents(String value) {
    return new BigDecimal(clean(value)).movePointRight(2).longValueExact();
  }

  private static long tradeCents(String value) {
    long amount = cents(value);
    require(amount >= 0, 502, "BILL_FORMAT", "渠道交易金额不能为负");
    return amount;
  }

  private static byte[] digest(String type, byte[] bytes) {
    try {
      return java.security.MessageDigest.getInstance(type).digest(bytes);
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private void difference(
      long id, long store, String ref, String kind, Long expected, Long actual) {
    db.update(
        "INSERT INTO"
            + " channel_difference(reconciliation_id,store_id,reference,kind,expected_cents,actual_cents)"
            + " VALUES(?,?,?,?,?,?)",
        id,
        store,
        ref,
        kind,
        expected,
        actual);
  }

  public Object summary(long id, long store) {
    scope.merchant(store, true);
    return db.one(
        "SELECT"
            + " id,store_id,business_date,state,trade_count,difference_count,fee_cents,bill_sha256,created_at"
            + " FROM channel_reconciliation WHERE id=? AND store_id=?",
        id,
        store);
  }

  public Object statements(long store) {
    scope.merchant(store, true);
    return db.rows(
        "SELECT"
            + " id,store_id,business_date,state,trade_count,difference_count,fee_cents,bill_sha256,created_at"
            + " FROM channel_reconciliation WHERE store_id=? ORDER BY business_date DESC LIMIT 100",
        store);
  }

  public Object differences(long store) {
    scope.merchant(store, true);
    return db.rows(
        "SELECT * FROM channel_difference WHERE store_id=? ORDER BY id DESC LIMIT 200", store);
  }

  public void resolve(long store, long id, String reason) {
    scope.platform();
    require(
        db.update(
                "UPDATE channel_difference SET state='REVIEWED',resolution=? WHERE id=? AND"
                    + " store_id=? AND state='OPEN'",
                reason,
                id,
                store)
            == 1,
        409,
        "DIFFERENCE_STATE",
        "差异不存在或已处理");
    scope.audit("CHANNEL_DIFFERENCE_REVIEW", id, store, reason);
  }

  private LocalDateTime now() {
    return LocalDateTime.now(clock);
  }
}
