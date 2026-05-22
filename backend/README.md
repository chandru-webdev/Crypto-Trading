# Crypto Trading Simulator — Backend

## Run (no global Maven install)

From this folder (`backend`):

```powershell
.\run.bat spring-boot:run
```

Or:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.1.8-hotspot"
.\mvnw.cmd spring-boot:run
```

First run downloads Maven 3.9.9 automatically (may take a minute).

## Prerequisites

- **Java 21+** (you have JDK 25 — OK)
- **MySQL** on `localhost:3306`, database `crypto_simulator`
- **Redis** on `localhost:6379`

Create DB (optional — app can create it if user has rights):

```sql
CREATE DATABASE IF NOT EXISTS crypto_simulator;
```

## Environment (optional)

| Variable | Default |
|----------|---------|
| `DB_USERNAME` | `root` |
| `DB_PASSWORD` | `root` |
| `REDIS_HOST` | `localhost` |

## Test auth

```powershell
curl -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" -d "{\"email\":\"test@example.com\",\"password\":\"password123\"}"
```

Verification link is logged when `app.mail.enabled=false` (default).
