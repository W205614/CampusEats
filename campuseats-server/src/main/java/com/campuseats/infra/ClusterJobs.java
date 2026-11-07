package com.campuseats.infra;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

/** The database clock and conditional completion are shared by all application nodes. */
@Component
@ConditionalOnProperty(name = "campus.cluster.enabled", havingValue = "true")
public class ClusterJobs {
  private final Db db;
  private final TransactionTemplate tx;
  private final String owner;

  public record Lease(String job, String token) {}

  public ClusterJobs(
      Db db,
      PlatformTransactionManager manager,
      @Value("${campus.instance-id:${HOSTNAME:local}}") String owner) {
    this.db = db;
    this.owner = owner;
    tx = new TransactionTemplate(manager);
    tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public Lease claim(String name) {
    return tx.execute(
        status -> {
          String token = UUID.randomUUID().toString();
          int inserted =
              db.update(
                  "INSERT IGNORE INTO cluster_job_lease"
                      + "(job_name,owner_id,lease_token,lease_until,last_started_at)"
                      + " VALUES(?,?,?,TIMESTAMPADD(SECOND,30,UTC_TIMESTAMP(6)),UTC_TIMESTAMP(6))",
                  name,
                  owner,
                  token);
          if (inserted == 1) return new Lease(name, token);
          int changed =
              db.update(
                  "UPDATE cluster_job_lease SET owner_id=?,lease_token=?,"
                      + "lease_until=TIMESTAMPADD(SECOND,30,UTC_TIMESTAMP(6)),last_started_at=UTC_TIMESTAMP(6)"
                      + " WHERE job_name=? AND lease_until<=UTC_TIMESTAMP(6)",
                  owner,
                  token,
                  name);
          return changed == 1 ? new Lease(name, token) : null;
        });
  }

  public boolean owned(Lease lease) {
    return db.count(
            "SELECT COUNT(*) FROM cluster_job_lease WHERE job_name=? AND lease_token=?"
                + " AND lease_until>UTC_TIMESTAMP(6)",
            lease.job(),
            lease.token())
        == 1;
  }

  public boolean complete(Lease lease, String error) {
    return db.update(
            "UPDATE cluster_job_lease SET"
                + " lease_until=UTC_TIMESTAMP(6),last_completed_at=UTC_TIMESTAMP(6),last_success_at=IF(?"
                + " IS NULL,UTC_TIMESTAMP(6),last_success_at),last_error=? WHERE job_name=? AND"
                + " lease_token=? AND lease_until>UTC_TIMESTAMP(6)",
            error,
            error,
            lease.job(),
            lease.token())
        == 1;
  }

  public Map<String, Object> latest(String name) {
    return db.one(
        "SELECT last_completed_at,last_success_at,last_error FROM cluster_job_lease WHERE"
            + " job_name=?",
        name);
  }

  public static Instant utc(Object value) {
    return value == null ? null : Db.time(value).toInstant(ZoneOffset.UTC);
  }
}
