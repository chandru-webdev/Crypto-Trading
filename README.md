# Crypto Trading Simulator

A full-stack crypto trading simulator with virtual money, real-time Binance prices, portfolio tracking, and a global leaderboard.

> Built module-by-module from a project guide.
> See [`docs/`](#) (planned) for architectural notes.

## Stack

**Backend**
- Spring Boot 3.3 (Java 21+)
- Spring Security + JWT (access + refresh-token rotation)
- Spring Data JPA + MySQL 8 (`DECIMAL(18,8)` for all monetary values)
- Spring Data Redis (short-lived single-use tokens, refresh-token JTI store, pub/sub)
- Spring WebSocket + STOMP (live price broadcast — coming in Module 2)
- Binance public WebSocket API for real-time prices (coming in Module 2)

**Frontend** (coming in Module 6)
- React 18 + Vite + Tailwind CSS
- SockJS + StompJS
- Recharts

## Repository layout

```
backend/                 Spring Boot service (this is the only module so far)
  src/main/java/com/cryptosim/
    auth/                Module 1 — registration, login, JWT, email verify, password reset
    user/                User domain (entity + repo)
    config/              SecurityConfig, RedisConfig, properties holders
    common/exception/    Structured ApiError + global handler
  src/main/resources/
    application.yml
  pom.xml
  mvnw.cmd / run.bat     Maven Wrapper so you don't need a global Maven install
```

## Build status

| Module | Status |
|---|---|
| 1. Auth (JWT + email verify + password reset)        | done |
| 2. Real-Time Price Feed (Binance WS + Redis pub/sub) | next |
| 3. Trade Execution (atomic BUY/SELL with BigDecimal) | planned |
| 4. Portfolio (live valuation + PnL)                  | planned |
| 5. Leaderboard + Trade History + Watchlist           | planned |
| 6. React Frontend                                    | planned |

## Quick start (backend)

Prereqs: **JDK 21+**, **MySQL** on `localhost:3306`, **Redis** on `localhost:6379`.

```bash
cd backend
mvn spring-boot:run                # if Maven is on PATH
# or, if Maven isn't installed:
./run.bat spring-boot:run          # on Windows
```

First run will create tables in `crypto_simulator` automatically.

Override defaults via env vars (see [`backend/src/main/resources/application.yml`](backend/src/main/resources/application.yml)):

```
DB_USERNAME, DB_PASSWORD, REDIS_HOST, JWT_SECRET, MAIL_ENABLED, ...
```

## Auth endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/api/auth/register`               | Create account, returns access + refresh token |
| POST | `/api/auth/login`                  | Returns access + refresh token |
| POST | `/api/auth/refresh`                | Rotate refresh token, get new access |
| POST | `/api/auth/logout`                 | Revoke refresh token |
| POST | `/api/auth/verify-email`           | Consume single-use Redis token |
| POST | `/api/auth/resend-verification`    | Re-send verification email |
| POST | `/api/auth/password-reset/request` | Email a single-use reset token |
| POST | `/api/auth/password-reset/confirm` | Consume reset token + set new password |

When `app.mail.enabled=false` (dev default), the verification/reset link is printed to the server log instead of sent.

## Security model

- Passwords hashed with bcrypt.
- Access tokens are short-lived (15 min). Refresh tokens are stored server-side (Redis) by `jti` and rotated on every use — a stolen token cannot be reused after the legit user refreshes.
- All error responses follow a structured `ApiError` JSON shape.
- Role-based access (`USER`, `ADMIN`) via `@PreAuthorize` and `SecurityConfig`.
- Money: `BigDecimal` in Java, `DECIMAL(18,8)` in MySQL — never floating-point.

## License

MIT (or your choice — update before publishing).
