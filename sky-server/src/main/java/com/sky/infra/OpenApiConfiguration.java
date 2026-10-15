package com.sky.infra;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.security.*;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfiguration {
  @Bean
  OpenAPI api() {
    return new OpenAPI()
        .info(
            new Info()
                .title("CampusEats Platform")
                .version("2.0")
                .description("多校区独立店铺平台，单体单实例；支付与退款均为模拟"))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearer",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
  }

  @Bean
  OpenApiCustomizer envelopes() {
    return api -> {
      api.getComponents()
          .getSchemas()
          .forEach(
              (name, schema) -> {
                if (schema.getProperties() == null) return;
                schema
                    .getProperties()
                    .forEach(
                        (rawField, rawProperty) -> {
                          String field = rawField.toString();
                          Schema<?> property = (Schema<?>) rawProperty;
                          if ("int64".equals(property.getFormat()))
                            schema.addProperty(field, new StringSchema().pattern("^[0-9]+$"));
                          if (java.util.List.of("price", "amount", "deliveryFee", "packagingFee")
                              .contains(field))
                            schema.addProperty(field, new StringSchema().example("12.00"));
                        });
              });
      api.getPaths()
          .forEach(
              (path, item) ->
                  item.readOperations()
                      .forEach(
                          op -> {
                            if (op.getParameters() != null)
                              for (var parameter : op.getParameters()) {
                                var schema = parameter.getSchema();
                                if (schema != null && "int64".equals(schema.getFormat()))
                                  parameter.setSchema(new StringSchema().pattern("^[0-9]+$"));
                              }
                            if (!(path.endsWith("/auth/login")
                                || path.endsWith("/auth/demo")
                                || path.endsWith("/auth/wechat")
                                || path.startsWith("/api/v1/user/menu")
                                || path.equals("/api/v1/user/shop")
                                || path.equals("/api/v1/user/buildings")
                                || path.equals("/api/v1/user/campuses")
                                || path.equals("/api/v1/user/stores")
                                || path.equals("/api/v1/user/search")
                                || path.matches("/api/v1/user/campuses/[^/]+/buildings")
                                || path.matches(
                                    "/api/v1/user/stores/[^/]+/(products|categories|reviews)")))
                              op.addSecurityItem(new SecurityRequirement().addList("bearer"));
                            op.getResponses()
                                .forEach(
                                    (status, response) -> {
                                      if (!status.startsWith("2") || response.getContent() == null)
                                        return;
                                      response
                                          .getContent()
                                          .forEach(
                                              (media, content) -> {
                                                if (!media.equals("application/json")
                                                    && !media.equals("*/*")) return;
                                                Schema<?> original = content.getSchema();
                                                var schema = new ObjectSchema();
                                                schema.addProperty(
                                                    "code", new StringSchema().example("OK"));
                                                schema.addProperty("message", new StringSchema());
                                                schema.addProperty(
                                                    "data",
                                                    original == null
                                                        ? new ObjectSchema()
                                                        : original);
                                                schema.addProperty(
                                                    "requestId", new StringSchema().format("uuid"));
                                                schema.setRequired(
                                                    java.util.List.of(
                                                        "code", "message", "data", "requestId"));
                                                content.setSchema(schema);
                                              });
                                    });
                          }));
    };
  }
}
