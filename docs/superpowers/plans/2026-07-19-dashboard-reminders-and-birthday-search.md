# Dashboard Reminders and Birthday Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add birthday-month customer search, home-only service counts, precise expiration and unpaid-rent reminders, and independent notification pagination.

**Architecture:** Extend the existing dashboard and customer query APIs without adding tables. Date-driven reminders are derived in `CmsQueryService` from latest contracts and rent payment coverage; Angular receives complete notification arrays and paginates each section independently in component state.

**Tech Stack:** Java 17, Spring Boot, JdbcTemplate, JUnit/MockMvc, Angular standalone component, TypeScript, SCSS.

## Global Constraints

- Keep existing routes unchanged.
- Birthday filters compare month only and support existing stored date formats.
- Dashboard KPI and notification UI appears only on `/home`.
- Notification sections paginate independently with five records per page.
- Incomplete contracts and unpaid rent must never duplicate the same contract.
- Do not add an accounts-receivable table in this change.

---

### Task 1: Customer Birthday Month Filters

**Files:**
- Modify: `backend/src/test/java/com/example/cms/CmsApplicationTests.java`
- Modify: `backend/src/main/java/com/example/cms/controller/CmsController.java`
- Modify: `backend/src/main/java/com/example/cms/service/CmsQueryService.java`
- Modify: `frontend/src/app/core/cms-api.service.ts`
- Modify: `frontend/src/app/app.component.ts`
- Modify: `frontend/src/app/app.component.html`

**Interfaces:**
- Consumes: `GET /api/customers` and the existing `customers(...)` query method.
- Produces: optional `ownerBirthdayMonth` and `contactBirthdayMonth` request parameters with integer values 1 through 12.

- [ ] **Step 1: Write failing MockMvc tests**

Insert customers with birthday values in Gregorian, slash-separated, and ROC formats. Request `/api/customers?ownerBirthdayMonth=8` and `/api/customers?contactBirthdayMonth=9`, asserting only matching companies are returned. Add one request combining a birthday month with `companyName`.

- [ ] **Step 2: Run the focused test and verify RED**

Run: `..\tools\apache-maven-3.9.9\bin\mvn.cmd -Dtest=CmsApplicationTests#customerSearchFiltersByBirthdayMonth test`

Expected: FAIL because the controller does not accept the month parameters and the query does not filter birthdays.

- [ ] **Step 3: Implement month parsing and query filtering**

Add nullable month parameters to the controller and service. Validate the range 1 through 12. Use the service's existing date parser to compare month values after the SQL query so all supported formats behave consistently and malformed dates simply do not match.

- [ ] **Step 4: Add frontend filters**

Extend `CustomerSearchFilters` with nullable month fields, include them in `HttpParams`, reset them in `resetCustomerFilters()`, and add two month selects with options 1 through 12 to the customer filter bar.

- [ ] **Step 5: Run the focused test and frontend build**

Run: `..\tools\apache-maven-3.9.9\bin\mvn.cmd -Dtest=CmsApplicationTests#customerSearchFiltersByBirthdayMonth test`

Run: `npm run build`

Expected: PASS and Angular build success.

### Task 2: Dashboard Counts and Reminder Rules

**Files:**
- Modify: `backend/src/test/java/com/example/cms/CmsApplicationTests.java`
- Modify: `backend/src/main/java/com/example/cms/service/CmsQueryService.java`
- Modify: `frontend/src/app/core/cms-api.service.ts`

**Interfaces:**
- Consumes: latest contract per customer, `contracts.payment_months`, `contracts.rent`, and `rent_payments.fee_end_date_text`.
- Produces: `officeCustomers`, `registrationCustomers`, revised `expiringContracts`, new `notifications.unpaidRent`, and existing `incompleteContracts`.

- [ ] **Step 1: Write failing dashboard tests**

Add tests proving: service counts use only latest contracts; expiration begins 45 days before end; an old renewed contract is excluded; inactive contracts are excluded; an unpaid reminder begins 30 days before the day after latest fee coverage; adding coverage removes it; and a contract with no payment appears only under incomplete contracts.

- [ ] **Step 2: Run dashboard tests and verify RED**

Run: `..\tools\apache-maven-3.9.9\bin\mvn.cmd -Dtest=CmsApplicationTests#dashboard* test`

Expected: FAIL for missing counts, old expiration window, and missing unpaid-rent array.

- [ ] **Step 3: Implement latest-contract counts**

Count distinct latest contracts whose `rental_status` is `辦公室` or `登記+辦公室` for office customers and `登記` or `登記+辦公室` for registration customers.

- [ ] **Step 4: Implement revised expiration reminders**

Query only each customer's maximum contract ID with active lease status. Parse end dates and include dates from today through today plus 45 days. Return `rental_item` instead of `lease_status`.

- [ ] **Step 5: Implement unpaid-rent reminders**

For each latest active contract with valid end date and at least one payment, parse the greatest valid fee end date, calculate next period start as plus one day, and include it when today is on or after next start minus 30 days and next start is on or before contract end. Return customer and contract IDs, company name, next period start, payment months, and rent multiplied by payment months.

- [ ] **Step 6: Run dashboard tests and full backend suite**

Run: `..\tools\apache-maven-3.9.9\bin\mvn.cmd test`

Expected: all tests pass.

### Task 3: Home-Only KPI and Notification Pagination

**Files:**
- Modify: `frontend/src/app/app.component.ts`
- Modify: `frontend/src/app/app.component.html`
- Modify: `frontend/src/app/app.component.scss`

**Interfaces:**
- Consumes: updated `Dashboard` interface from Task 2.
- Produces: two home KPI cards and three independently paginated notification sections.

- [ ] **Step 1: Add independent pagination state**

Add a page size of 5 and page signals for expiration, unpaid rent, and incomplete contracts. Add typed slice, total-page, previous, and next helpers. Clamp pages to valid ranges whenever dashboard data reloads.

- [ ] **Step 2: Restrict KPI rendering to home**

Move the KPI section under the home view condition. Render only `officeCustomers` and `registrationCustomers`; remove the four old KPI cards.

- [ ] **Step 3: Update notification content**

Render expiration rental item, add the unpaid-rent section and reconciliation action, and keep incomplete contracts as the third section. Each list uses its paginated slice.

- [ ] **Step 4: Add paginator styling and responsive checks**

Use compact previous/next icon or text controls, current/total page text, stable button dimensions, and responsive notification layout without nested cards. Set each notification section to align its internal grid content at the start so all three headings share the same top edge even when a section is empty.

- [ ] **Step 5: Build and browser-test**

Run: `npm run build`

Check `/home` and `/customers` at desktop and mobile widths. Confirm KPI cards disappear outside home, each notification section pages independently, empty states render, and birthday filters issue the expected requests.

### Task 4: Final Regression Verification and Commit

**Files:**
- Verify all modified files.

**Interfaces:**
- Consumes: all deliverables from Tasks 1 through 3.
- Produces: one tested feature commit.

- [ ] **Step 1: Run clean verification**

Run backend `..\tools\apache-maven-3.9.9\bin\mvn.cmd test`, frontend `npm run build`, `git diff --check`, and live HTTP checks for ports 8080 and 4200.

- [ ] **Step 2: Inspect browser console and layouts**

Confirm there are no new runtime errors and no horizontal overflow on home and customer search.

- [ ] **Step 3: Commit**

```bash
git add backend frontend docs/superpowers/plans/2026-07-19-dashboard-reminders-and-birthday-search.md
git commit -m "feat: improve dashboard reminders and birthday search"
```
