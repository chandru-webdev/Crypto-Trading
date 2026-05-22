# Backend — Crypto Trading Simulator

Spring Boot 3.3 service exposing the auth + (later) trading APIs. See the [root README](../README.md) for the full project summary.

---

## Prerequisites

- **JDK 21+** (tested with Adoptium JDK 25)
- **MySQL 8** on `localhost:3306` — database `crypto_simulator` (auto-created)
- **Redis 7** on `localhost:6379` — on Windows use [Memurai](https://www.memurai.com/get-memurai)
- **Maven 3.9+** on PATH (or use the bundled `mvnw.cmd` / `run.bat`)

---

## Run

```powershell
mvn spring-boot:run
```

Or without a global Maven install (uses the bundled Maven Wrapper):

```powershell
.\run.bat spring-boot:run
```

The app starts on `http://localhost:8080`.

---

## Common Maven commands

| Command | What it does |
|---|---|
| `mvn clean compile` | compile only |
| `mvn spring-boot:run` | start the app in dev mode |
| `mvn test` | run unit tests (when added) |
| `mvn clean package` | produce `target/crypto-trading-simulator-0.0.1-SNAPSHOT.jar` |
| `java -jar target/*.jar` | run the packaged JAR |

---

## Configuration

All settings live in [`src/main/resources/application.yml`](src/main/resources/application.yml) and can be overridden with environment variables.

| Variable | Default | Used for |
|---|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `3306` / `crypto_simulator` | MySQL |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / `root` | MySQL |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis |
| `JWT_SECRET` | dev key | **must override in production** |
| `MAIL_ENABLED` | `false` | when `true`, actually sends SMTP email |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP creds | when `MAIL_ENABLED=true` |

When `MAIL_ENABLED=false` (default) the verification / password-reset link is **logged to the console** instead of emailed — convenient for local dev with no SMTP server.

---

## Smoke-test the auth flow

Once MySQL + Redis are running and the app is up:

### 1. Register

```powershell
Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/auth/register" `
  -ContentType "application/json" `
  -Body '{"email":"test@example.com","password":"password123"}'
```

Response:

```json
{
  "userId": 1,
  "email": "test@example.com",
  "role": "USER",
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "eyJhbGciOi...",
  "accessTokenExpiresInSeconds": 900
}
```

Watch the app log for the verification link:

```
[MAIL DISABLED] verification email for test@example.com -> ...?token=...
```

### 2. Verify email

```powershell
Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/auth/verify-email" `
  -ContentType "application/json" `
  -Body '{"token":"<paste-token-from-log>"}'
```

### 3. Login

```powershell
Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/auth/login" `
  -ContentType "application/json" `
  -Body '{"email":"test@example.com","password":"password123"}'
```

### 4. Refresh

```powershell
Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/auth/refresh" `
  -ContentType "application/json" `
  -Body '{"refreshToken":"<paste-refresh-token>"}'
```

The old refresh token is revoked from Redis; the response contains a new pair.

---

## Project layout

```
backend/src/main/java/com/cryptosim/
├── CryptoSimApplication.java
├── auth/
│   ├── controller/AuthController.java         REST endpoints (/api/auth/**)
│   ├── dto/                                   8 record DTOs
│   ├── filter/JwtAuthFilter.java              OncePerRequestFilter — parses Bearer
│   └── service/
│       ├── AuthService.java                   register/login/refresh/verify/reset
│       ├── JwtService.java                    jjwt 0.12, HS256
│       ├── TokenStorageService.java           Redis-backed single-use + jti
│       ├── EmailService.java                  logs link if MAIL_ENABLED=false
│       └── CustomUserDetailsService.java
├── user/
│   ├── entity/{User.java, Role.java}          DECIMAL(18,8) virtual balance
│   └── repository/UserRepository.java
├── config/
│   ├── SecurityConfig.java                    stateless, role-based, JSON errors
│   ├── JwtProperties.java
│   ├── AuthProperties.java
│   └── MailProperties.java
└── common/exception/
    ├── ApiError.java                          structured payload
    ├── ApiException.java + 4 typed exceptions
    └── GlobalExceptionHandler.java
```

---

## Module 1 (Auth) — what's done

- User entity with `BigDecimal` virtual + initial balance (`DECIMAL(18,8)`, starts at `$10,000`).
- Bcrypt password hashing.
- JWT access + refresh tokens (jjwt 0.12, HS256), with `jti` tracked in Redis for rotation/revocation.
- Email verification: 15-min single-use Redis token, link logged in dev.
- Password reset: enumeration-safe request endpoint, 15-min single-use Redis token.
- `JwtAuthFilter` that loads the user into the SecurityContext from a valid access token (rejects refresh tokens on API calls).
- `SecurityConfig`: stateless, `/api/auth/**` public, `/api/admin/**` requires `ROLE_ADMIN`, everything else authenticated. CORS allow-list for `localhost:5173` and `localhost:3000`.
- `GlobalExceptionHandler` returns structured `ApiError` JSON on every 4xx/5xx (including auth/access-denied — no HTML stack traces).

---

## Known gotchas

- **JDK 25 needs Lombok ≥ 1.18.38** (pinned in `pom.xml`). Older versions fail annotation processing.
- **Redis is required** — `/api/auth/register` returns 500 if Redis isn't reachable.
- **`stringRedisTemplate` already exists** in Spring Boot auto-config; don't redefine that bean name.

---

## Next module

**Module 2: Real-Time Price Feed** — Binance WebSocket → `ConcurrentHashMap` → Redis pub/sub → STOMP `/topic/prices/{symbol}`.
