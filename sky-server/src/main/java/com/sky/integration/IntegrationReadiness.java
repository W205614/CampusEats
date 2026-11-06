package com.sky.integration;

import static com.sky.common.BusinessException.require;

import com.sky.infra.Db;
import com.sky.payment.WechatPaySettings;
import com.sky.platform.PlatformScope;
import java.time.*;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Durable channel responses establish connectivity; business acceptance additionally requires human
 * evidence.
 */
@Service
public class IntegrationReadiness {
  public record State(
      String provider,
      String status,
      long connectivityEvidence,
      String sourceRevision,
      String acceptanceEvidence,
      LocalDateTime acceptedAt) {}

  private final Db db;
  private final Environment env;
  private final WechatPaySettings payment;
  private final PlatformScope scope;
  private final Clock clock;

  public IntegrationReadiness(
      Db db, Environment env, WechatPaySettings payment, PlatformScope scope, Clock clock) {
    this.db = db;
    this.env = env;
    this.payment = payment;
    this.scope = scope;
    this.clock = clock;
  }

  public List<State> states() {
    scope.platform();
    return List.of("WECHAT_PAY", "TENCENT_SMS", "WECHAT_SUBSCRIPTION", "COS").stream()
        .map(this::state)
        .toList();
  }

  private State state(String name) {
    boolean configured =
        switch (name) {
          case "WECHAT_PAY" -> payment.enabled;
          case "TENCENT_SMS" ->
              env.getProperty("campus.identity.phone-enabled", Boolean.class, false);
          case "WECHAT_SUBSCRIPTION" ->
              env.getProperty("campus.notifications.enabled", Boolean.class, false)
                  && !env.getProperty("campus.wechat-appid", "").isBlank();
          case "COS" -> env.getProperty("campus.storage.backend", "local").equals("cos");
          default -> throw new IllegalArgumentException("Unknown integration");
        };
    long evidence =
        switch (name) {
          case "WECHAT_PAY" ->
              db.count(
                  "SELECT COUNT(*) FROM payment_intent WHERE action_json IS NOT NULL OR"
                      + " (state='SUCCEEDED' AND total_cents>0)");
          case "TENCENT_SMS" ->
              db.count(
                  "SELECT COUNT(*) FROM notification_delivery WHERE channel='SMS' AND"
                      + " channel_reference IS NOT NULL");
          case "WECHAT_SUBSCRIPTION" ->
              db.count(
                  "SELECT COUNT(*) FROM notification_delivery WHERE channel='WECHAT' AND"
                      + " state='ACCEPTED'");
          default ->
              db.count("SELECT COUNT(*) FROM stored_image WHERE backend='COS' AND state='READY'");
        };
    String revision = env.getProperty("APP_REVISION", "unknown");
    var accepted =
        db.one(
            "SELECT evidence_ref,accepted_at FROM integration_acceptance WHERE provider=? AND"
                + " source_revision=?",
            name,
            revision);
    String status =
        !configured
            ? "UNCONFIGURED"
            : evidence == 0
                ? "CONFIGURED"
                : accepted == null ? "CONNECTIVITY_VERIFIED" : "BUSINESS_VERIFIED";
    return new State(
        name,
        status,
        evidence,
        revision,
        accepted == null ? null : accepted.get("evidence_ref").toString(),
        accepted == null ? null : Db.time(accepted.get("accepted_at")));
  }

  public void accept(String provider, String evidence) {
    var actor = scope.platform();
    require(
        List.of("WECHAT_PAY", "TENCENT_SMS", "WECHAT_SUBSCRIPTION", "COS").contains(provider),
        400,
        "INTEGRATION",
        "未知接入项");
    var current = state(provider);
    require(
        env.matchesProfiles("prod")
            && !current.sourceRevision().equals("unknown")
            && current.connectivityEvidence() > 0,
        409,
        "LIVE_EVIDENCE_REQUIRED",
        "需要生产版本及实际渠道响应后才能登记人工验收");
    if (provider.equals("WECHAT_PAY")) {
      require(
          db.count(
                      "SELECT COUNT(DISTINCT channel) FROM payment_intent WHERE state='SUCCEEDED'"
                          + " AND total_cents>0")
                  == 4
              && db.count("SELECT COUNT(*) FROM channel_refund WHERE state='SUCCESS'") > 0
              && db.count("SELECT COUNT(*) FROM channel_reconciliation WHERE state='MATCHED'") > 0,
          409,
          "LIVE_EVIDENCE_REQUIRED",
          "请先完成四种支付场景、退款和渠道对账");
    }
    if (provider.equals("TENCENT_SMS"))
      require(
          db.count(
                  "SELECT COUNT(*) FROM notification_delivery WHERE channel='SMS' AND"
                      + " state='SUCCEEDED'")
              > 0,
          409,
          "LIVE_EVIDENCE_REQUIRED",
          "需要短信送达回执");
    if (provider.equals("COS"))
      require(
          db.count(
                  "SELECT COUNT(DISTINCT visibility) FROM stored_image WHERE backend='COS' AND"
                      + " state='READY'")
              == 2,
          409,
          "LIVE_EVIDENCE_REQUIRED",
          "需要商品与私有举证两种实际文件记录");
    db.update(
        "INSERT INTO"
            + " integration_acceptance(provider,source_revision,evidence_ref,actor_id,accepted_at)"
            + " VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE"
            + " evidence_ref=VALUES(evidence_ref),actor_id=VALUES(actor_id),accepted_at=VALUES(accepted_at)",
        provider,
        current.sourceRevision(),
        evidence,
        actor.id(),
        LocalDateTime.now(clock));
    scope.audit(
        "INTEGRATION_ACCEPTANCE", 0L, null, provider + " 人工验收记录，版本" + current.sourceRevision());
  }
}
