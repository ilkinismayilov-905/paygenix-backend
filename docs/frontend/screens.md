# Frontend screens

Each screen and the API it uses. Only screens the backend supports today are built; gaps are listed at the end.
All `/api/**` calls except the public ones need `Authorization: Bearer <access token>`. Errors are problem+json; the UI reads `code`.

## Public

| Screen | Route | API |
|---|---|---|
| Sign in | `/sign-in` | `POST /api/auth/login` (403 ACCOUNT_LOCKED, 429 LOGIN_LOCKED + Retry-After), "Continue with Google" → backend `/oauth2/authorization/google` |
| Google callback | `/oauth2/callback` | `?code=` → `POST /api/auth/oauth/exchange`; `?error=email_not_verified \| account_exists \| account_locked \| oauth_failed` → message |
| Sign up (one route, 5 steps; a reload starts over): email | `/sign-up` | `POST /api/onboarding/email` → `flowId` |
| Sign up: code | `/sign-up` step 2 | `POST /api/onboarding/{flowId}/otp` → onboarding token for the next steps (400 INVALID_OTP / OTP_EXPIRED, 410 FLOW_EXPIRED, 429 TOO_MANY_ATTEMPTS) |
| Sign up: name, username, password + PIN | `/sign-up` steps 3–5 | `PUT /api/onboarding/{flowId}/full-name`, `/username` (409 USERNAME_TAKEN, 429 USERNAME_CHECK_LIMIT + Retry-After), `/pin` {pin: 6 digits, password: 8–72, acceptedLegalDocuments: the Terms/Privacy versions shown} completes sign-up (409 LEGAL_VERSION_OUTDATED: reload the texts, retry); the screen then signs in with the same email + password. Terms/Privacy open in a dialog (`GET /api/legal/terms`, `/privacy`) |

## Signed in

| Screen | Route | API |
|---|---|---|
| App shell (header, nav, sign-out) | — | `GET /api/auth/me`; sign-out: `GET /api/auth/sessions` → `DELETE /api/auth/sessions/{id}` of the `current` one |
| Set first PIN (Google users) | `/pin/setup` | `POST /api/user/pin`; shown on any 403 PIN_NOT_SET (transfer, payment, email change) |
| Dashboard | `/` | `GET /api/cards/me` (masked number, `displayAmount` + `currency`), `GET /api/stories/feed` |
| Cards list | `/cards` | `GET /api/cards/me`, `POST /api/cards` (issue virtual card; the full number is shown only in this response) |
| Card detail | `/cards/:id` | `GET /api/cards/{id}`, `DELETE /api/cards/{id}` (409 CARD_HAS_BALANCE) |
| Transfer (blocked: T-112) | `/transfer` | `POST /api/transfer` with `Idempotency-Key`; 403 INVALID_PIN, 429 PIN_LOCKED + Retry-After, 409 INSUFFICIENT_BALANCE / RESOURCE_BUSY (retry with the same key), 422 IDEMPOTENCY_KEY_REUSED |
| Payment (blocked: T-112) | `/pay` | `POST /api/payment/initiate` → `POST /api/payment/confirm` (both with `Idempotency-Key`); confirm: same PIN errors, 409 PAYMENT_ALREADY_PROCESSED / INSUFFICIENT_BALANCE, 422 IDEMPOTENCY_KEY_REUSED |
| Stories viewer | overlay on dashboard | `GET /api/stories/feed`, `POST /api/stories/{id}/view`, `GET /api/stories/media-download` |
| Profile (blocked: T-113) | `/settings/profile` | `POST /api/profile`, `POST /api/profile/photo`, photo via `GET /api/user/media-download?objectName=`; nothing to prefill the form from yet |
| Change email | `/settings/email` | `POST /api/profile/email` {email, pin} → `POST /api/profile/email/confirm` {otp, currentEmailOtp} |
| Change PIN | `/settings/pin` | `POST /api/change-pin` → `/{flowId}/otp` → `/{flowId}/pin` |
| Devices (sessions) | `/settings/sessions` | `GET /api/auth/sessions`, `DELETE /api/auth/sessions/{id}`, `DELETE /api/auth/sessions` (sign out everywhere) |
| Verify identity | `/settings/verify` | `POST /api/verify`, `POST /api/verify/photo` (no review flow yet, see gaps) |
| Report a problem | `/help/report` | `POST /api/reports` |
| Terms / Privacy | `/legal/terms`, `/legal/privacy` | `GET /api/legal/terms`, `GET /api/legal/privacy` |
| Accept updated Terms/Privacy | shown by the app shell instead of the page while `GET /api/auth/me` lists `pendingLegalDocuments` | `POST /api/legal/acceptances` {documents} (409 LEGAL_VERSION_OUTDATED → re-read /me). Until accepted, transfers and payments answer 403 LEGAL_ACCEPTANCE_REQUIRED |

## System states

| State | Behaviour |
|---|---|
| Session expired | 401 → one refresh (`POST /api/auth/refresh-token`) → retry; if that fails → sign-in with a notice |
| Service unavailable | 503 SERVICE_UNAVAILABLE / MEDIA_UNAVAILABLE → retry banner, honours Retry-After |
| Not found | `/404` page for unknown routes and 404 detail calls |
| Error | `/500`-style error boundary with a retry button |
| Loading / empty | every list and form has loading, empty and error states |

## Backend gaps (no UI until fixed)

| Gap | Effect | Task |
|---|---|---|
| A user cannot list their own transfers, payments or transactions: `GET /api/transfer`, `/api/transaction`, `/api/payment/history` are admin-only | no activity list on dashboard or card detail | T-073 |
| No way to fund a card | balances stay 0 outside seeded data | T-064 Stripe top-up (owner) |
| Transfer and payment take the full 16-digit `fromCardNumber`, but `GET /api/cards/me` gives only the masked number | the client cannot pick the source card | T-112 (`fromCardId`) |
| No endpoint to read one's own profile (`GET /api/profile` is admin-only, `/api/auth/me` has only id, username, roles) | profile form cannot be prefilled | T-113 |
| No KYC review / admin API | verify screen submits only; no admin UI | T-070, T-071 |
