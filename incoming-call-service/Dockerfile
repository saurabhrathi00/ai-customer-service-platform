# ---------- Build stage ----------
FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src

RUN mvn clean package -DskipTests -B


# ---------- Runtime stage ----------
FROM eclipse-temurin:17-jre

LABEL maintainer="aiassistant"
LABEL service="incoming-call-service"

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd appgroup && useradd -m -g appgroup appuser

WORKDIR /app

RUN mkdir -p /app/logs && chown -R appuser:appgroup /app/logs

# Bake non-sensitive config; secrets are mounted at /etc/secrets at runtime.
COPY configs/ /app/configs/

COPY --from=build /build/target/incoming-call-service-*.jar app.jar

EXPOSE 8085

ENV SPRING_CONFIG_ADDITIONAL_LOCATION=file:/app/configs/,file:/etc/secrets/

USER appuser

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -fsS http://localhost:8085/incoming-call-service/api/v1/health || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
