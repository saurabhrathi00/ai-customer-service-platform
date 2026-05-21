# user-business-service

Business management microservice for the AI Customer Service Platform. Handles business registration, profiles, Twilio phone number assignment, and interest-rating configuration. Other services call it to resolve a Twilio number back to a `businessId`.

---

## Overview

This is a B2C platform — businesses are our clients. Each business gets a unique Business ID and one or more Twilio numbers. When a customer calls a Twilio number, the incoming-call-service looks up the owning business through this service and loads the AI with only that business's knowledge.

### Responsibilities
- Business registration and login credential storage
- Business profile (name, category, description, location, operating hours)
- Twilio phone number assignment per business
- Interest-rating configuration per business (scoring rules that drive call prioritisation)
- Internal lookup endpoint: Twilio number → business

---

## Tech Stack

| Component | Version / Notes |
|---|---|
| Java | 17 |
| Spring Boot | 3.5.6 |
| Database | PostgreSQL (Supabase managed) |
| Connection Pool | HikariCP (via `DataSourceConfig`) |
| ID Generation | ULID (`de.huxhorn.sulky.ulid` 8.2.0) |
| Password Hashing | BCrypt (strength 10) |
| JWT | jjwt 0.13.0 (validation at gateway, not here) |
| Boilerplate | Lombok |

---

## Architecture & Conventions

### IDs
- **Always ULID**, never UUID / Long / auto-increment
- Generated in `@PrePersist`: `this.id = new ULID().nextULID();`
- Field type is `String`

### Configuration
| Type | File | Class | Prefix |
|---|---|---|---|
| Non-sensitive | `configs/service.properties` | `ServiceConfiguration` | `configs` |
| Sensitive | `secrets/secrets.properties` | `SecretsConfiguration` | `secrets` |

Never hardcode credentials or URLs.

### DTOs vs Entities
- Never return `@Entity` directly from a controller
- Always convert Entity → Response DTO
- Request DTOs → `models/request/`, Response DTOs → `models/response/`

### Lombok Conventions
| Layer | Annotations |
|---|---|
| Entity | `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` |
| Request DTO | `@Data` |
| Response DTO | `@Value @Builder @Jacksonized` (immutable) |
| Service / Controller | `@RequiredArgsConstructor` (constructor injection) |
| Config class | `@Data @Configuration @ConfigurationProperties` |

### API Versioning
- Public: `/api/v1/business/...`
- Internal (service-to-service): `/api/internal/...`

### Exceptions
- Throw named custom exceptions from services (`BusinessNotFoundException`, `ConflictException`, …)
- `GlobalExceptionHandler` maps them to `ApiError { status, message }`
- Never throw raw `RuntimeException`

### Security
- All routes are `permitAll()` — JWT is validated at the API gateway, not in this service
- `BCryptPasswordEncoder(10)` is configured in `SecurityConfig`

### Transactions
- Class-level: `@Transactional(readOnly = true)`
- Write methods: `@Transactional` override at method level

### Logging
- SLF4J: `private static final Logger log = LoggerFactory.getLogger(ClassName.class);`
- INFO for business events (registration, number assignment, profile update)
- DEBUG for detail, ERROR for exceptions

---

## Package Structure

```
com.aiassistant.userbusiness/
├── controllers/          @RestController classes
├── services/             @Service classes (business logic)
├── repository/           JpaRepository interfaces
├── models/
│   ├── dao/              @Entity classes
│   ├── request/          Incoming request DTOs
│   ├── response/         Outgoing response DTOs
│   └── error/            ApiError
├── configuration/        SecretsConfiguration, ServiceConfiguration, DataSourceConfig, SecurityConfig
├── exceptions/           AppException, BusinessNotFoundException, ConflictException, GlobalExceptionHandler
└── enums/                RoleEnum (ROLE_BUSINESS, ROLE_ADMIN)
```

---

## Database Schema

### `businesses`
| Column | Type | Notes |
|---|---|---|
| id | VARCHAR | ULID, PK |
| name | VARCHAR | NOT NULL |
| email | VARCHAR | NOT NULL, UNIQUE |
| password_hash | VARCHAR | NOT NULL |
| category | VARCHAR | |
| description | TEXT | |
| location | VARCHAR | |
| operating_hours | VARCHAR | |
| is_active | BOOLEAN | DEFAULT true |
| created_at | TIMESTAMP | DEFAULT NOW() |
| updated_at | TIMESTAMP | |

### `business_phone_numbers`
| Column | Type | Notes |
|---|---|---|
| id | VARCHAR | ULID, PK |
| business_id | VARCHAR | FK → businesses.id |
| twilio_number | VARCHAR | NOT NULL, UNIQUE |
| label | VARCHAR | |
| is_active | BOOLEAN | DEFAULT true |
| created_at | TIMESTAMP | DEFAULT NOW() |

### `rating_config`
| Column | Type | Notes |
|---|---|---|
| id | VARCHAR | ULID, PK |
| business_id | VARCHAR | FK → businesses.id |
| signal_key | VARCHAR | e.g. `LONG_CALL`, `POSITIVE_FEEDBACK` |
| score_value | INTEGER | NOT NULL |
| updated_at | TIMESTAMP | DEFAULT NOW() |

---

## API Endpoints

### Public — `/api/v1/business/`
| Method | Path | Description |
|---|---|---|
| POST | `/register` | Register a new business |
| GET | `/{id}/profile` | Get business profile |
| PUT | `/{id}/profile` | Update business profile |
| GET | `/{id}/phone-numbers` | List Twilio numbers for a business |
| POST | `/{id}/phone-numbers` | Add a new Twilio number |
| DELETE | `/{id}/phone-numbers/{numberId}` | Remove a phone number |
| GET | `/{id}/rating-config` | Get interest-rating config |
| PUT | `/{id}/rating-config` | Update interest-rating config |

### Internal — `/api/internal/`
| Method | Path | Description |
|---|---|---|
| GET | `/business/lookup?twilioNumber=+1xxx` | Find `businessId` by Twilio number (incoming-call-service) |
| GET | `/business/{id}/exists` | Check business existence (auth-service) |

### Health
| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/health` | Liveness probe |

---

## Default Rating Config

Inserted automatically on business registration:

| signal_key | score_value |
|---|---|
| LONG_CALL | 2 |
| POSITIVE_FEEDBACK | 2 |
| CALLBACK_REQUESTED | 3 |
| NEGATIVE_FEEDBACK | -1 |
| SHORT_CALL | -2 |
| AI_COULD_NOT_ANSWER | 1 |

---

## Running Locally

### Prerequisites
- Java 17
- Maven 3.9+
- Access to the Supabase Postgres instance (credentials in `secrets/secrets.properties`)

### Configuration
Populate the two property files (not committed):

`configs/service.properties`
```
configs.someKey=value
```

`secrets/secrets.properties`
```
secrets.db.url=jdbc:postgresql://...
secrets.db.username=...
secrets.db.password=...
```

### Build & Run
```bash
mvn clean install
mvn spring-boot:run
```

### Docker
```bash
docker build -t user-business-service .
docker run -p 8080:8080 user-business-service
```

---

## Reference

This service mirrors the design of `auth-service` in the same codebase. When in doubt about a pattern, look there first:

| Concern | Reference file |
|---|---|
| Entity design | `UserEntity.java` |
| Response DTO | `AuthenticationResponse.java` |
| Service pattern | `AuthenticationService.java` |
| Controller pattern | `AuthenticationController.java` |
| Config pattern | `SecretsConfiguration.java`, `ServiceConfiguration.java` |