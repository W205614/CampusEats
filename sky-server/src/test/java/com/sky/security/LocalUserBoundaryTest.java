package com.sky.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sky.common.BusinessException;
import com.sky.infra.Db;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.*;
import org.springframework.web.client.RestClient;

class LocalUserBoundaryTest {
  @Test
  void trialRejectsDemoCombinationAndNeverAdvertisesSharedIdentity() {
    var environment = new MockEnvironment();
    environment.setActiveProfiles("trial");
    var auth =
        new AuthService(
            mock(Db.class),
            Clock.systemUTC(),
            mock(RestClient.class),
            environment,
            "local-user-boundary-secret-thirty-two-characters",
            "",
            "");
    assertFalse(auth.capabilities().demoLoginEnabled());
    assertTrue(auth.capabilities().localLoginEnabled());
    assertFalse(auth.capabilities().wechatLoginEnabled());
    assertEquals("MOCK", auth.capabilities().paymentMode());
    environment.setActiveProfiles("trial", "demo");
    assertEquals(
        503,
        assertThrows(
                BusinessException.class,
                () ->
                    new AuthService(
                        mock(Db.class),
                        Clock.systemUTC(),
                        mock(RestClient.class),
                        environment,
                        "local-user-boundary-secret-thirty-two-characters",
                        "",
                        ""))
            .status());
  }

  @Test
  void firstLoginOnlyAllowsIdentityAndPasswordOperations() throws Exception {
    var auth = mock(AuthService.class);
    when(auth.authenticate("first")).thenReturn(new Actor(20, "USER", "USER", "first", true));
    var errors = mock(ErrorWriter.class);
    doAnswer(
            i -> {
              ((jakarta.servlet.http.HttpServletResponse) i.getArgument(1))
                  .setStatus(i.getArgument(2));
              return null;
            })
        .when(errors)
        .write(any(), any(), anyInt(), anyString(), anyString());
    var limits = mock(RateLimits.class);
    var filter = new AuthFilter(auth, errors, limits, new SimpleMeterRegistry());
    for (String path :
        new String[] {
          "/api/v1/user/auth/password",
          "/api/v1/user/auth/account",
          "/api/v1/user/auth/me",
          "/api/v1/user/auth/logout"
        }) {
      var request = new MockHttpServletRequest("POST", path);
      request.addHeader("Authorization", "Bearer first");
      var reached = new AtomicBoolean();
      filter.doFilter(request, new MockHttpServletResponse(), (a, b) -> reached.set(true));
      assertTrue(reached.get(), path);
    }
    var request = new MockHttpServletRequest("POST", "/api/v1/user/checkouts");
    request.addHeader("Authorization", "Bearer first");
    var response = new MockHttpServletResponse();
    filter.doFilter(
        request, response, (a, b) -> fail("Must change password before business writes"));
    assertEquals(403, response.getStatus());
    var login = new MockHttpServletRequest("POST", "/api/v1/user/auth/login");
    login.setRemoteAddr("192.0.2.8");
    filter.doFilter(login, new MockHttpServletResponse(), (a, b) -> {});
    verify(limits).check("login-ip:192.0.2.8", 20, 60);
  }
}
