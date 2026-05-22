# ---------- Build stage ----------
FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /build

# Copy only pom first (better caching)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests -B


# ---------- Runtime stage ----------
FROM eclipse-temurin:17-jre

LABEL maintainer="saurabhrathi00"
LABEL service="auth-service"

# curl is needed for the Docker HEALTHCHECK below
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN groupadd appgroup && useradd -m -g appgroup appuser

WORKDIR /app

# Create logs directory with appropriate permissions
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app/logs

# Bake non-sensitive config into the image. Secrets are NOT copied — they are
# mounted at runtime at /etc/secrets/secrets.properties (see docker-compose / host).
COPY configs/ /app/configs/

# Copy jar from build stage
COPY --from=build /build/target/auth-service-*.jar app.jar

EXPOSE 8081

# Spring picks up baked configs from /app/configs and runtime secrets from /etc/secrets.
ENV SPRING_CONFIG_ADDITIONAL_LOCATION=file:/app/configs/,file:/etc/secrets/

USER appuser

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -fsS http://localhost:8081/auth-service/api/v1/health || exit 1

ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
