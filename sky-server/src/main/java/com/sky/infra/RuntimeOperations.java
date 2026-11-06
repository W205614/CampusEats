package com.sky.infra;

import java.time.*;
import java.util.*;
import java.util.function.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Health reads cached progress. Cluster peers refresh that progress while attempting a job. */
@Component("runtimeOperations")
public class RuntimeOperations implements HealthIndicator {
  private final Clock clock;
  private final boolean tasksEnabled;
  private final LongSupplier ticker;
  private final Instant started;
  private final Map<String, State> states = new LinkedHashMap<>();

  @Autowired(required = false)
  private ClusterJobs cluster;

  public RuntimeOperations(Clock clock, @Value("${campus.tasks-enabled:true}") boolean enabled) {
    this(clock, enabled, System::nanoTime);
  }

  @Autowired
  public RuntimeOperations(
      Clock clock,
      @Value("${campus.tasks-enabled:true}") boolean enabled,
      @Value("${campus.notifications.enabled:false}") boolean notificationsEnabled) {
    this(clock, enabled, System::nanoTime);
    states.put("externalNotifications", new State(enabled && notificationsEnabled, 120));
    states.put("smsReceipts", new State(enabled && notificationsEnabled, 180));
  }

  RuntimeOperations(Clock clock, boolean enabled, LongSupplier ticker) {
    this.clock = clock;
    this.tasksEnabled = enabled;
    this.ticker = ticker;
    this.started = clock.instant();
    for (String name :
        List.of(
            "checkoutExpiry",
            "channelPayments",
            "platformRefunds",
            "legacyRefunds",
            "outbox",
            "legacyExpiry",
            "deliveryOverdue")) states.put(name, new State(enabled, 120));
    states.put("fulfillmentExceptions", new State(enabled, 120));
    states.put("externalNotifications", new State(enabled, 120));
    states.put("smsReceipts", new State(enabled, 180));
    states.put("maintenance", new State(enabled, 720));
    states.put("metrics", new State(true, 60));
  }

  public final class Round {
    private final String name;
    private final long start = ticker.getAsLong();
    private String error;
    private ClusterJobs.Lease lease;

    private Round(String name) {
      this.name = name;
    }

    public boolean withinBudget() {
      return ticker.getAsLong() - start < 5_000_000_000L && (lease == null || cluster.owned(lease));
    }

    public void failed(Exception ex, Object id) {
      error = ex.getClass().getSimpleName();
      State state = states.get(name);
      synchronized (state) {
        state.error = error;
      }
      LoggerFactory.getLogger(RuntimeOperations.class)
          .warn("Task failed; job={} id={} type={}", name, id, error);
    }
  }

  public void run(String name, Consumer<Round> action) {
    State state = Objects.requireNonNull(states.get(name));
    synchronized (state) {
      if (!state.enabled || state.running) return;
      state.running = true;
      state.lastStarted = clock.instant();
    }
    Round round = new Round(name);
    boolean completed = false;
    try {
      if (cluster != null && !name.equals("metrics")) {
        round.lease = cluster.claim(name);
        if (round.lease == null) {
          var peer = cluster.latest(name);
          synchronized (state) {
            if (peer != null) {
              state.lastCompleted = ClusterJobs.utc(peer.get("last_completed_at"));
              state.lastSuccess = ClusterJobs.utc(peer.get("last_success_at"));
              state.error = (String) peer.get("last_error");
            }
            state.running = false;
          }
          return;
        }
      }
      action.accept(round);
      completed = true;
    } catch (Exception ex) {
      round.failed(ex, "batch");
    } finally {
      if (cluster != null && !name.equals("metrics") && round.lease == null) {
        synchronized (state) {
          state.running = false;
        }
      } else {
        if (round.lease != null) {
          try {
            if (!cluster.complete(round.lease, round.error)) round.error = "LEASE_LOST";
          } catch (Exception ex) {
            round.failed(ex, "cluster-completion");
          }
        }
        synchronized (state) {
          state.running = false;
          state.lastCompleted = clock.instant();
          state.error =
              completed ? round.error : Objects.requireNonNullElse(round.error, "ABORTED");
          if (state.error == null) state.lastSuccess = state.lastCompleted;
        }
      }
    }
  }

  public Map<String, Object> snapshot() {
    var jobs = new LinkedHashMap<String, Object>();
    var issues = new ArrayList<String>();
    for (var entry : states.entrySet()) {
      State state = entry.getValue();
      synchronized (state) {
        String status =
            !state.enabled
                ? "DISABLED"
                : state.error != null
                    ? "FAILED"
                    : Duration.between(
                                    state.lastCompleted == null ? started : state.lastCompleted,
                                    clock.instant())
                                .getSeconds()
                            > state.maxAge
                        ? "STALE"
                        : state.lastCompleted == null ? "STARTING" : "OK";
        if (status.equals("FAILED") || status.equals("STALE"))
          issues.add(entry.getKey() + ":" + status);
        var data = new LinkedHashMap<String, Object>();
        data.put("enabled", state.enabled);
        data.put("status", status);
        data.put("running", state.running);
        data.put("lastStartedAt", time(state.lastStarted));
        data.put("lastCompletedAt", time(state.lastCompleted));
        data.put("lastSuccessAt", time(state.lastSuccess));
        data.put("lastError", state.error);
        jobs.put(entry.getKey(), data);
      }
    }
    return Map.of(
        "tasksEnabled",
        tasksEnabled,
        "startedAt",
        started.toString(),
        "jobs",
        jobs,
        "issues",
        issues);
  }

  @Override
  public Health health() {
    var details = snapshot();
    return (((List<?>) details.get("issues")).isEmpty() ? Health.up() : Health.down())
        .withDetails(details)
        .build();
  }

  private static String time(Instant value) {
    return value == null ? null : value.toString();
  }

  private static class State {
    final boolean enabled;
    final long maxAge;
    boolean running;
    Instant lastStarted, lastCompleted, lastSuccess;
    String error;

    State(boolean enabled, long maxAge) {
      this.enabled = enabled;
      this.maxAge = maxAge;
    }
  }
}
