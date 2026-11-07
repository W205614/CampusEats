package com.campuseats.infra;

import static com.campuseats.common.BusinessException.require;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.*;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
@org.springframework.core.annotation.Order(0)
public class ProductionInfrastructureGuard implements ApplicationRunner {
  private final Environment env;
  private final RedisConnectionFactory control;

  public ProductionInfrastructureGuard(
      Environment env, @Qualifier("redisConnectionFactory") RedisConnectionFactory control) {
    this.env = env;
    this.control = control;
  }

  @Override
  public void run(ApplicationArguments args) {
    require(
        !env.acceptsProfiles(org.springframework.core.env.Profiles.of("demo", "trial", "docker")),
        503,
        "PRODUCTION_PROFILE",
        "prod不能与demo、trial或docker配置同时启用");
    String controlAddress =
        env.getRequiredProperty("campus.redis.control.host")
            + ":"
            + env.getProperty("campus.redis.control.port", "6379");
    String cacheAddress =
        env.getRequiredProperty("campus.redis.cache.host")
            + ":"
            + env.getProperty("campus.redis.cache.port", "6379");
    require(
        !controlAddress.equalsIgnoreCase(cacheAddress),
        503,
        "REDIS_ISOLATION",
        "控制数据与缓存必须使用不同Redis实例，逻辑数据库不能隔离淘汰策略");
    require(
        env.getProperty("campus.redis.control.ssl", Boolean.class, true)
            && env.getProperty("campus.redis.cache.ssl", Boolean.class, true),
        503,
        "REDIS_TLS",
        "生产控制Redis和缓存Redis必须启用TLS");
    validateDatabaseUrl(env.getRequiredProperty("spring.datasource.url"));
    require(
        env.getRequiredProperty("campus.instance-id").matches("[a-zA-Z0-9_.-]{1,80}"),
        503,
        "INSTANCE_ID",
        "INSTANCE_ID格式不正确");
    for (String origin : env.getRequiredProperty("campus.allowed-origins").split(","))
      require(
          origin.matches("https://[a-zA-Z0-9.-]+(?::[0-9]+)?"),
          503,
          "PRODUCTION_ORIGIN",
          "生产来源必须为明确的HTTPS源");
    try (var connection = control.getConnection()) {
      Properties memory = connection.serverCommands().info("memory");
      require(
          memory != null && "noeviction".equals(memory.getProperty("maxmemory_policy")),
          503,
          "REDIS_POLICY",
          "控制Redis必须配置noeviction，且凭据必须允许读取INFO memory");
    }
  }

  static void validateDatabaseUrl(String url) {
    var parameters = new HashMap<String, List<String>>();
    int query = url.indexOf('?');
    try {
      require(
          url.startsWith("jdbc:mysql://"), 503, "DATABASE_CONFIGURATION", "生产DB_URL必须使用MySQL连接");
      int path = url.indexOf('/', "jdbc:mysql://".length());
      require(
          path >= 0
              && URLDecoder.decode(
                      url.substring("jdbc:mysql://".length(), path), StandardCharsets.UTF_8)
                  .matches("(?:[a-zA-Z0-9.-]+|\\[[0-9a-fA-F:]+\\])(?::[0-9]{1,5})?"),
          503,
          "DATABASE_CONFIGURATION",
          "生产DB_URL须使用单一数据库端点，禁止主机级参数覆盖全局TLS设置");
      if (query >= 0)
        for (String parameter : url.substring(query + 1).split("&")) {
          String[] pair = parameter.split("=", 2);
          String decodedName = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
          String name = decodedName.toLowerCase(Locale.ROOT);
          String canonical =
              Map.of(
                      "sslmode",
                      "sslMode",
                      "servertimezone",
                      "serverTimezone",
                      "connectiontimezone",
                      "connectionTimeZone")
                  .get(name);
          if (canonical != null) {
            require(
                decodedName.equals(canonical),
                503,
                "DATABASE_CONFIGURATION",
                "生产DB_URL关键参数大小写必须规范");
            parameters
                .computeIfAbsent(name, ignored -> new ArrayList<>())
                .add(pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "");
          }
        }
    } catch (IllegalArgumentException ex) {
      throw new com.campuseats.common.BusinessException(503, "DATABASE_CONFIGURATION", "生产DB_URL参数编码无效");
    }
    var ssl = parameters.getOrDefault("sslmode", List.of());
    // Connector/J can resolve repeated properties differently from a substring check.
    require(
        ssl.size() == 1 && ssl.getFirst().equals("VERIFY_IDENTITY"),
        503,
        "DATABASE_TLS",
        "生产DB_URL必须仅声明一次sslMode=VERIFY_IDENTITY并配置信任证书");
    var serverZone = parameters.getOrDefault("servertimezone", List.of());
    var connectionZone = parameters.getOrDefault("connectiontimezone", List.of());
    require(
        serverZone.size() <= 1
            && connectionZone.size() <= 1
            && !(serverZone.isEmpty() && connectionZone.isEmpty())
            && serverZone.stream().allMatch("Asia/Shanghai"::equals)
            && connectionZone.stream().allMatch("Asia/Shanghai"::equals),
        503,
        "DATABASE_TIME_ZONE",
        "生产DB_URL必须明确且一致地声明Asia/Shanghai连接时区");
  }
}
