# Lucky Roulette Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the backend-only Lucky Roulette API for CC-355, including KST daily limits, free/ad-gated spins, AdMob SSV nonce routing, weighted prize drawing, energy granting, and result replay.

**Architecture:** Add a new `domain/roulette` module with dedicated persistence for daily state, ad nonce, and spin records. Keep the existing `/api/ads/google/ssv` endpoint, but route verified callbacks to either ordinary ad reward granting or roulette nonce verification based on nonce ownership. Roulette policy starts as Spring configuration values.

**Tech Stack:** Kotlin 1.9.25, Spring Boot 3.5.11, Spring Data JPA, Flyway, Kotest, Mockito, MySQL/H2-compatible DDL.

---

## File Map

- Create `apps/backend/src/main/resources/db/migration/V18__roulette.sql` for roulette tables.
- Create `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/roulette/properties/RouletteProperties.kt` for limits, TTL, segments, and probability table.
- Create `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/roulette/persistence/entity/*` for daily state, ad nonce, spin record, prize, and spin type.
- Create `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/roulette/persistence/repository/*` for JPA access and pessimistic locks.
- Create `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/roulette/service/*` for status, nonce verification, and spin orchestration.
- Create `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/roulette/web/*` for controller, request, response, and exception mapping.
- Modify `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/web/controller/GoogleAdSsvController.kt` to call a routing service instead of directly granting ordinary ad rewards.
- Modify `apps/backend/src/main/resources/application.yaml` to add `app.roulette` defaults.
- Add focused Kotest tests under `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/roulette`.

## Tasks

### Task 1: Policy and Prize Drawing

**Files:**
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/roulette/service/RoulettePrizeDrawServiceTest.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/roulette/properties/RouletteProperties.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/roulette/service/RoulettePrizeDrawService.kt`

- [ ] Write a failing test for exact boundary mapping: rolls below 1 select `JACKPOT_100`, below 11 select `E10`, below 81 select `E3`, otherwise `MISS`.
- [ ] Run the test and confirm it fails because the service does not exist.
- [ ] Implement properties and prize drawing with an injectable integer roller.
- [ ] Run the test and confirm it passes.

### Task 2: Persistence

**Files:**
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/roulette/persistence/RoulettePersistenceIntegrationTest.kt`
- Create: `apps/backend/src/main/resources/db/migration/V18__roulette.sql`
- Create: roulette entities and repositories under `domain/roulette/persistence`.

- [ ] Write a failing migration/entity integration test that saves daily state, nonce, and spin record, then verifies unique nonce replay lookup.
- [ ] Run the test and confirm it fails because tables/entities do not exist.
- [ ] Add Flyway DDL, entities, enums, and repositories.
- [ ] Run the test and confirm it passes.

### Task 3: Service Behavior

**Files:**
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/roulette/service/RouletteServiceTest.kt`
- Create/modify: roulette service files.

- [ ] Write failing tests for status, free spin, blocked ad nonce while free spin remains, verified ad spin, result replay, and capped energy response.
- [ ] Run tests and confirm expected failures.
- [ ] Implement minimal service behavior using repositories, `EnergyService`, and `RoulettePrizeDrawService`.
- [ ] Run tests and confirm they pass.

### Task 4: SSV Routing

**Files:**
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/ad/web/controller/GoogleAdSsvControllerTest.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/AdSsvRewardRouter.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/web/controller/GoogleAdSsvController.kt`

- [ ] Write a failing controller/service test proving a verified roulette nonce is marked verified without calling ordinary ad grant.
- [ ] Run the test and confirm it fails.
- [ ] Add `AdSsvRewardRouter` and route ordinary vs roulette nonce ownership.
- [ ] Run the test and confirm it passes.

### Task 5: API Layer

**Files:**
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/roulette/web/controller/RouletteControllerTest.kt`
- Create: roulette controller, request, response, and exception handler files.

- [ ] Write failing WebMvc tests for status, free spin, issue nonce, spin with ad, and documented error codes.
- [ ] Run tests and confirm expected failures.
- [ ] Add controller, DTOs, and exception handler.
- [ ] Run tests and confirm they pass.

### Task 6: Verification

- [ ] Run `cd apps/backend && ./gradlew test`.
- [ ] If Docker/Testcontainers are unavailable, run the focused non-container tests and report the integration-test blocker clearly.
- [ ] Re-read `docs/domains/benefit-zone/US-CC-355-lucky-roulette-backend.md` and confirm each acceptance criterion is implemented or explicitly deferred.
