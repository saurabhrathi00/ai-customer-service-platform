# ---------- Build stage ----------
FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /build

# Copy only pom first (better caching)
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests


# ---------- Runtime stage ----------
FROM eclipse-temurin:17-jre

LABEL maintainer="saurabhrathi00"

# Create non-root user
RUN groupadd appgroup && useradd -m -g appgroup appuser

WORKDIR /app

# Create logs directory with appropriate permissions
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app/logs

# Copy jar from build stage
COPY --from=build /build/target/auth-service-*.jar app.jar

EXPOSE 8080

ENV SPRING_CONFIG_LOCATION=file:/etc/service.properties,file:/etc/secrets.properties

USER appuser

ENTRYPOINT ["java","-XX:+UseContainerSupport","-jar","/app/app.jar"]
