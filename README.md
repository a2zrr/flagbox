# FlagBox

[![CI](https://github.com/a2zrr/flagbox/actions/workflows/ci.yml/badge.svg)](https://github.com/a2zrr/flagbox/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

FlagBox is a REST API that turns features on and off in your apps without a redeploy.
You can roll a feature out to a percentage of users, or target specific users and attributes, and change it at any time.
It is built with Spring Boot 3, PostgreSQL and Flyway, and runs as a single container.

Feature flags let you ship code that is turned off, then enable it gradually and roll it back in seconds if something breaks.

## Features

- Boolean flags per project, with a separate config in each environment (for example development and production)
- Percentage rollouts that are deterministic: the same user always gets the same answer
- Targeting rules: on or off for a list of user IDs, or match on an attribute such as `country IN [FR, DZ]`
- One call evaluates one flag, or every flag for a user
- Two kinds of API keys: admin keys to manage flags, and client keys to evaluate them
- Full audit history of every change, with old and new values
- Problem Details (RFC 7807) for every error, and OpenAPI docs at `/swagger-ui/index.html`
- Rate limiting per key, metrics per flag, and health checks

## How it works

A client asks for a flag with a user ID. FlagBox checks, in this order, and the first step that decides wins:

| Step | Condition | Value | Reason |
|---|---|---|---|
| 1 | No such flag (or it is archived) | `null` | `FLAG_NOT_FOUND`: the app uses its own fallback |
| 2 | Flag is disabled in this environment | `false` | `FLAG_DISABLED` |
| 3a | Rules, in order: user is on an off-list | `false` | `USER_DENIED` |
| 3b | Rules, in order: user is on an on-list | `true` | `USER_ALLOWED` |
| 3c | Rules, in order: attribute rule matches | the rule's value | `RULE_MATCH` |
| 4 | Rollout: user's bucket is below the percentage | `true` | `ROLLOUT_IN` |
| 4 | Rollout: otherwise | `false` | `ROLLOUT_OUT` |

The rollout bucket is MurmurHash3 of `flagKey:userId`, modulo 100.
Read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the details.

## Quick start

You need Docker, and Git. The commands below work in Git Bash. PowerShell equivalents follow each block.

**1. Get the code and create your `.env`**

```bash
git clone https://github.com/a2zrr/flagbox.git
cd flagbox
cp .env.example .env
KEY="fbx_admin_$(openssl rand -base64 32 | tr '+/' '-_' | tr -d '=\n' | cut -c1-43)"
printf 'FLAGBOX_BOOTSTRAP_ADMIN_KEY=%s\n' "$KEY" > .env
export ADMIN="$KEY"
```

```powershell
git clone https://github.com/a2zrr/flagbox.git
cd flagbox
Copy-Item .env.example .env
$ADMIN = 'fbx_admin_' + (-join ((48..57 + 65..90 + 97..122) | Get-Random -Count 43 | ForEach-Object { [char]$_ }))
"FLAGBOX_BOOTSTRAP_ADMIN_KEY=$ADMIN" | Set-Content -Encoding ascii .env
```

**2. Start FlagBox and PostgreSQL**

The first build takes a few minutes.

```bash
docker compose -f docker-compose.full.yml up -d --build
```

```powershell
docker compose -f docker-compose.full.yml up -d --build
```

**3. Check that it is up**

```bash
curl -s http://localhost:8080/actuator/health
```

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

You should see `"status":"UP"`.

**4. Create a project**

```bash
curl -s -X POST http://localhost:8080/api/v1/projects \
  -H "X-API-Key: $ADMIN" -H "Content-Type: application/json" \
  -d '{"key":"checkout","name":"Checkout"}'
```

```powershell
$headers = @{ 'X-API-Key' = $ADMIN }
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/projects -Headers $headers `
  -ContentType 'application/json' -Body '{"key":"checkout","name":"Checkout"}'
```

**5. Create a flag and turn it on at 30% in production**

```bash
curl -s -X POST http://localhost:8080/api/v1/projects/checkout/flags \
  -H "X-API-Key: $ADMIN" -H "Content-Type: application/json" \
  -d '{"key":"new-checkout","name":"New checkout flow"}'

curl -s -X PATCH http://localhost:8080/api/v1/projects/checkout/environments/production/flags/new-checkout \
  -H "X-API-Key: $ADMIN" -H "Content-Type: application/json" \
  -d '{"enabled":true,"rolloutPercentage":30}'
```

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/projects/checkout/flags -Headers $headers `
  -ContentType 'application/json' -Body '{"key":"new-checkout","name":"New checkout flow"}'

Invoke-RestMethod -Method Patch -Uri http://localhost:8080/api/v1/projects/checkout/environments/production/flags/new-checkout `
  -Headers $headers -ContentType 'application/json' -Body '{"enabled":true,"rolloutPercentage":30}'
```

**6. Create a CLIENT key for production and save it**

The raw key is returned only once, so copy it now.

```bash
CLIENT=$(curl -s -X POST http://localhost:8080/api/v1/projects/checkout/environments/production/api-keys \
  -H "X-API-Key: $ADMIN" -H "Content-Type: application/json" \
  -d '{"name":"web-frontend","type":"CLIENT"}' | sed -n 's/.*"key":"\([^"]*\)".*/\1/p')
echo "$CLIENT"
```

```powershell
$client = (Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/projects/checkout/environments/production/api-keys `
  -Headers $headers -ContentType 'application/json' -Body '{"name":"web-frontend","type":"CLIENT"}').key
$client
```

**7. Evaluate the flag for a user**

```bash
curl -s -X POST http://localhost:8080/api/v1/evaluate \
  -H "X-API-Key: $CLIENT" -H "Content-Type: application/json" \
  -d '{"flagKey":"new-checkout","context":{"userId":"user-42"}}'
```

```powershell
$clientHeaders = @{ 'X-API-Key' = $client }
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/evaluate -Headers $clientHeaders `
  -ContentType 'application/json' -Body '{"flagKey":"new-checkout","context":{"userId":"user-42"}}'
```

The answer looks like `{"flagKey":"new-checkout","value":true,"reason":"ROLLOUT_IN"}`.
Change `user-42` to other IDs and about 30% of them will get `true`, the same ones every time.

To stop everything: `docker compose -f docker-compose.full.yml down`.

## Try the JavaScript example

[examples/js](examples/js) is a plain Node 18+ client with no dependencies. It falls back to a local default when FlagBox says `FLAG_NOT_FOUND` or cannot be reached.

```bash
cd examples/js && FLAGBOX_CLIENT_KEY="$CLIENT" node evaluate.mjs new-checkout user-1 user-2 user-3
```

## Documentation

- [docs/API.md](docs/API.md): every endpoint, with curl examples and sample responses
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): packages, data model, evaluation flow, caching and security
- [docs/DECISIONS.md](docs/DECISIONS.md): the main design choices and why
- [examples/js](examples/js): the Node client example
- [CONTRIBUTING.md](CONTRIBUTING.md): how to build, test and send changes

## Tech stack

Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, Flyway, PostgreSQL 16, Caffeine, Bucket4j, springdoc-openapi, Micrometer, Testcontainers, JaCoCo, Docker, GitHub Actions.

## Configuration

| Variable | Default | What it does |
|---|---|---|
| `FLAGBOX_BOOTSTRAP_ADMIN_KEY` | none | Registers the first global admin key on first start. Ignored after that. |
| `FLAGBOX_RATE_LIMIT_EVALUATIONS_PER_SECOND` | `100` | Evaluation requests per second, per API key |
| `FLAGBOX_DB_URL`, `FLAGBOX_DB_USER`, `FLAGBOX_DB_PASSWORD` | local PostgreSQL | Database connection |
| `PORT` | `8080` | HTTP port |

## Known limitations

- **Single-instance rate limit.** Rate limit counters live in memory, so with several instances each one applies the limit separately. A shared store such as Redis would be needed for an exact global limit.
- **Boolean flags only.** Multivariate flags (strings, numbers) are not supported yet.
- **No UI.** Flags are managed through the API and the Swagger page.
- **Global key rotation needs two keys.** The last active global admin key cannot be revoked, so create the new key first.
- **Attribute values are strings.** Matching is case-sensitive, and numbers and booleans are compared as text.

## License

MIT. See [LICENSE](LICENSE).
