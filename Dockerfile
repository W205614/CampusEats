FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
ARG MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public
COPY deploy/maven-settings.xml /workspace/maven-settings.xml
COPY pom.xml .
COPY sky-common/pom.xml sky-common/pom.xml
COPY sky-pojo/pom.xml sky-pojo/pom.xml
COPY sky-server/pom.xml sky-server/pom.xml
COPY sky-common/src sky-common/src
COPY sky-pojo/src sky-pojo/src
COPY sky-server/src/main sky-server/src/main
RUN --mount=type=cache,target=/root/.m2 mvn -s /workspace/maven-settings.xml -Dmaven.mirror.url=$MAVEN_MIRROR_URL -U -B -ntp -Dmaven.test.skip=true -Dmaven.wagon.http.retryHandler.count=3 package
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
RUN mkdir -p /app/uploads && chown -R 10001:10001 /app
COPY --from=build --chown=10001:10001 /workspace/sky-server/target/sky-server-1.0-SNAPSHOT.jar /app/app.jar
USER 10001:10001
ENV TZ=Asia/Shanghai
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=65","-jar","/app/app.jar"]
