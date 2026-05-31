# Google AdMob SSV Backend Design

## Summary

Add a backend-only Google AdMob server-side verification callback for rewarded ads. Google will call `GET /api/v1/ads/google/ssv`; the backend will validate the callback signature, validate the rewarded ad unit when configured, and persist only successfully verified events.

This work does not credit `user_points`, expose user-facing reward APIs, or update Android rewarded-ad integration. Verified events are stored as future point-credit candidates.

## Scope

- Add a public Google SSV callback endpoint at `GET /api/v1/ads/google/ssv`.
- Verify required callback parameters, Google signature, and configured rewarded ad unit.
- Store successful callbacks in a new Google SSV event table with `reward_status = VERIFIED`.
- Treat repeated `transaction_id` callbacks as idempotent success.
- Log rejected callbacks without storing them.
- Defer actual point crediting and any client-facing status or balance synchronization APIs.

## Architecture

Create a new backend ad domain for the SSV callback, separate from `point`. The ad domain owns Google callback parsing, signature verification, public key caching, event persistence, and HTTP response mapping. The point domain remains unchanged for this feature.

The callback endpoint must be unauthenticated because Google, not a CashChat user session, calls it. `SecurityConfig` should allow only this exact public callback path; any future ad APIs should remain authenticated by default unless explicitly opened.

Google public keys will be fetched through the existing `RestClient` pattern and cached in application memory with a configurable TTL no greater than 24 hours. A process restart may refetch keys on the first callback, which is acceptable for this MVP.

## Persistence

Add a new entity/table named `google_ad_ssv_events` for successfully verified Google SSV callbacks only.

Store at least:

- `transaction_id`: Google transaction identifier, unique.
- `user_id`: raw SSV `user_id`, currently expected to be the CashChat user id string.
- `reward_amount`: parsed reward amount.
- `reward_item`: reward item/type from Google.
- `ad_unit`: callback ad unit id.
- `key_id`: Google public key id used for verification.
- `reward_status`: enum, initially always `VERIFIED`.
- `raw_query_string`: original callback query string, including `signature` and `key_id`.
- inherited `created_at` and `updated_at`.

This table represents verified reward candidates, not completed point-credit ledger entries. Do not add `credited_at` or mutate `user_points` in this feature.

## Validation Flow

The controller preserves the raw query string and passes it to the service. The service validates required parameters before any persistence.

Invalid callback cases return `400 Bad Request` and are logged but not stored:

- missing required parameters
- invalid numeric fields
- unknown or unusable `key_id`
- signature mismatch
- configured rewarded ad unit mismatch

Signature verification follows Google AdMob SSV rules: verify the signature over the original query string with `signature` and `key_id` removed from the signed payload. Because the exact query string matters, persist `raw_query_string` for later troubleshooting.

Rewarded ad unit validation is configuration-driven. In production, the rewarded ad unit id must be configured and callbacks must match it. In dev/test, an empty setting may skip ad unit validation to keep local and test flows flexible.

## Responses And Idempotency

Return:

- `200 OK` when a callback is verified and stored.
- `200 OK` when the same `transaction_id` was already stored.
- `400 Bad Request` for invalid callbacks that should not succeed on retry.
- `500 Internal Server Error` for transient server-side failures, such as Google key fetch failure or database failure.

Use a unique constraint on `transaction_id`. The service should check for an existing event before saving. If a concurrent insert causes a unique violation, reload the existing event and treat it as `200 OK`.

If the same `transaction_id` arrives with different core fields, do not modify the stored event. Log a warning and still return `200 OK`, because the already verified transaction should remain idempotent and this feature does not credit points.

## Configuration

Add configuration under this app-owned namespace:

- `app.ads.google.ssv-public-keys-uri`
- `app.ads.google.public-key-cache-ttl`
- `app.ads.google.rewarded-ad-unit-id`

Development defaults may use Google's documented public key endpoint and may allow an empty rewarded ad unit id. Production should require `APP_ADS_GOOGLE_REWARDED_AD_UNIT_ID`.

## Deferred Point-Credit Security Note

This feature uses SSV `user_id` as the stored user identifier because it only records verified events. Before implementing actual point crediting, add or explicitly evaluate a server-issued reward attempt/nonce flow:

1. The app requests a reward attempt from the backend before showing an ad.
2. The backend stores a short-lived nonce or attempt id for the authenticated user.
3. The app passes that nonce through AdMob SSV `custom_data`.
4. The SSV callback must match a valid, unused attempt before points are credited.

This prevents treating client-supplied `user_id` alone as sufficient authorization for value-bearing point crediting.

## Test Plan

Service tests:

- valid callback saves a `VERIFIED` event
- duplicate `transaction_id` returns success without another save
- required parameter errors return invalid-callback behavior
- invalid reward amount returns invalid-callback behavior
- signature mismatch returns invalid-callback behavior
- rewarded ad unit mismatch returns invalid-callback behavior
- public key fetch failure returns transient-error behavior

Web/controller tests:

- `GET /api/v1/ads/google/ssv` is publicly accessible without JWT
- success returns `200 OK`
- invalid callback returns `400 Bad Request`
- transient service failure returns `500 Internal Server Error`

Persistence tests:

- `transaction_id` unique constraint is enforced
- core callback fields and `raw_query_string` are persisted

## Assumptions

- Android will later set AdMob SSV `user_id` to the CashChat user id string.
- This feature does not need to parse or trust `custom_data` yet.
- Failed callbacks are observable through logs, not DB rows.
- The exact Google public key response shape will be matched to Google's AdMob SSV key server during implementation.
