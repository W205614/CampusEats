package com.sky.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.web.bind.annotation.*;

class ApiAdviceTimeoutTest {
  @RestController
  static class Probe {
    @GetMapping("/timeout-probe/{kind}")
    void fail(@PathVariable String kind) {
      throw switch (kind) {
        case "query" -> new QueryTimeoutException("probe");
        case "transaction" -> new TransactionTimedOutException("probe");
        case "lock" -> new CannotAcquireLockException("probe");
        default -> new CannotGetJdbcConnectionException("probe");
      };
    }
  }

  @Test
  void timeoutsConflictsAndConnectionFailuresRemainDistinct() throws Exception {
    var mvc =
        MockMvcBuilders.standaloneSetup(new Probe())
            .setControllerAdvice(new ApiAdvice(new MockHttpServletRequest()))
            .build();
    for (String kind : new String[] {"query", "transaction"}) {
      mvc.perform(get("/timeout-probe/" + kind))
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.code").value("REQUEST_TIMEOUT"));
    }
    mvc.perform(get("/timeout-probe/lock"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
    mvc.perform(get("/timeout-probe/connection"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("DATABASE_UNAVAILABLE"));
  }
}
