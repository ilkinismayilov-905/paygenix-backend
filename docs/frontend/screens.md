# Frontend screens

Each screen and the API it uses. Only screens the backend supports today are built; gaps are listed at the end.
All `/api/**` calls except the public ones need `Authorization: Bearer <access token>`. Errors are problem+json; the UI reads `code`.

## Public

| Screen | Route | API |
|---|---|---|
| Sign in | `/sign-in` | `POST /api/auth/login` (403 ACCOUNT_LOCKED, 429 LOGIN_LOCKED + Retry-After), "Continue with Google" → backend `/oauth2/authorization/google` |
| Google callback | `/oauth2/callback` | `?code=` → `POST /api/auth/oauth/exchange`; `?error=email_not_verified \| account_exists \| account_locked \| oauth_failed` → message |
| Sign up: email | `/sign-up` | `POST /api/onboarding/email` → `flowId` |
| Sign up: code | `/sign-up/code` | `POST /api/onboarding/{flowId}/otp` → onboarding token for the next steps (410 expired, 429 too many) |
| Sign up: name, username, PIN | `/sign-up/profile` | `PUT /api/onboarding/{flowId}/full-name`, `/username` (409 taken, 429), `/pin` {pin: 6 digits, password: 8–72} completes sign-up, then sign in with email + password |

## Signed in

| Screen | Route | API |
|---|---|---|
| App shell (header, nav, sign-out) | — | `GET /api/auth/me`; sign-out: `GET /api/auth/sessions` → `DELETE /api/auth/sessions/{id}` of the `current` one |
| Set first PIN (Google users) | `/pin/setup` | `POST /api/user/pin`; shown when a payment answers 403 PIN_NOT_SET |
| Dashboard | `/` | `GET /api/cards/me` (cards + balances), `GET /api/stories/feed` |
| Cards list | `/cards` | `GET /api/cards/me`, `POST /api/cards` (issue virtual card) |
| Card detail | `/cards/:id` | `GET /api/cards/{id}`, `DELETE /api/cards/{id}` (409 CARD_HAS_BALANCE) |
| Transfer | `/transfer` | `POST /api/transfer` with `Idempotency-Key`; PIN errors 403 INVALID_PIN, 429 PIN_LOCKED + Retry-After; 422 insufficient balance |
| Payment | `/pay` | `POST /api/payment/initiate` → `POST /api/payment/confirm` (both with `Idempotency-Key`) |
| Stories viewer | overlay on dashboard | `GET /api/stories/feed`, `POST /api/stories/{id}/view`, `GET /api/stories/media-download` |
| Profile | `/settings/profile` | `GET /api/auth/me`, `POST /api/profile`, `POST /api/profile/photo` |
| Change email | `/settings/email` | `POST /api/profile/email` {email, pin} → `POST /api/profile/email/confirm` {otp, currentEmailOtp} |
| Change PIN | `/settings/pin` | `POST /api/change-pin` → `/{flowId}/otp` → `/{flowId}/pin` |
| Devices (sessions) | `/settings/sessions` | `GET /api/auth/sessions`, `DELETE /api/auth/sessions/{id}`, `DELETE /api/auth/sessions` (sign out everywhere) |
| Verify identity | `/settings/verify` | `POST /api/verify`, `POST /api/verify/photo` (no review flow yet, see gaps) |
| Report a problem | `/help/report` | `POST /api/reports` |
| Terms / Privacy | `/legal/terms`, `/legal/privacy` | `GET /api/legal/terms`, `GET /api/legal/privacy` |

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
| No way to fund a card | balances stay 0 outside seeded data | T-062 (owner) |
| `/api/legal/*` needs a signed-in user | sign-up cannot show Terms before the account exists | T-110 |
| Refresh token only in the JSON body | page reload signs the user out | T-109 |
| No KYC review / admin API | verify screen submits only; no admin UI | T-070, T-071 |
