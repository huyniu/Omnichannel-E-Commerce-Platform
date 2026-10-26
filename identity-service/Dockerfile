# syntax=docker/dockerfile:1.7
# Build context: repository root (multi-module Maven reactor).
#   docker build -f identity-service/Dockerfile -t omni-identity-service .

# ---------- Stage 1: build ----------
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /src
# Module POMs first so the dependency layer is cached until a POM changes.
COPY pom.xml .
COPY common/pom.xml common/
COPY proto/pom.xml proto/
COPY api-gateway/pom.xml api-gateway/
COPY identity-service/pom.xml identity-service/
COPY product-service/pom.xml product-service/
COPY order-service/pom.xml order-service/
COPY payment-service/pom.xml payment-service/
COPY notification-service/pom.xml notification-service/
COPY common/src common/src
COPY proto/src proto/src
COPY identity-service/src identity-service/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl identity-service -am -DskipTests package

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /src/identity-service/target/app.jar app.jar
USER app
EXPOSE 8081
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5   CMD wget -qO- http://localhost:8081/actuator/health/readiness || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
