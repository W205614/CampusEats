package com.campuseats.infra;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Control keys cannot share the cache's eviction policy in production. */
@Configuration
public class RedisConfiguration {
  @Bean("stringRedisTemplate")
  @Primary
  StringRedisTemplate controlRedisTemplate(
      @Qualifier("redisConnectionFactory") RedisConnectionFactory connection) {
    return new StringRedisTemplate(connection);
  }

  @Bean("cacheRedisAccess")
  RedisAccess cacheRedisAccess() {
    return new RedisAccess();
  }

  @Bean("cacheRedisTemplate")
  StringRedisTemplate cacheRedisTemplate(
      @Qualifier("redisConnectionFactory") RedisConnectionFactory control,
      @Qualifier("cacheRedisConnectionFactory") ObjectProvider<RedisConnectionFactory> cache) {
    return new StringRedisTemplate(cache.getIfAvailable(() -> control));
  }

  @Configuration
  @Profile("prod")
  static class ProductionRedis {
    @Bean("redisConnectionFactory")
    @Primary
    LettuceConnectionFactory controlRedis(Environment env) {
      return connection(env, "campus.redis.control");
    }

    @Bean("cacheRedisConnectionFactory")
    LettuceConnectionFactory cacheRedis(Environment env) {
      return connection(env, "campus.redis.cache");
    }

    private static LettuceConnectionFactory connection(Environment env, String prefix) {
      var server =
          new RedisStandaloneConfiguration(
              env.getRequiredProperty(prefix + ".host"),
              env.getProperty(prefix + ".port", Integer.class, 6379));
      server.setPassword(env.getRequiredProperty(prefix + ".password"));
      server.setDatabase(env.getProperty(prefix + ".database", Integer.class, 0));
      var builder =
          LettuceClientConfiguration.builder()
              .commandTimeout(Duration.ofSeconds(1))
              .clientOptions(
                  ClientOptions.builder()
                      .socketOptions(
                          SocketOptions.builder().connectTimeout(Duration.ofSeconds(1)).build())
                      .build());
      if (env.getProperty(prefix + ".ssl", Boolean.class, true)) builder.useSsl();
      return new LettuceConnectionFactory(server, builder.build());
    }
  }
}
