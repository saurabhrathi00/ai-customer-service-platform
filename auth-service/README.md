# auth-service
Auth Service is a Spring Boot microservice responsible for authentication and user management. This guide explains how to run it locally using IntelliJ and in Docker containers.
## Features

- JWT-based authentication
- PostgreSQL database
- External configuration & secrets management
- Can run locally via IntelliJ or as a Docker container

## Prerequisites

- Java 17
- Maven
- Docker (if running via container)
- PostgreSQL

This service uses **external configuration files**:
- `service.properties` → general configuration
- `secrets.properties` → secrets like DB credentials and JWT keys

Both files can be loaded via environment variable:

```bash
SPRING_CONFIG_LOCATION=/path/to/service.properties,/path/to/secrets.properties

configs.dbUrl=jdbc:postgresql://<POSTGRES_HOST>:5432/<DB_NAME>

# JWT Configuration
secrets.jwt.secret=<your_jwt_secret>
secrets.jwt.access-token-expiration=15m
secrets.jwt.refresh-token-expiration=7d
secrets.jwt.type=Bearer

# Database Configuration
secrets.datasource.username=<db_user>
secrets.datasource.password=<db_password>
secrets.datasource.driver-class-name=org.postgresql.Driver
```

## Running Locally (IntelliJ)
Clone the repo:
```bash
git clone https://github.com/saurabhrathi00/auth-service.git
cd auth-service
```
Set environment variables:
```bash
export SPRING_CONFIG_LOCATION=/absolute/path/to/service.properties,/absolute/path/to/secrets.properties
```

##Running with Docker
Build the Docker image:
```bash
docker build -t auth-service:latest .
docker run --name auth_service --network auth-service_app_network \
  -e SPRING_CONFIG_LOCATION=file:/etc/service.properties,file:/etc/secrets.properties \
  -p 8080:8080 \
  auth-service:latest
```
