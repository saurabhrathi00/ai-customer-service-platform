# CLAUDE.md — auth-service

## What This Service Does
Identity provider for the AI Customer Service Platform. Issues and refreshes
JWTs for business signin and issues short-lived service tokens for
service-to-service calls.

- Business signin (email/password) → access token + refresh token
- Refresh access token from refresh token
- Service-to-service token issuance with audience + scope policy
- Token validation is **not** done here — each downstream service verifies the
  JWT itself using the shared HS512 secret

**MVP data model:** this service does NOT own any tables. It reads the
`business.businesses` table that **user-business-service** owns and writes
during registration. Each business row carries `email + password_hash + id`
which is exactly what signin needs. When multi-user-per-business lands, a
separate `business_users` table will be introduced — signin will then read
from that and put the user's `business_id` into the JWT (same claim, different
source).

- Registration is in user-business-service (`POST /api/v1/business/register`),
  NOT here.
- There is no signup endpoint here.

---

## Tech Stack
- Java 17, Spring Boot
- PostgreSQL via Supabase
- Lombok for boilerplate reduction
- ULID for IDs (de.huxhorn.sulky.ulid)
- jjwt 0.13.x for JWT (HS512)
- HikariCP for connection pooling
- BCryptPasswordEncoder(10) for passwords

---

## Package Structure
```
com.aiassistant.auth/
├── controllers/
│   ├── AuthenticationController.java   → /api/v1/auth/**
│   └── InternalAuthController.java     → /api/internal/token
├── services/
│   └── AuthenticationService.java
├── repository/
│   └── BusinessAuthRepository.java     → reads business.businesses
├── security/token/
│   ├── TokenProvider.java
│   ├── JwtTokenProvider.java           → HS512
│   └── TokenPrincipal.java
├── models/
│   ├── dao/
│   │   └── BusinessAuthEntity.java     → read-only mapping of business.businesses
│   ├── request/
│   │   ├── SigninRequest.java
│   │   ├── RefreshTokenRequest.java
│   │   └── ServiceTokenRequest.java
│   ├── response/
│   │   ├── AuthenticationResponse.java
│   │   ├── RefreshTokenResponse.java
│   │   └── ServiceTokenResponse.java
│   └── error/
│       └── ApiError.java
├── configuration/
│   ├── SecretsConfiguration.java
│   ├── ServiceConfiguration.java
│   ├── DataSourceConfig.java
│   └── SecurityConfig.java
└── exceptions/
    ├── AppException.java
    ├── AuthFailedException.java
    ├── ConflictException.java
    └── GlobalExceptionHandler.java
```

---

## Strict Conventions — Follow These Exactly

### IDs
- ULID (NOT UUID, NOT Long auto-increment) — `new ULID().nextULID()` in `@PrePersist`
- ID field type is always `String`

### Config Pattern
- Non-sensitive config → `configs/service.properties` → `@ConfigurationProperties(prefix = "configs")` → `ServiceConfiguration.java`
- Sensitive config → `secrets/secrets.properties` → `@ConfigurationProperties(prefix = "secrets")` → `SecretsConfiguration.java`
- NEVER hardcode credentials, JWT secrets, or URLs in code or `application.properties`

### DTOs vs Entities
- NEVER return `@Entity` directly from a controller — always convert to a response DTO
- Request DTOs in `models/request/`, response DTOs in `models/response/`
- Response DTOs: `@Value @Builder @Jacksonized` (immutable)
- Request DTOs: `@Data` with validation annotations (some legacy ones use plain getters/setters — new ones should use `@Data`)

### Lombok Usage
- Entities: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`
- Request DTOs: `@Data`
- Response DTOs: `@Value @Builder @Jacksonized`
- Services/Controllers: `@RequiredArgsConstructor` (constructor injection)
- Config classes: `@Data + @Configuration + @ConfigurationProperties`

### API Versioning
- Public endpoints: `/api/v1/auth/...`
- Internal service-to-service endpoints: `/api/internal/...`

### Exception Handling
- Throw custom exceptions from services (`AuthFailedException`, `ConflictException`, `RoleNotFoundException`)
- `GlobalExceptionHandler` returns `ApiError { status, message }`
- Never throw generic `RuntimeException`

### Security
- This service's own endpoints are `permitAll()` — clients posting credentials don't have a JWT yet
- `BCryptPasswordEncoder(10)` for password hashing
- JWT signing uses HS512 with `secrets.jwt.secret`. **This secret is shared with every downstream service that verifies tokens** — rotation requires coordinated deploy

### Logging
- SLF4J: `private static final Logger log = LoggerFactory.getLogger(ClassName.class);`
- Log key auth events: signin (success/failure), token refresh, service-token issuance
- NEVER log raw tokens or passwords

### Transactions
- Service class: `@Transactional(readOnly = true)` at class level
- Write methods: `@Transactional` at method level

---

## Database Access

This service owns NO tables. It connects to the same Postgres as
user-business-service and reads `business.businesses` (the table
user-business-service writes during registration). The connection uses
`spring.jpa.hibernate.ddl-auto=validate` so it can never create/alter that
table.

### business.businesses (read-only here)
Only the columns this service needs:
```
id            VARCHAR (ULID, PK)
email         VARCHAR NOT NULL UNIQUE
password_hash VARCHAR NOT NULL
is_active     BOOLEAN
```

### Role catalog (no DB rows — hardcoded for MVP)
For MVP every signin produces a token with the same role+scopes:

- role: `ROLE_BUSINESS_ADMIN`
- scopes: `business.read`, `business.write`

When multi-user-per-business is added, roles will move into a real table and
this hardcoded list goes away.

---

## Role Catalog (MVP)

| Role | Granted Scopes |
|---|---|
| `ROLE_BUSINESS_ADMIN` | `business.read`, `business.write` |
| `ROLE_BUSINESS_VIEWER` | `business.read` |
| `ROLE_PLATFORM_ADMIN` | `business.read`, `business.write`, `admin.*` |

Service-to-service callers do NOT use these roles. They authenticate via
`clientId/clientSecret` and receive a token with scopes filtered by the
audience-specific policy in `configs/service.properties`.

---

## JWT Claims Contract (issued by this service, verified by everyone else)

```json
{
  "sub": "user@business.com",            // username (email)
  "uid": "<users.id ULID>",              // auth-service user id
  "businessId": "<businesses.id ULID>",  // the business this user belongs to
  "roles":  ["ROLE_BUSINESS_ADMIN"],     // list of role names
  "scopes": ["business.read",            // flat list of scopes derived from roles
             "business.write"],
  "iat": 1747576800,
  "exp": 1747577700
}
```

Service tokens (`/api/internal/token`) use a different shape:

```json
{
  "sub": "<clientId>",                   // calling service id, e.g. "incoming-call-service"
  "aud": "user-business-service",        // target service
  "scope": ["business.internal.read"],   // singular "scope" claim, list of strings
  "iat": ...,
  "exp": ...
}
```

Downstream filters treat both `scopes` and `scope` as authority sources (each
maps to a `SCOPE_<name>` granted authority).

Signing algorithm: **HS512**. Secret: `secrets.jwt.secret` — must match every
service that verifies tokens. Token type prefix: `Bearer`.

Token lifetimes (from `secrets.properties`):
- `secrets.jwt.access-token-expiration` (default 15m)
- `secrets.jwt.refresh-token-expiration` (default 7d)
- `secrets.jwt.service-token-expiration` (default 15m)

---

## Endpoints

### Public — `/api/v1/auth/`
| Method | Path | Description |
|---|---|---|
| POST | `/signin` | Email + password → access + refresh tokens |
| POST | `/refresh` | Refresh token → new access token |
| POST | `/logout` | (planned) Invalidate refresh token |

> Signup / business registration lives in **user-business-service**
> (`POST /api/v1/business/register`). There is no signup endpoint here.

### Internal — `/api/internal/`
| Method | Path | Description |
|---|---|---|
| POST | `/token` | `clientId + clientSecret + audience + scopes` → service token |

> Note: there is intentionally **no** `/auth/validate` endpoint. Token
> verification is performed locally by each downstream service using the shared
> JWT secret (HS512). Centralised verification would create a hot dependency on
> auth-service in every request path.

---

## Service-to-Service Policy

`configs/service.properties` defines which calling services may obtain which
scopes against which audiences:

```properties
configs.auth.enabled=true
configs.auth.policy.services.<audience>.scopes=<comma,separated,scopes>
```

`ServiceTokenRequest`:
```json
{
  "clientId": "incoming-call-service",
  "clientSecret": "...",            // matched against secrets.services.<id>.password
  "audience": "user-business-service",
  "scopes": ["business.internal.read"]
}
```

`AuthenticationService.generateServiceToken` validates the client credentials,
asserts the audience exists in policy, asserts the requested scopes are a
subset of the policy's allowed scopes, then issues a JWT.

---

## How Downstream Services Verify Tokens

This is the contract every downstream service follows (already implemented in
user-business-service — use it as the reference):

1. Read `Authorization: Bearer <jwt>` header.
2. Verify signature with the shared HS512 secret.
3. Parse claims; build a Spring `Authentication` with:
   - `principal` = a typed object holding `sub`, `uid`, `businessId`, `audience`, `roles`, `scopes`
   - `authorities` = each role name as-is + `SCOPE_<scope>` for every scope
4. Enforce method-level `@PreAuthorize`:
   - `hasAuthority('SCOPE_business.read')` etc.
   - tenant isolation guard: `@businessAccessGuard.canAccess(#id)` — true iff `{id}` matches `businessId` claim OR caller has `ROLE_PLATFORM_ADMIN`.
5. Service-token routes (`/api/internal/**`) require the audience-specific scope (e.g. `SCOPE_business.internal.read`).

This service does NOT call back into downstream services to validate anything —
the secret + signature is enough.

---

## What's Already Created (Do Not Recreate)
- `AuthServiceApplication.java` (main class)
- `SecretsConfiguration.java`, `ServiceConfiguration.java`, `DataSourceConfig.java`, `SecurityConfig.java`
- `JwtTokenProvider.java`, `TokenProvider.java`, `TokenPrincipal.java`
- `UserEntity.java`, `RoleEntity.java`
- `UserRepository.java`, `RoleRepository.java`
- `AuthenticationService.java` (signIn, refreshToken, generateServiceToken)
- `AuthenticationController.java`, `InternalAuthController.java`
- Request/response DTOs for signin, refresh, service-token
- `GlobalExceptionHandler.java`, `ApiError.java`, custom exceptions
- `RoleEnum.java`

---

## Known Gaps to Close

1. **No refresh-token revocation / store.** `refreshToken` only validates the
   token's signature and expiry — there's no allowlist/denylist. Acceptable
   for MVP; revisit before production.
2. **No `aud` enforcement on user JWTs.** Service tokens carry `aud`, but user
   tokens currently don't.
3. **MVP roles are hardcoded.** Multi-user-per-business will require a real
   roles table (probably owned by user-business-service or a new users
   service) and reading the user's role at signin time.
4. **`SecurityConfig.permitAll()` is intentional here** (no JWT exists at
   signin time). Don't copy that into downstream services.

---

## Reference Pattern
When in doubt about a design choice, mirror user-business-service:
- Entity design → `BusinessEntity.java`
- Response DTOs → `BusinessResponse.java` (`@Value @Builder @Jacksonized`)
- Service pattern → `BusinessService.java` (class-level `@Transactional(readOnly=true)`, method-level `@Transactional` on writes)
- Controller pattern → `BusinessController.java` (`@RequiredArgsConstructor`, `ResponseEntity` return)
- Config pattern → `SecretsConfiguration.java` + `ServiceConfiguration.java`
- Security/JWT verification pattern → `security/JwtAuthenticationFilter.java` + `security/BusinessAccessGuard.java`
