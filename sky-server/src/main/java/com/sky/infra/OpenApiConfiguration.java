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
        .info(new Info().title("CampusEats v1").version("1.0").description("单校区点餐演示；所有支付退款均为模拟"))
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
                            if (!(path.endsWith("/auth/login")
                                || path.endsWith("/auth/demo")
                                || path.endsWith("/auth/wechat")
                                || path.startsWith("/api/v1/user/menu")
                                || path.equals("/api/v1/user/shop")
                                || path.equals("/api/v1/user/buildings")))
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
