FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
ARG MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public
COPY deploy/maven-settings.xml /workspace/maven-settings.xml
COPY pom.xml .
COPY campuseats-common/pom.xml campuseats-common/pom.xml
COPY campuseats-api/pom.xml campuseats-api/pom.xml
COPY campuseats-server/pom.xml campuseats-server/pom.xml
COPY campuseats-common/src campuseats-common/src
COPY campuseats-api/src campuseats-api/src
COPY campuseats-server/src/main campuseats-server/src/main
RUN --mount=type=cache,target=/root/.m2 \
    mvn -s /workspace/maven-settings.xml -Dmaven.mirror.url=$MAVEN_MIRROR_URL -U -B -ntp \
      -Dmaven.test.skip=true -Dmaven.wagon.http.retryHandler.count=3 package
RUN cd /workspace/campuseats-server/target && test -s bom.json && \
    sha256sum campuseats-server-1.0.1.jar bom.json | \
    sed 's/campuseats-server-1.0.1.jar/app.jar/; s/bom.json/sbom.cdx.json/' > runtime-manifest.sha256

FROM eclipse-temurin:21.0.13_9-jre-alpine-3.24@sha256:82cf3d9ccbe1ed46252008d51b2f2329e6b9e8a2f4394c4bebefc3ea7f4b1372 AS runtime
ARG RUNTIME_REFRESH=initial
ARG SOURCE_REVISION=unknown
LABEL org.opencontainers.image.revision=$SOURCE_REVISION
ENV APP_REVISION=$SOURCE_REVISION
# ImageIO/fixed-width XLSX and the bundled JDK truststore need neither fontconfig nor build-time GPG tools.
RUN test -n "$RUNTIME_REFRESH" && apk del --no-cache fontconfig ttf-dejavu gnupg p11-kit-trust && \
    for attempt in 1 2 3; do \
      apk upgrade --no-cache && apk add --no-cache curl findutils && exit 0; \
      [ "$attempt" = 3 ] && exit 1; \
      sleep 2; \
    done
WORKDIR /app
RUN mkdir -p /app/uploads && chown -R 10001:10001 /app
COPY --from=build --chown=10001:10001 /workspace/campuseats-server/target/campuseats-server-1.0.1.jar /app/app.jar
COPY --from=build --chown=10001:10001 /workspace/campuseats-server/target/bom.json /app/sbom.cdx.json
COPY --from=build --chown=10001:10001 /workspace/campuseats-server/target/runtime-manifest.sha256 /app/runtime-manifest.sha256
COPY LICENSE /app/LICENSE
USER 10001:10001
ENV TZ=Asia/Shanghai
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=65","-jar","/app/app.jar"]
