# Crypto Trading Simulator

A full-stack crypto trading simulator with virtual money, real-time Binance prices, portfolio tracking, and a global leaderboard. Built module-by-module from a production-style guide.

**Live repo:** https://github.com/chandru-webdev/Crypto-Trading

---

## Project summary

Practice trading **without risking real money**: every user starts with a virtual `$10,000.00000000` balance and trades live BTC / ETH / SOL prices streamed from Binance's public WebSocket. The system maintains a holdings ledger, computes live PnL, persists trade history, and ranks users on a global leaderboard.

This is designed as a **resume-grade Spring Boot + React project** — production patterns (BigDecimal money, JWT auth with refresh-token rotation, structured error JSON, Redis caching, atomic trades) are used from day one rather than retrofitted.

### Goals
1. Real-time price feed via Binance WebSocket → broadcast to clients via STOMP.
2. Atomic BUY / SELL trade execution that can never corrupt a user's balance.
3. Live portfolio valuation and PnL.
4. Trade history with filters + leaderboard cached in Redis.
5. React dashboard with live charts and a trading panel.

---

## Tech stack

| Layer | Tech |
|---|---|
| **Backend framework** | Spring Boot 3.3 on Java 21+ (tested up to JDK 25) |
| **Security** | Spring Security 6, JWT (jjwt 0.12), bcrypt |
| **Persistence** | Spring Data JPA, MySQL 8 (`DECIMAL(18,8)` for money) |
| **Cache / pub-sub** | Spring Data Redis (Lettuce client), Redis 7 (Memurai on Windows) |
| **Real-time** | Spring WebSocket + STOMP over SockJS, Binance public stream |
| **Mail** | Spring `JavaMailSender` (dev mode: links logged instead of sent) |
| **Build** | Maven 3.9 + Spring Boot Maven Plugin (Maven Wrapper included) |
| **Frontend** *(planned, Module 6)* | React 18, Vite, Tailwind CSS, Recharts, SockJS-Client, StompJS |

---

## Build status

| # | Module | Status |
|---|---|---|
| 1 | Auth (JWT + email verify + password reset)         | **done** |
| 2 | Real-Time Price Feed (Binance WS + Redis pub/sub)  | next |
| 3 | Trade Execution (atomic BUY/SELL, BigDecimal)      | planned |
| 4 | Portfolio (live valuation + PnL)                   | planned |
| 5 | Leaderboard + Trade History + Watchlist            | planned |
| 6 | React Frontend                                     | planned |
| 7 | Tests + Docker Compose                             | planned |

---

## What's built so far — Module 1: Auth

A complete authentication backend covering registration, login, refresh-token rotation, email verification, and password reset.

### Features
- **Register** → bcrypt password hash → user persisted with `$10,000` starting balance → verification email queued (or logged in dev).
- **Login** → returns short-lived access token (15 min) + long-lived refresh token (7 days).
- **Refresh-token rotation**: every refresh revokes the old `jti` from Redis and issues a brand-new pair. A stolen token only works until the legit user next refreshes.
- **Email verification**: opaque random token stored in Redis with 15-min TTL, single-use.
- **Password reset**: enumeration-safe request endpoint, single-use Redis token, 15-min TTL.
- **Role-based access**: `USER` (default) and `ADMIN` enforced via `SecurityConfig` and `@PreAuthorize`.
- **Structured errors**: every 4xx/5xx returns a consistent `ApiError` JSON (code, message, timestamp, path, optional field-level violations).

### REST endpoints (all under `/api/auth`)

| Method | Path | Description |
|---|---|---|
| POST | `/register`               | Create account; returns `accessToken` + `refreshToken` |
| POST | `/login`                  | Authenticate; returns token pair |
| POST | `/refresh`                | Rotate refresh, returns new token pair |
| POST | `/logout`                 | Revoke the supplied refresh token |
| POST | `/verify-email`           | Consume single-use Redis token |
| POST | `/resend-verification`    | Re-send verification email (silent if already verified) |
| POST | `/password-reset/request` | Email a single-use reset token (silent if user not found) |
| POST | `/password-reset/confirm` | Consume reset token + set new password |

### Example responses

`POST /api/auth/register` → `201 Created`
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

Any error (validation, auth, conflict, etc.) → consistent shape:
```json
{
  "timestamp": "2026-05-22T18:40:11.123Z",
  "status": 409,
  "error": "Conflict",
  "code": "EMAIL_ALREADY_EXISTS",
  "message": "An account with email 'test@example.com' already exists",
  "path": "/api/auth/register"
}
```

---

## Repository layout

```
crypto/
├── README.md                          ← you are here
├── .gitignore
└── backend/
    ├── pom.xml                        Spring Boot 3.3.5, jjwt 0.12, Lombok 1.18.38
    ├── mvnw.cmd / run.bat             Maven Wrapper (no global Maven required)
    ├── README.md                      backend-specific run notes
    └── src/main/
        ├── java/com/cryptosim/
        │   ├── CryptoSimApplication.java
        │   ├── auth/
        │   │   ├── controller/AuthController.java
        │   │   ├── dto/                Register/Login/Refresh/Reset DTOs (records)
        │   │   ├── filter/JwtAuthFilter.java
        │   │   └── service/
        │   │       ├── AuthService.java              register/login/refresh/verify/reset
        │   │       ├── JwtService.java               jjwt 0.12, HS256, access + refresh
        │   │       ├── TokenStorageService.java      Redis-backed single-use + jti store
        │   │       ├── EmailService.java             logs link when MAIL_ENABLED=false
        │   │       └── CustomUserDetailsService.java
        │   ├── user/
        │   │   ├── entity/{User.java, Role.java}    DECIMAL(18,8) virtual balance
        │   │   └── repository/UserRepository.java
        │   ├── config/
        │   │   ├── SecurityConfig.java               stateless, role-based, JSON errors
        │   │   ├── JwtProperties.java
        │   │   ├── AuthProperties.java
        │   │   └── MailProperties.java
        │   └── common/exception/
        │       ├── ApiError.java                     structured payload
        │       ├── ApiException.java                 base + 4 typed subclasses
        │       └── GlobalExceptionHandler.java
        └── resources/
            └── application.yml
```

---

## Quick start

### Prerequisites

| | What |
|---|---|
| Java | JDK 21+ (tested with Adoptium JDK 25) |
| Maven | 3.9+ on PATH (or use the bundled `mvnw.cmd` / `run.bat`) |
| MySQL | 8.x on `localhost:3306` |
| Redis | `localhost:6379` — on Windows install [Memurai](https://www.memurai.com/get-memurai) |

### Run

```powershell
cd backend
mvn spring-boot:run
```

The app starts on `http://localhost:8080`. On first run, JPA creates the `users` table automatically inside the `crypto_simulator` database.

### Try it

```powershell
Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/auth/register" `
  -ContentType "application/json" `
  -Body '{"email":"test@example.com","password":"password123"}'
```

When `app.mail.enabled=false` (dev default), the verification link appears in the server log:
```
[MAIL DISABLED] verification email for test@example.com -> ...?token=...
```

### Environment variables

All have sensible defaults. Override what you need:

| Variable | Default | Used by |
|---|---|---|
| `DB_HOST` | `localhost` | MySQL |
| `DB_PORT` | `3306` | MySQL |
| `DB_NAME` | `crypto_simulator` | MySQL |
| `DB_USERNAME` | `root` | MySQL |
| `DB_PASSWORD` | `root` | MySQL |
| `REDIS_HOST` | `localhost` | Redis |
| `REDIS_PORT` | `6379` | Redis |
| `JWT_SECRET` | dev key | **change in production** |
| `MAIL_ENABLED` | `false` | when `true`, actually sends emails |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | — | SMTP config |
| `APP_VERIFY_URL` | `http://localhost:5173/verify-email` | frontend route |
| `APP_RESET_URL` | `http://localhost:5173/reset-password` | frontend route |

---

## Architecture notes

### Money handling
`BigDecimal` everywhere in Java; `DECIMAL(18,8)` everywhere in MySQL. Eight decimal places of precision matches exchange standards and matters because:

```java
0.1 + 0.2 == 0.30000000000000004   // floating-point — a financial bug
```

### Transactional trades (coming Module 3)
Every BUY/SELL will be wrapped in `@Transactional` — balance debit + holding credit happen atomically. If any step fails the entire trade rolls back; you can never end up with money debited but no holding credited.

### JWT design
| Token | TTL | Where stored | Purpose |
|---|---|---|---|
| **access**  | 15 min | client (memory) | sent in `Authorization: Bearer …` on every API call |
| **refresh** | 7 days | client + Redis (`jti`) | trade in for a new access token |

The filter rejects refresh tokens used on API endpoints — only access tokens authenticate requests.

### Real-time prices (coming Module 2)
The plan:
1. `BinancePriceService` connects to `wss://stream.binance.com/stream` on startup.
2. Each tick parses `c` (current price) into `BigDecimal` and writes to a `ConcurrentHashMap<String, BigDecimal>` — O(1) reads for trade execution.
3. Same tick is pushed to Redis pub/sub.
4. A Redis subscriber broadcasts to STOMP topic `/topic/prices/{symbol}` — all browser clients see updates with no polling.
5. A `@Scheduled` heartbeat reconnects the WebSocket if Binance drops the connection.

---

## Security model

- Passwords: **bcrypt** (Spring Security default).
- JWT secret loaded from env var, never committed.
- Access tokens are intentionally short (15 min) so a leaked one expires fast.
- Refresh tokens are revoked the moment they're used — rotation defeats token theft.
- CORS allow-list scoped to local Vite/CRA dev ports (`5173`, `3000`).
- All error responses are JSON; no HTML stack traces leak to clients.
- Reset/verification URL-safe random tokens (32 bytes) stored only in Redis with TTL.

---

## Development log

| Commit | What |
|---|---|
| `1af8384` | feat(auth): scaffold backend + Module 1 (40 files, 1780 lines) |
| `ce78fa6` | fix(build): Lombok 1.18.38 for JDK 25, remove duplicate Redis bean |

---

## Known gotchas

- **JDK 25 + Lombok**: needs Lombok ≥ 1.18.38 (older versions fail annotation processing on JDK 25). Pinned in `pom.xml`.
- **Redis required**: register/login will 500 if Redis isn't running. Install Memurai on Windows or `redis-server` on Linux/Mac.
- **Don't `@Bean` `stringRedisTemplate`**: Spring Boot already provides one. Define a custom one only under a different bean name.

---

## License

MIT (update before publishing).
