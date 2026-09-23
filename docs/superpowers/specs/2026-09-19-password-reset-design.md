# Password Reset Design

## Goal

Allow a CMS staff member to securely reset a forgotten password through a time-limited link delivered to the work email recorded for that staff account.

## Scope and decisions

- The recovery channel is a work email maintained by a supervisor. New registrations must provide a valid email address.
- Existing staff records keep their current role but require a supervisor to add an email in **職員總覽** before they can use recovery.
- A reset request accepts either account or email, but always returns HTTP 202 with the same generic message. It never reveals whether an account or email exists.
- The backend creates 32 random bytes with `SecureRandom`, returns the raw value only in the email link, and stores only its SHA-256 hash.
- A token belongs to one staff member, expires after 30 minutes, may be used once, and supersedes all prior unused tokens for that staff member.
- The reset endpoint requires a matching new-password confirmation and an eight-character minimum. Passwords remain BCrypt hashes.
- The reset screen does not authenticate the user. It shows a success state and takes the user back to ordinary login.
- The reset URL is derived from configured `cms.password-reset.app-base-url`, never from a request header. It uses the SPA query parameter `resetToken`; the browser removes this parameter from its visible URL immediately after reading it.
- SMTP is configured only through environment variables. No credential, reset token, or password is logged.

## Data model

`staff` gains nullable `email VARCHAR(254)` with a unique index. The value is required for registration, and supervisors may add or update it later.

`password_reset_tokens` contains:

- `password_reset_token_id` — primary key.
- `staff_id` — foreign key to `staff`.
- `token_hash CHAR(64)` — SHA-256 hex digest; never the raw token.
- `expires_at`, `used_at`, and `created_at` timestamps.

## Backend contract

`POST /api/auth/password-reset-requests`

```json
{ "identifier": "manager" }
```

Returns `202 Accepted` and `{ "message": "若帳號資料存在，重設說明已寄送至註冊信箱。" }` whether or not a deliverable account exists.

`POST /api/auth/password-resets`

```json
{
  "token": "raw-token-from-email",
  "password": "new-password",
  "confirmPassword": "new-password"
}
```

Returns 204 when the token is valid and the password is changed. Expired, used, invalid, mismatched, or weak inputs return 400 without changing the account.

`PUT /api/staff/{id}` accepts both `rolePermissionId` and `email`. Only the established supervisor UI invokes it. A duplicate email returns 409.

## Delivery and configuration

Spring Mail sends a simple plain-text message from `CMS_PASSWORD_RESET_FROM`. Runtime configuration uses `CMS_SMTP_HOST`, `CMS_SMTP_PORT`, `CMS_SMTP_USERNAME`, `CMS_SMTP_PASSWORD`, and `CMS_APP_BASE_URL`. The email includes the 30-minute validity, a fixed app URL, and no password.

When SMTP cannot deliver, the API preserves the generic 202 response to avoid account enumeration; the server logs the operational failure without personal tokens or credentials.

## UI and accessibility

The existing warm neutral CMS login card becomes a three-state flow: sign in, request reset, and set new password. Each form has visible labels, helpful copy, an in-place status message, disabled/loading submit state, a clear back action, keyboard-visible focus, password show/hide controls, and 44px minimum form controls. The reset form only appears when a token is present. No structural icons or extra UI framework are added.

## Tests

- Backend integration tests prove that a known staff email receives a link, only a hash is stored, and an unknown identifier returns the same accepted response without creating a token.
- A backend integration test extracts the token from the mocked external mail sender, resets the password, proves old credentials fail and new credentials succeed, and proves replay fails.
- A backend integration test proves expired tokens cannot change a password.
- Frontend component tests prove the recovery form is reachable, its loading state resets on completion, and successful reset returns the user to login.
- The full backend suite, frontend test suite, production frontend build, and responsive visual checks are run before handoff.
