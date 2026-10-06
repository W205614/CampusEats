FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p /app/uploads && chown -R 10001:10001 /app
COPY --chown=10001:10001 sky-server/target/sky-server-1.0-SNAPSHOT.jar /app/app.jar
USER 10001:10001
ENV SPRING_PROFILES_ACTIVE=docker TZ=Asia/Shanghai
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]
