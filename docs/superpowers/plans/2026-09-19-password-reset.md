# Password Reset Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add secure email-link password recovery and the Email data needed for CMS staff to use it.

**Architecture:** The Spring backend persists a hashed, single-use recovery token and sends the raw token exclusively through SMTP. The Angular single-page login card reads the reset-link query parameter into short-lived UI state, then submits the new password to the backend. Staff email is managed through the existing staff API and overview.

**Tech Stack:** Spring Boot 3.3, JdbcTemplate, Spring Mail, H2/MySQL, Angular 18, Jasmine/Karma, SCSS.

**Spec:** `docs/superpowers/specs/2026-09-19-password-reset-design.md`

## Global Constraints

- Do not store raw reset tokens, passwords, SMTP secrets, or reset links in logs or the database.
- Use a 32-byte `SecureRandom` URL-safe token; store SHA-256 hex; expire after exactly 30 minutes.
- Return the identical 202 request response for an unknown account, email, empty email, or valid account.
- Store changed passwords with existing BCrypt; require matching passwords of at least eight characters.
- Retain the existing warm neutral CSS tokens, visible labels, keyboard focus, mobile layout, and `prefers-reduced-motion` support.

---

### Task 1: Persist staff email and recovery tokens

**Files:**
- Modify: `backend/src/main/resources/schema.sql`
- Modify: `backend/src/main/resources/data/seed-data.json`
- Modify: `backend/src/main/java/com/example/cms/config/SeedDataLoader.java`
- Modify: `backend/src/main/java/com/example/cms/dto/RegisterRequest.java`
- Modify: `backend/src/main/java/com/example/cms/dto/StaffUpdateRequest.java`
- Modify: `backend/src/main/java/com/example/cms/service/StaffService.java`
- Test: `backend/src/test/java/com/example/cms/CmsApplicationTests.java`

**Interfaces:**
- Produces staff `email` values, a `password_reset_tokens` table, and `StaffUpdateRequest(Long rolePermissionId, String email)`.
- Consumes the existing `staff` table and staff overview endpoint.

- [ ] **Step 1: Write the failing staff-email integration test**

```java
mvc.perform(put("/api/staff/1").contentType("application/json")
        .content("{\"rolePermissionId\":1,\"email\":\"manager@cms.test\"}"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.email", is("manager@cms.test")));
```

- [ ] **Step 2: Run `mvn test -Dtest=CmsApplicationTests#staffEmailCanBeMaintained` and confirm it fails because email is absent.**
- [ ] **Step 3: Add the schema, seed, DTO, and JdbcTemplate email support.**
- [ ] **Step 4: Re-run the targeted test and confirm it passes.**
- [ ] **Step 5: Commit the persistence unit with `feat: store staff recovery emails`.**

### Task 2: Implement protected reset request and confirmation APIs

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/resources/application-test.yml`
- Create: `backend/src/main/java/com/example/cms/dto/PasswordResetRequest.java`
- Create: `backend/src/main/java/com/example/cms/dto/PasswordResetConfirmationRequest.java`
- Create: `backend/src/main/java/com/example/cms/service/PasswordResetMailService.java`
- Create: `backend/src/main/java/com/example/cms/service/SmtpPasswordResetMailService.java`
- Modify: `backend/src/main/java/com/example/cms/controller/AuthController.java`
- Modify: `backend/src/main/java/com/example/cms/service/AuthService.java`
- Test: `backend/src/test/java/com/example/cms/CmsApplicationTests.java`

**Interfaces:**
- Consumes `PasswordResetMailService.sendResetLink(String email, String link)` and `sendPasswordChangedNotice(String email)`.
- Produces `POST /api/auth/password-reset-requests` and `POST /api/auth/password-resets`.

- [ ] **Step 1: Write failing integration tests for generic request responses, hashed storage, successful reset/replay rejection, and expiry rejection.**

```java
mvc.perform(post("/api/auth/password-reset-requests").contentType("application/json")
        .content("{\"identifier\":\"manager\"}"))
    .andExpect(status().isAccepted());
```

- [ ] **Step 2: Run `mvn test -Dtest=CmsApplicationTests#passwordResetRequestCreatesOnlyHashedToken` and confirm it fails with a missing route.**
- [ ] **Step 3: Add Spring Mail, configuration properties, the mockable mail boundary, and SecureRandom/SHA-256 token logic.**
- [ ] **Step 4: Add confirmation validation, BCrypt replacement, token use marking, and mail notice.**
- [ ] **Step 5: Re-run reset tests and the full backend suite.**
- [ ] **Step 6: Commit the API unit with `feat: add email password reset APIs`.**

### Task 3: Add front-end API contracts and behavior tests

**Files:**
- Modify: `frontend/src/app/core/cms-api.service.ts`
- Modify: `frontend/src/app/app.component.ts`
- Modify: `frontend/src/app/app.component.spec.ts`

**Interfaces:**
- Consumes `CmsApiService.requestPasswordReset({ identifier })` and `resetPassword({ token, password, confirmPassword })`.
- Produces auth modes `login`, `register`, `forgot`, and `reset` plus request/reset form state.

- [ ] **Step 1: Write failing component tests for moving to recovery, clearing request busy state, and returning to login after reset.**

```typescript
component.openForgotPassword();
expect(component.authMode()).toBe('forgot');
```

- [ ] **Step 2: Run `npm test -- --watch=false --include=src/app/app.component.spec.ts` and confirm it fails because `openForgotPassword` is absent.**
- [ ] **Step 3: Add typed API methods, form state, token query extraction/removal, validation, and subscribers.**
- [ ] **Step 4: Re-run focused Angular tests and confirm they pass.**
- [ ] **Step 5: Commit the behavior unit with `feat: add password reset client flow`.**

### Task 4: Build the recovery and staff-email UI

**Files:**
- Modify: `frontend/src/app/app.component.html`
- Modify: `frontend/src/app/app.component.scss`
- Modify: `frontend/src/app/app.component.ts`
- Modify: `frontend/src/app/core/cms-api.service.ts`
- Modify: `frontend/src/app/app.component.spec.ts`

**Interfaces:**
- Consumes Task 3 auth state and existing CSS `--brand-*` variables.
- Produces visible login recovery forms and a supervisor-editable Email column in staff overview.

- [ ] **Step 1: Write a failing DOM test for the visible recovery button and heading.**

```typescript
expect((fixture.nativeElement as HTMLElement).textContent).toContain('忘記密碼');
```

- [ ] **Step 2: Run the focused test and confirm it fails because recovery controls are absent.**
- [ ] **Step 3: Add three compact auth panels with labeled controls, inline feedback, disabled buttons while saving, visible focus styles, and 44px controls.**
- [ ] **Step 4: Add the email column/input to staff overview and pass the role plus edited email to the update endpoint.**
- [ ] **Step 5: Re-run frontend tests and build production assets.**
- [ ] **Step 6: Commit the UI unit with `feat: add password recovery interface`.**

### Task 5: Verify configuration, security paths, and responsive UI

**Files:**
- Modify: `README.md`
- Test: `backend/src/test/java/com/example/cms/CmsApplicationTests.java`
- Test: `frontend/src/app/app.component.spec.ts`

**Interfaces:**
- Documents `CMS_SMTP_*`, `CMS_PASSWORD_RESET_FROM`, and `CMS_APP_BASE_URL`.

- [ ] **Step 1: Add the environment setup and supervisor email migration instructions to the README.**
- [ ] **Step 2: Run `mvn test`, `npm test -- --watch=false`, and `npm run build`.**
- [ ] **Step 3: Run the app locally and inspect login, request, and reset states at 390px and desktop width, including focus and reduced-motion behavior.**
- [ ] **Step 4: Inspect `git diff --check` and commit final documentation with `docs: document password reset configuration`.**
