# Frontend screens

Each screen and the API it uses. Only screens the backend supports today are built; gaps are listed at the end.
All `/api/v1/**` calls except the public ones need `Authorization: Bearer <access token>`. Errors are problem+json; the UI reads `code`.

## Public

| Screen | Route | API |
|---|---|---|
| Sign in | `/sign-in` | `POST /api/v1/auth/login` (403 ACCOUNT_LOCKED, 429 LOGIN_LOCKED + Retry-After), "Continue with Google" → backend `/oauth2/authorization/google` |
| Google callback | `/oauth2/callback` | `?code=` → `POST /api/v1/auth/oauth/exchange`; `?error=email_not_verified \| account_exists \| account_locked \| oauth_failed` → message |
| Sign up (one route, 5 steps; a reload starts over): email | `/sign-up` | `POST /api/v1/onboarding/email` → `flowId` |
| Sign up: code | `/sign-up` step 2 | `POST /api/v1/onboarding/{flowId}/otp` → onboarding token for the next steps (400 INVALID_OTP / OTP_EXPIRED, 410 FLOW_EXPIRED, 429 TOO_MANY_ATTEMPTS) |
| Sign up: name, username, password + PIN | `/sign-up` steps 3–5 | `PUT /api/v1/onboarding/{flowId}/full-name`, `/username` (409 USERNAME_TAKEN, 429 USERNAME_CHECK_LIMIT + Retry-After), `/pin` {pin: 6 digits, password: 8–72, acceptedLegalDocuments: the Terms/Privacy versions shown} completes sign-up (409 LEGAL_VERSION_OUTDATED: reload the texts, retry); the screen then signs in with the same email + password. Terms/Privacy open in a dialog (`GET /api/v1/legal/terms`, `/privacy`) |

## Signed in

| Screen | Route | API |
|---|---|---|
| App shell (header, nav, sign-out) | — | `GET /api/v1/auth/me`; sign-out: `GET /api/v1/auth/sessions` → `DELETE /api/v1/auth/sessions/{id}` of the `current` one |
| Set first PIN (Google users) | `/pin/setup` | `POST /api/v1/user/pin` {pin} (409 PIN_ALREADY_SET); transfer/pay link here on 403 PIN_NOT_SET and come back (router state `from`); email change will too |
| Dashboard | `/` | `GET /api/v1/cards/me` (masked number, `displayAmount` + `currency`), `GET /api/v1/cards/me/activity?page=&size=` (newest first, `hasNext`), `GET /api/v1/stories/feed` (promo row, hidden when empty or on error) |
| Cards list | `/cards` | `GET /api/v1/cards/me`, `POST /api/v1/cards` (issue virtual card; the full number is shown only in this response) |
| Card detail | `/cards/:id` | `GET /api/v1/cards/{id}`, `GET /api/v1/cards/me/activity?cardId=`, `DELETE /api/v1/cards/{id}` (409 CARD_HAS_BALANCE) |
| Transfer | `/transfer` | `GET /api/v1/fees` (fee and total shown as typed), `POST /api/v1/transfer` {fromCardId, toCardNumber, amount, pin} with `Idempotency-Key`, response has `currency`; 403 INVALID_PIN, 429 PIN_LOCKED + Retry-After, 409 INSUFFICIENT_BALANCE / RESOURCE_BUSY (retry with the same key), 422 IDEMPOTENCY_KEY_REUSED |
| Top up | `/top-up` (from dashboard and card detail, `?card=`), `/top-up/return?session=` | `POST /api/v1/topups` {cardId, amount 1.00-10000.00} with `Idempotency-Key` → browser goes to Stripe Checkout (test mode); back on the return page `GET /api/v1/topups/session/{id}` is polled until not PENDING (SUCCEEDED, EXPIRED, REFUND_NEEDED). 503 TOP_UP_UNAVAILABLE when Stripe is not configured |
| Payment | `/pay` | `POST /api/v1/payment/initiate` {phoneNumber, fromCardId, amount} → `POST /api/v1/payment/confirm` (both with `Idempotency-Key`); confirm: same PIN errors, 409 PAYMENT_ALREADY_PROCESSED / INSUFFICIENT_BALANCE, 410 PAYMENT_EXPIRED (not confirmed within 15 min), 422 PHONE_TOPUP_REJECTED / 504 PROVIDER_TIMEOUT (operator; nothing charged; the mock refuses numbers ending 000 and times out on 999), 422 IDEMPOTENCY_KEY_REUSED; cashback rate from `GET /api/v1/fees` |
| Stories viewer | full-screen dialog on the dashboard | `GET /api/v1/stories/feed` (one brand group, presigned `mediaUrl`), `POST /api/v1/stories/{id}/view` when shown; starts at the first unseen, images 5 s, videos to the end |
| Admin: users | `/admin/users` (admins only) | `GET /api/v1/admin/users?query=`, `POST /api/v1/admin/users/{id}/lock` (signs out everywhere) / `unlock`; 409 CANNOT_LOCK_SELF, 403 ADMIN_TARGET |
| Admin: problem reports | `/admin/reports` (admins only) | `GET /api/v1/admin/reports?status=OPEN\|IN_PROGRESS\|RESOLVED\|REJECTED`, `PATCH /api/v1/admin/reports/{id}` {status} |
| Admin: promo stories | `/admin/stories` (admins only; linked from Review) | `GET /api/v1/admin/stories` (live, with views), `POST /api/v1/admin/stories?hours=1..168` multipart `file` (image or video ≤ 10 MB), `DELETE /api/v1/admin/stories/{id}` |
| Profile | `/settings/profile` | `GET /api/v1/profile/me` prefills (`photoUrl`: 10-minute link), `POST /api/v1/profile`; photo: `POST /api/v1/profile/photo` multipart `file` (image ≤ 5 MB; 409 PROFILE_REQUIRED before the first save, 413 PHOTO_TOO_LARGE), `DELETE /api/v1/profile/photo`; links to email, PIN, devices, verification, report, Terms, Privacy |
| Change email | `/settings/email` (link on Profile) | `POST /api/v1/profile/email` {email, pin} (403 INVALID_PIN / PIN_NOT_SET → PIN setup, 409 EMAIL_TAKEN, 429 OTP_SEND_TOO_FREQUENT / EMAIL_CHANGE_LIMIT / PIN_LOCKED + Retry-After) → `POST /api/v1/profile/email/confirm` {otp, currentEmailOtp} (400 INVALID_OTP for either code; OTP_EXPIRED, 429 TOO_MANY_ATTEMPTS, 409 EMAIL_TAKEN → start again). Success signs out every device, this tab too |
| Change PIN | `/settings/pin` (link on Profile) | `POST /api/v1/change-pin` (429 OTP_SEND_TOO_FREQUENT, 60 s) → `/{flowId}/otp` {otp} (400 INVALID_OTP / OTP_EXPIRED, 429 TOO_MANY_ATTEMPTS) → `/{flowId}/pin` {newPin, reEnterNewPin} (400 PIN_UNCHANGED); 410 FLOW_EXPIRED / 404 / 409 INVALID_STEP → start again. No current PIN asked: the email code is the proof |
| Devices (sessions) | `/settings/sessions` (link on Profile) | `GET /api/v1/auth/sessions` (device named from the User-Agent), `DELETE /api/v1/auth/sessions/{id}` (404: already gone, counts as done), `DELETE /api/v1/auth/sessions` (sign out everywhere, asks first) |
| Verify identity | `/settings/verify` (link on Profile) | `GET /api/v1/kyc/me` (NOT_SUBMITTED / PENDING / APPROVED / REJECTED + reason), `POST /api/v1/kyc?documentType=` multipart `document` (JPEG/PNG/WebP ≤ 5 MB; 409 KYC_ALREADY_PENDING / KYC_ALREADY_VERIFIED, 413 DOCUMENT_TOO_LARGE, 429 KYC_SUBMIT_LIMIT + Retry-After, 503 MEDIA_UPLOAD_FAILED) |
| Admin: identity reviews | `/admin/kyc`, `/admin/kyc/:id` (admins only; others get 404; "Review" in the header) | `GET /api/v1/admin/kyc?status=&page=&size=`, `GET /api/v1/admin/kyc/{id}` (5-minute photo link), `POST /api/v1/admin/kyc/{id}/approve`, `POST /api/v1/admin/kyc/{id}/reject` {reason} (403 KYC_OWN_SUBMISSION, 409 KYC_ALREADY_REVIEWED) |
| Report a problem | `/help/report` (link on Profile) | `POST /api/v1/reports` {category, message ≤ 2000, referenceId?} → report number |
| Terms / Privacy | `/legal/terms`, `/legal/privacy` (public; linked from sign-in and Profile) | `GET /api/v1/legal/terms`, `GET /api/v1/legal/privacy` (404 → not published yet) |
| Accept updated Terms/Privacy | shown by the app shell instead of the page while `GET /api/v1/auth/me` lists `pendingLegalDocuments` | `POST /api/v1/legal/acceptances` {documents} (409 LEGAL_VERSION_OUTDATED → re-read /me). Until accepted, transfers and payments answer 403 LEGAL_ACCEPTANCE_REQUIRED |

## System states

| State | Behaviour |
|---|---|
| Session expired | 401 → one refresh (`POST /api/v1/auth/refresh-token`) → retry; if that fails → sign-in with a notice, and back to the same page after signing in |
| Signed out by the user | sign-out, sign out everywhere, email change: the next sign-in opens home |
| Service unavailable | 503 SERVICE_UNAVAILABLE / MEDIA_UNAVAILABLE → retry banner, honours Retry-After |
| Not found | `/404` page for unknown routes and 404 detail calls |
| Error | `/500`-style error boundary with a retry button |
| Loading / empty | every list and form has loading, empty and error states |

## Backend gaps (no UI until fixed)

| Gap | Effect | Task |
|---|---|---|
| A user cannot list their own transfers, payments or transactions: `GET /api/v1/transfer`, `/api/v1/transaction`, `/api/v1/payment/history` are admin-only | no activity list on dashboard or card detail | closed by T-073: `GET /api/v1/cards/me/activity` (ledger entries, no counterparty yet) |
| No way to fund a card | — | closed by T-064: Stripe Checkout top-up, credited by the signed webhook (needs STRIPE_SECRET_KEY and STRIPE_WEBHOOK_SECRET) |
| Transfer and payment took the full 16-digit `fromCardNumber` | — | closed by T-112: `fromCardId` |
| No endpoint to read one's own profile (`GET /api/v1/profile` is admin-only, `/api/v1/auth/me` has only id, username, roles) | profile form cannot be prefilled | T-113 |
| No KYC review / admin API | — | closed by T-070, T-071: document upload to a private bucket, admin review |
