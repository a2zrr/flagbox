# Decisions

Each entry gives the context, the decision, and what follows from it.

## 1. MurmurHash3 for rollout buckets

**Context.** A rollout needs a stable number for each user and flag, so the same user keeps the same answer. The number must be reproducible in any language, because clients may want to check it.

**Decision.** Use MurmurHash3 (x86, 32-bit, seed 0) of `flagKey:userId`, modulo 100. It is fast, has good spread, and has published test vectors.

**Consequences.** Any language can reproduce a bucket with a standard library. The test suite checks the hash against published vectors and an independent implementation. Changing the hash or the input format would move every user, so it must not change.

## 2. API keys, not JWTs

**Context.** Two kinds of caller need access: an admin tool that manages flags, and an app that evaluates them. Both are server-to-server.

**Decision.** Use opaque API keys with an `X-API-Key` header. Store only the SHA-256 of each key. Keys are scoped to an environment and can be revoked at once.

**Consequences.** A revocation takes effect on the next request, with no token expiry to wait for. JWTs would need a signing key, expiry handling and a revocation list, and none of that buys anything for this use. Every request does one indexed lookup, which is cheap. Keys cannot carry user identity, which is fine here.

## 3. First match wins, with the rule's own value

**Context.** A flag needs targeting: some users always on or off, and some attributes matching a rule. Rules can conflict, for example a user on both an on-list and an off-list.

**Decision.** Rules are an ordered list, and the first match decides. Each rule carries its own `value`, so an attribute rule can also serve `false`. The evaluation order is fixed and documented.

**Consequences.** Conflicts are resolved by position, and the list is visible to the caller. Order must be kept when rules are edited, so `rules` in an update replaces the whole list. Users see the same answer for the same input, which makes support easier.

## 4. Caffeine snapshot cache, evicted after commit

**Context.** Evaluation is on the hot path of every app request, and should not query the database each time.

**Decision.** Cache one snapshot per project (all environments, all active flags) in Caffeine, load it with one query, and evict it after the transaction commits.

**Consequences.** Evaluation is memory-only once warm. Eviction after commit means a concurrent read cannot put the old state back into the cache. The cache is per instance, so each instance evicts its own copy. With the 10-minute expiry as a safety net, a missed eviction is bounded in time. The tests check that changes show up on the next evaluation.

## 5. Global admin keys with a last-key lock

**Context.** The bootstrap key must create projects, and rotating it must not lock everyone out.

**Decision.** Keys are either global (no scope, ADMIN only) or scoped to one environment. Revoking the last active global key is refused with `409`, and the check takes a row lock so two revocations cannot race past it.

**Consequences.** Rotation is safe: create the new key, then revoke the old one. The lock makes the check correct under concurrency, and a test races two revocations to prove it. The cost is a short lock on the global keys during a revocation, which is rare.

## 6. Ship the runtime as a jlink image on Alpine

**Context.** The first image was 412 MB, mostly a JRE with fonts this headless service never uses.

**Decision.** Build a trimmed runtime with `jlink` in an Alpine build stage, and copy only that and the jar into a plain Alpine image. The module list is explicit, because `jdeps` does not see the libraries inside a Spring Boot jar.

**Consequences.** The image is 242 MB. Modules have to be listed by hand, and a missing one only shows up at runtime. Each time a dependency changes, the image needs a smoke test (health, a key, an evaluation).
