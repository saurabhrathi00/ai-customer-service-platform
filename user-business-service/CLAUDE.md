# CLAUDE.md — user-business-service

## What This Service Does
Handles all business-related operations for the AI Customer Service Platform:
- Business registration and login credential management
- Business profile (name, category, description, location, operating hours)
- Phone number assignment per business (provider-agnostic: Exotel, EnableX, etc.)
- Interest rating configuration per business (scoring rules for call prioritisation)
- Internal endpoint for other services to look up a business by phone number

This is a B2C platform — businesses are our clients. Each business gets a unique
Business ID and one or more phone numbers. Customers call those numbers and are
served by AI loaded with ONLY that business's knowledge.

---

## Tech Stack
- Java 17, Spring Boot 3.5.6
- PostgreSQL via Supabase (managed Postgres)
- Lombok for boilerplate reduction
- ULID for IDs (de.huxhorn.sulky.ulid 8.2.0)
- jjwt 0.13.0 for JWT (if needed internally)
- HikariCP for connection pooling (via DataSourceConfig)
- BCryptPasswordEncoder(10) for passwords

---

## Package Structure
```
com.aiassistant.userbusiness/
├── controllers/          → @RestController classes
├── services/             → @Service classes (business logic)
├── repository/           → JpaRepository interfaces
├── models/
│   ├── dao/              → @Entity classes (DB tables)
│   ├── request/          → Incoming request DTOs
│   ├── response/         → Outgoing response DTOs
│   └── error/            → ApiError (already created)
├── configuration/        → @Configuration classes (already created)
│   ├── SecretsConfiguration.java
│   ├── ServiceConfiguration.java
│   ├── DataSourceConfig.java
│   └── SecurityConfig.java
├── exceptions/           → Custom exceptions + GlobalExceptionHandler (already created)
│   ├── AppException.java
│   ├── BusinessNotFoundException.java
│   ├── ConflictException.java
│   └── GlobalExceptionHandler.java
└── enums/
    └── RoleEnum.java     → ROLE_BUSINESS, ROLE_ADMIN
```

---

## Strict Conventions — Follow These Exactly

### IDs
- Always use ULID (NOT UUID, NOT Long, NOT auto-increment)
- Generate in @PrePersist: `this.id = new ULID().nextULID();`
- ID field type is always String

### Config Pattern
- Non-sensitive config → `configs/service.properties` → bound via `@ConfigurationProperties(prefix = "configs")` → `ServiceConfiguration.java`
- Sensitive config → `secrets/secrets.properties` → bound via `@ConfigurationProperties(prefix = "secrets")` → `SecretsConfiguration.java`
- NEVER hardcode credentials or URLs in code or application.properties

### DTOs vs Entities
- NEVER return @Entity directly from a controller
- Always convert: Entity → ResponseDTO before returning
- Request DTOs go in models/request/, Response DTOs go in models/response/
- Use Lombok @Value + @Builder + @Jacksonized for response DTOs (immutable)
- Use Lombok @Data for request DTOs (mutable, validation annotations)

### Lombok Usage
- Entities: @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
- Request DTOs: @Data
- Response DTOs: @Value @Builder @Jacksonized
- Services/Controllers: @RequiredArgsConstructor (for constructor injection)
- Config classes: @Data + @Configuration + @ConfigurationProperties

### API Versioning
- Public endpoints: /api/v1/business/...
- Internal service-to-service endpoints: /api/internal/...

### Exception Handling
- Throw custom exceptions from services (BusinessNotFoundException, ConflictException, etc.)
- GlobalExceptionHandler catches them and returns ApiError { status, message }
- Never throw generic RuntimeException — always create a named exception

### Security
- Defense-in-depth: JWT is validated at the API gateway AND re-validated inside this service via `JwtAuthenticationFilter` (registered before `UsernamePasswordAuthenticationFilter`)
- Public routes (no JWT required):
  - `GET /api/v1/health`
  - `POST /api/v1/business/register`
- All other `/api/v1/business/**` and `/api/internal/**` routes require an authenticated principal
- 401 → `ApiError("Authentication required")`, 403 → `ApiError("Access denied")` — handled by `SecurityConfig` entry point / access-denied handler
- `BCryptPasswordEncoder(10)` is configured in `SecurityConfig`
- Tenant isolation enforced via `BusinessAccessGuard` (a business can only act on its own `{id}`)

### Logging
- Use SLF4J: `private static final Logger log = LoggerFactory.getLogger(ClassName.class);`
- Log key operations: business registered, phone number assigned, profile updated
- Log level: INFO for business events, DEBUG for detail, ERROR for exceptions

### Transactions
- Service class: `@Transactional(readOnly = true)` at class level
- Write methods: `@Transactional` at method level to override

---

## Database Tables This Service Owns

### businesses
```
id              VARCHAR (ULID, PK)
name            VARCHAR NOT NULL
email           VARCHAR NOT NULL UNIQUE
password_hash   VARCHAR NOT NULL
category        VARCHAR
description     TEXT
location        VARCHAR
operating_hours VARCHAR
is_active       BOOLEAN DEFAULT true
created_at      TIMESTAMP DEFAULT NOW()
updated_at      TIMESTAMP
```

### telephony_providers
```
id              VARCHAR (ULID, PK)
name            VARCHAR NOT NULL
slug            VARCHAR NOT NULL UNIQUE
is_active       BOOLEAN DEFAULT true
created_at      TIMESTAMP DEFAULT NOW()
```

### provider_phone_numbers
```
id              VARCHAR (ULID, PK)
provider_id     VARCHAR NOT NULL (FK → telephony_providers.id)
phone_number    VARCHAR NOT NULL UNIQUE
status          VARCHAR NOT NULL DEFAULT 'available'  -- available | assigned | released
created_at      TIMESTAMP DEFAULT NOW()
```

### business_phone_numbers
```
id                        VARCHAR (ULID, PK)
business_id               VARCHAR NOT NULL (FK → businesses.id)
provider_phone_number_id  VARCHAR NOT NULL (FK → provider_phone_numbers.id)
label                     VARCHAR
created_at                TIMESTAMP DEFAULT NOW()
```

### rating_config
```
id              VARCHAR (ULID, PK)
business_id     VARCHAR NOT NULL (FK → businesses.id)
signal_key      VARCHAR NOT NULL   (e.g. LONG_CALL, POSITIVE_FEEDBACK)
score_value     INTEGER NOT NULL
updated_at      TIMESTAMP DEFAULT NOW()
```

---

## Endpoints to Build

### Public — /api/v1/business/
| Method | Path | Description |
|--------|------|-------------|
| POST | /register | Register new business |
| GET | /{id}/profile | Get business profile |
| PUT | /{id}/profile | Update business profile |
| GET | /{id}/phone-numbers | List all phone numbers for business |
| POST | /{id}/phone-numbers | Add a new phone number |
| DELETE | /{id}/phone-numbers/{numberId} | Remove a phone number |
| GET | /{id}/rating-config | Get interest rating config |
| PUT | /{id}/rating-config | Update interest rating config |

### Internal — /api/internal/
| Method | Path | Description |
|--------|------|-------------|
| GET | /business/lookup?phoneNumber=91xxx | Find businessId by phone number (called by call-orchestration-service) |
| GET | /business/{id}/exists | Check if a business exists (called by auth-service) |

---

## Default Rating Config (Insert on Business Registration)
When a new business registers, insert these default rating_config rows:

| signal_key | score_value |
|---|---|
| LONG_CALL | 2 |
| POSITIVE_FEEDBACK | 2 |
| CALLBACK_REQUESTED | 3 |
| NEGATIVE_FEEDBACK | -1 |
| SHORT_CALL | -2 |
| AI_COULD_NOT_ANSWER | 1 |

---

## What's Already Created (Do Not Recreate)
- UserBusinessServiceApplication.java (main class)
- HealthController.java (/api/v1/health)
- SecretsConfiguration.java
- ServiceConfiguration.java
- DataSourceConfig.java
- SecurityConfig.java
- ApiError.java
- AppException.java
- BusinessNotFoundException.java
- ConflictException.java
- GlobalExceptionHandler.java
- RoleEnum.java

---

## Reference Pattern
This service follows the exact same design as the auth-service in the same codebase.
When in doubt about any pattern, follow what auth-service does.
Key reference files to mirror:
- Entity design → UserEntity.java
- Response DTOs → AuthenticationResponse.java (@Value @Builder @Jacksonized)
- Service pattern → AuthenticationService.java (@Transactional(readOnly=true) at class, @Transactional at write methods)
- Controller pattern → AuthenticationController.java (@RequiredArgsConstructor, ResponseEntity return type)
- Config pattern → SecretsConfiguration.java + ServiceConfiguration.java
