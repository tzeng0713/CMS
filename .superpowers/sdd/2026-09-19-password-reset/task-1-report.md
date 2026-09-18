# Task 1 report — Persist staff email and recovery tokens

## Status

Completed and committed as the persistence unit for password recovery emails and reset tokens.

## Changes

- Added nullable `staff.email VARCHAR(254)` and a unique `idx_staff_email` index.
- Added `password_reset_tokens` with a generated primary key, staff foreign key, SHA-256-sized hash field, expiry, use, and creation timestamps.
- Added distinct work-email values to all seeded staff and persisted them in `SeedDataLoader`.
- Added `email` to `RegisterRequest` and `StaffUpdateRequest`.
- Updated staff overview/update JDBC SQL to return email and update it when supplied. `COALESCE` preserves a staff email for existing role-only update callers until the later client work is delivered.
- Added the focused MockMvc integration test `staffEmailCanBeMaintained`.

Registration validation was intentionally not changed, per the preflight ruling in `progress.md`.

## TDD evidence

### Red

1. Added `staffEmailCanBeMaintained`, which performs `PUT /api/staff/1` with `rolePermissionId` and `email`, then expects the email in the JSON response.
2. Ran from `backend`:

```powershell
& '..\..\..\tools\apache-maven-3.9.9\bin\mvn.cmd' test '-Dtest=CmsApplicationTests#staffEmailCanBeMaintained'
```

Result: **BUILD FAILURE**, as expected. The response body had no `email` member, and the assertion failed with:

```text
java.lang.AssertionError: No value at JSON path "$.email"
Caused by: com.jayway.jsonpath.PathNotFoundException: No results for path: $['email']
```

### Green

After the minimal schema, seed, DTO, and JDBC changes, reran the same focused command. It exited successfully with no test failures.

## Verification commands and output

```powershell
& '..\..\..\tools\apache-maven-3.9.9\bin\mvn.cmd' test
```

Result:

```text
Tests run: 34, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

```powershell
git diff --check
```

Result: no whitespace errors (Git emitted only CRLF conversion warnings for modified files).

## Changed files

- `backend/src/main/resources/schema.sql`
- `backend/src/main/resources/data/seed-data.json`
- `backend/src/main/java/com/example/cms/config/SeedDataLoader.java`
- `backend/src/main/java/com/example/cms/dto/RegisterRequest.java`
- `backend/src/main/java/com/example/cms/dto/StaffUpdateRequest.java`
- `backend/src/main/java/com/example/cms/service/StaffService.java`
- `backend/src/test/java/com/example/cms/CmsApplicationTests.java`

## Self-review

- Confirmed the test first failed specifically because email was absent from the overview/update response, then passed after the persistence path was implemented.
- Confirmed SQL exposes email only through the existing staff overview/update flow and preserves legacy role-only updates.
- Confirmed the seeded email values are unique and fit the new constraint.
- Confirmed the recovery-token table stores only a hash-shaped field; no raw token handling was introduced in this task.
- No files outside Task 1, apart from this required task report, were modified.

## Concerns / follow-up

- The unique email index enforces uniqueness at the database layer, but translating a duplicate-key failure to the specified HTTP 409 remains for the API/error-handling work in the later password-reset task.
- Registration accepts the new DTO email field but does not yet require or validate it, intentionally following the preflight ordering ruling.
