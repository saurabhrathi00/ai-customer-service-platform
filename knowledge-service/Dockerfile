# ---------- Build stage ----------
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

# ---------- Runtime stage ----------
FROM eclipse-temurin:17-jre
LABEL maintainer="aiassistant"

RUN groupadd appgroup && useradd -m -g appgroup appuser
WORKDIR /app
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app/logs

COPY --from=build /build/target/knowledge-service-*.jar app.jar

EXPOSE 8083

ENV SPRING_CONFIG_LOCATION=file:/etc/service.properties,file:/etc/secrets.properties

USER appuser
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "/app/app.jar"]
