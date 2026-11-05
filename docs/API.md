# API reference

Every endpoint is under `/api/v1`. Send your key in the `X-API-Key` header.
The examples use `$ADMIN` (a global or project admin key) and `$CLIENT` (a CLIENT key), as set up in the [README](../README.md).
Examples are for Git Bash; PowerShell equivalents for the quick start are in the README.

The live, interactive version is at `http://localhost:8080/swagger-ui/index.html`.

## Authentication and errors

| Situation | Status |
|---|---|
| No key, malformed, unknown or revoked key | `401` |
| Valid key without permission (CLIENT key on a management endpoint, or a key for another project or environment) | `403` |
| Unknown project, environment, flag or key | `404` |
| Invalid body or parameter | `400` with an `errors` list |
| Duplicate key, or revoking the last global admin key | `409` |
| Too many evaluation requests | `429` with `Retry-After` |

Errors use Problem Details:

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "A project with key 'checkout' already exists"
}
```

Validation errors also list the fields:

```json
{
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more fields are invalid",
  "errors": [{ "field": "rolloutPercentage", "message": "must be less than or equal to 100" }]
}
```

Lists are paginated with `page` (from 0) and `size` (1 to 100, default 20):

```json
{ "items": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0 }
```

## Projects

**Create a project.** Creates `development` and `production` as well. Global admin key only.

```bash
curl -s -X POST http://localhost:8080/api/v1/projects -H "X-API-Key: $ADMIN" \
  -H "Content-Type: application/json" -d '{"key":"checkout","name":"Checkout","description":"Web checkout"}'
```

```json
{ "id": 1, "key": "checkout", "name": "Checkout", "description": "Web checkout", "createdAt": "2026-11-05T13:03:23.071390Z" }
```

**List projects.** Global admin key only.

```bash
curl -s "http://localhost:8080/api/v1/projects?page=0&size=20" -H "X-API-Key: $ADMIN"
```

**Get a project.** Any key of that project, or a global key.

```bash
curl -s http://localhost:8080/api/v1/projects/checkout -H "X-API-Key: $ADMIN"
```

**Update a project.** Omitted fields stay the same. An empty description clears it.

```bash
curl -s -X PATCH http://localhost:8080/api/v1/projects/checkout -H "X-API-Key: $ADMIN" \
  -H "Content-Type: application/json" -d '{"description":"Payments and checkout"}'
```

**Delete a project.** Deletes its environments, flags and keys. The audit history stays. Returns `204`.

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X DELETE http://localhost:8080/api/v1/projects/checkout -H "X-API-Key: $ADMIN"
```

## Environments

**Create an environment.** Every existing flag gets a disabled config in it. Global admin key only.

```bash
curl -s -X POST http://localhost:8080/api/v1/projects/checkout/environments -H "X-API-Key: $ADMIN" \
  -H "Content-Type: application/json" -d '{"key":"staging","name":"Staging"}'
```

```json
{ "id": 3, "key": "staging", "name": "Staging", "createdAt": "2026-11-05T13:10:02.518Z" }
```

**List, get, rename, delete.** Listing and getting need access to the project. Renaming and deleting need a global key. The key cannot change.

```bash
curl -s http://localhost:8080/api/v1/projects/checkout/environments -H "X-API-Key: $ADMIN"
curl -s http://localhost:8080/api/v1/projects/checkout/environments/staging -H "X-API-Key: $ADMIN"
curl -s -X PATCH http://localhost:8080/api/v1/projects/checkout/environments/staging -H "X-API-Key: $ADMIN" \
  -H "Content-Type: application/json" -d '{"name":"Pre-production"}'
curl -s -o /dev/null -w "%{http_code}\n" -X DELETE http://localhost:8080/api/v1/projects/checkout/environments/staging -H "X-API-Key: $ADMIN"
```

## Flags

A flag is defined once per project. Its behaviour is set per environment (see the next section).

**Create a flag.** It starts disabled, at 100% rollout, with no rules, in every environment. Global admin key only.

```bash
curl -s -X POST http://localhost:8080/api/v1/projects/checkout/flags -H "X-API-Key: $ADMIN" \
  -H "Content-Type: application/json" -d '{"key":"new-checkout","name":"New checkout flow","description":"Redesigned payment step"}'
```

```json
{
  "id": 1, "key": "new-checkout", "name": "New checkout flow", "description": "Redesigned payment step",
  "type": "BOOLEAN", "archived": false, "createdAt": "2026-11-05T13:03:23.307707Z", "updatedAt": "2026-11-05T13:03:23.307707Z"
}
```

**List flags.** Archived flags are hidden unless you pass `includeArchived=true`.

```bash
curl -s "http://localhost:8080/api/v1/projects/checkout/flags?includeArchived=true" -H "X-API-Key: $ADMIN"
```

**Get a flag.**

```bash
curl -s http://localhost:8080/api/v1/projects/checkout/flags/new-checkout -H "X-API-Key: $ADMIN"
```

**Update a flag's name, description or archived state.** Global admin key only. Archiving makes the flag `FLAG_NOT_FOUND` for clients, and restoring brings it back.

```bash
curl -s -X PATCH http://localhost:8080/api/v1/projects/checkout/flags/new-checkout -H "X-API-Key: $ADMIN" \
  -H "Content-Type: application/json" -d '{"archived":true}'
```

**Delete a flag.** Deletes its configs. The history stays. Returns `204`. Global admin key only.

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X DELETE http://localhost:8080/api/v1/projects/checkout/flags/new-checkout -H "X-API-Key: $ADMIN"
```

## Flag configs (per environment)

**List configs in an environment.** Sorted by flag key and paginated.

```bash
curl -s http://localhost:8080/api/v1/projects/checkout/environments/production/flags -H "X-API-Key: $ADMIN"
```

**Get one config.**

```bash
curl -s http://localhost:8080/api/v1/projects/checkout/environments/production/flags/new-checkout -H "X-API-Key: $ADMIN"
```

```json
{ "flagKey": "new-checkout", "environmentKey": "production", "enabled": true, "rolloutPercentage": 30, "rules": [], "updatedAt": "2026-11-05T13:03:23.675176Z" }
```

**Update a config.** Send only the fields you want to change. `rules` replaces the whole list, and the order is the evaluation order. A key scoped to this environment can call this.

```bash
curl -s -X PATCH http://localhost:8080/api/v1/projects/checkout/environments/production/flags/new-checkout \
  -H "X-API-Key: $ADMIN" -H "Content-Type: application/json" -d '{
    "enabled": true,
    "rolloutPercentage": 30,
    "rules": [
      { "type": "USER_LIST", "userIds": ["beta-tester-1"], "value": true },
      { "type": "USER_LIST", "userIds": ["blocked-user"], "value": false },
      { "type": "ATTRIBUTE", "attribute": "country", "operator": "IN", "values": ["FR", "DZ"], "value": true }
    ]
  }'
```

Rules:

- `USER_LIST`: `userIds` (1 to 1000 IDs) and `value`. `true` is an on-list, `false` is an off-list.
- `ATTRIBUTE`: `attribute` (a name such as `country`), `operator` (`IN` or `EQUALS`), `values` (`EQUALS` takes exactly one value), and `value`.
- At most 100 rules per config.

## Flag history

**Get the change history of a flag.** Changes to the flag and to its configs in every environment, newest first.

```bash
curl -s http://localhost:8080/api/v1/projects/checkout/flags/new-checkout/history -H "X-API-Key: $ADMIN"
```

```json
{
  "items": [
    {
      "id": 6, "entityType": "FLAG_CONFIG", "action": "UPDATE", "environmentId": 2, "actor": "bootstrap-admin",
      "oldValue": { "enabled": false, "rolloutPercentage": 100, "rules": [] },
      "newValue": { "enabled": true, "rolloutPercentage": 30, "rules": [] },
      "occurredAt": "2026-11-05T13:03:23.675176Z"
    }
  ],
  "page": 0, "size": 20, "totalElements": 2, "totalPages": 1
}
```

## API keys (per environment)

Keys are scoped to one environment. A key can manage keys only in its own environment.

**Create a key.** The raw key is in the `key` field, returned once only. `type` is `ADMIN` or `CLIENT`.

```bash
curl -s -X POST http://localhost:8080/api/v1/projects/checkout/environments/production/api-keys \
  -H "X-API-Key: $ADMIN" -H "Content-Type: application/json" -d '{"name":"web-frontend","type":"CLIENT"}'
```

```json
{
  "id": 2, "name": "web-frontend", "type": "CLIENT", "keyPrefix": "fbx_client_lGWut8",
  "createdAt": "2026-11-05T13:03:33.581474Z", "key": "fbx_client_lGWut8k9b3CH409woCORY8bYUMj54bbJ_f4gOwRynxw"
}
```

**List keys.** Raw keys are never returned.

```bash
curl -s http://localhost:8080/api/v1/projects/checkout/environments/production/api-keys -H "X-API-Key: $ADMIN"
```

**Revoke a key.** It stops working immediately. Revoking twice is harmless. Returns `204`.

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X DELETE \
  http://localhost:8080/api/v1/projects/checkout/environments/production/api-keys/2 -H "X-API-Key: $ADMIN"
```

## Global admin keys

Global keys can manage every project. They are for bootstrap and rotation. Only a global key can call these endpoints.

**Create a global key.** The raw key is returned once.

```bash
curl -s -X POST http://localhost:8080/api/v1/admin/api-keys -H "X-API-Key: $ADMIN" \
  -H "Content-Type: application/json" -d '{"name":"rotated-2026"}'
```

**List global keys.** Includes revoked keys. Raw keys are never returned.

```bash
curl -s http://localhost:8080/api/v1/admin/api-keys -H "X-API-Key: $ADMIN"
```

**Revoke a global key.** Returns `409` if it is the last active global key, so create the new one first. Returns `204`.

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X DELETE http://localhost:8080/api/v1/admin/api-keys/1 -H "X-API-Key: $ADMIN"
```

## Evaluation

These endpoints take the project and environment from the key. A key used here must be scoped to an environment, so a global key gets `403`. Both ADMIN and CLIENT keys work.

**Evaluate one flag.**

```bash
curl -s -X POST http://localhost:8080/api/v1/evaluate -H "X-API-Key: $CLIENT" \
  -H "Content-Type: application/json" -d '{"flagKey":"new-checkout","context":{"userId":"user-42"}}'
```

```json
{ "flagKey": "new-checkout", "value": true, "reason": "ROLLOUT_IN" }
```

With an attribute:

```bash
curl -s -X POST http://localhost:8080/api/v1/evaluate -H "X-API-Key: $CLIENT" \
  -H "Content-Type: application/json" \
  -d '{"flagKey":"new-checkout","context":{"userId":"user-42","attributes":{"country":"FR","plan":"premium"}}}'
```

An unknown or archived flag returns `200` with a `null` value:

```json
{ "flagKey": "no-such-flag", "value": null, "reason": "FLAG_NOT_FOUND" }
```

`userId` is required. For logged-out users, send a stable anonymous ID, such as a cookie or device ID, so their rollout bucket does not change between requests.

**Evaluate every active flag for one user.** Sorted by flag key.

```bash
curl -s -X POST http://localhost:8080/api/v1/evaluate/all -H "X-API-Key: $CLIENT" \
  -H "Content-Type: application/json" -d '{"context":{"userId":"user-42"}}'
```

```json
{ "flags": [ { "flagKey": "new-checkout", "value": true, "reason": "ROLLOUT_IN" } ] }
```

## Operations

```bash
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8080/actuator/metrics/flagbox.evaluations -H "X-API-Key: $ADMIN"
```

The health endpoint is public. The metrics counter `flagbox.evaluations` is tagged by `flag` and `reason`. Unknown flag keys share the tag value `unknown`.
