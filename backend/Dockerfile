# syntax=docker/dockerfile:1
# StockSmart backend — multi-stage build (Java 17 / Spring Boot 3.2 / Maven).
# Stage 1 resolves dependencies and packages the boot jar; stage 2 is a slim,
# non-root runtime with a health check pegged to the public /api/v1/health probe.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp package -DskipTests

FROM eclipse-temurin:17-jre
# curl powers the container health check; the runtime user is non-root.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home --uid 1001 stocksmart

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN chown -R stocksmart:stocksmart /app

USER stocksmart
EXPOSE 8080

# Container-aware JVM defaults; override with JAVA_OPTS at run time.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS http://localhost:8080/api/v1/health || exit 1

# exec form keeps java as PID 1 so SIGTERM reaches Spring's graceful shutdown.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
