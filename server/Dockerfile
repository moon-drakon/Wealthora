FROM eclipse-temurin:25-jdk-noble AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw \
    && ./mvnw --batch-mode --no-transfer-progress dependency:go-offline

COPY src/main/ src/main/
RUN ./mvnw --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:25-jre-noble AS runtime

WORKDIR /app

COPY --from=build --chown=10001:10001 \
    /workspace/target/wealthora-auth-server-1.0.0-SNAPSHOT.jar \
    /app/wealthora-server.jar

ENV SPRING_PROFILES_ACTIVE=prod

USER 10001:10001
EXPOSE 10000
STOPSIGNAL SIGTERM

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/wealthora-server.jar"]
