# Customer and Contract Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add renewal prefill, optional related-company groups, customer and contract fields, transactional first payments, and unpaid-contract dashboard reminders while keeping `/contracts/new` unchanged.

**Architecture:** Extend the existing SQL/JdbcTemplate backend with normalized related-company group tables and derived payment status. Keep the existing Angular `AppComponent` navigation architecture and extend its forms and API models without unrelated component restructuring.

**Tech Stack:** Java 17, Spring Boot, JdbcTemplate, H2/MySQL, JUnit/MockMvc, Angular 18, TypeScript.

## Global Constraints

- Keep the Angular route `/contracts/new` unchanged.
- Display "續約租約" to users instead of "新增租約".
- Store actual payments only in `rent_payments`, never as a contract payment flag.
- Keep office selection optional.
- Store staff relationships by staff ID while displaying staff names.

---

### Task 1: Backend behavior tests

**Files:**
- Modify: `backend/src/test/java/com/example/cms/CmsApplicationTests.java`

**Interfaces:**
- Consumes: existing `/api/customers/with-contract`, `/api/contracts`, `/api/dashboard`, and `/api/rent-payments` endpoints.
- Produces: failing integration tests that define the new DTO and response contracts.

- [ ] Add a test posting customer fields, related-company names, contract source/partner, and paired first-payment fields; assert all linked records exist.
- [ ] Add a test proving one missing first-payment field returns HTTP 400 and rolls back all records.
- [ ] Add a test proving signer and partner cannot be the same staff member.
- [ ] Add a test proving related companies display bidirectionally and unresolved names link after the matching customer is created.
- [ ] Add a test proving dashboard `incompleteContracts` contains unpaid contracts, omits paid contracts, and has no `ownerBirthdays` key.
- [ ] Add a test for `GET /api/customers/{id}/latest-contract` returning the greatest contract ID.
- [ ] Run `..\tools\apache-maven-3.9.9\bin\mvn.cmd test` and verify the new tests fail because the new schema/API behavior is absent.

### Task 2: Schema, DTOs, and migrations

**Files:**
- Modify: `backend/src/main/resources/schema.sql`
- Modify: `backend/src/main/java/com/example/cms/config/SchemaMigrationRunner.java`
- Modify: `backend/src/main/java/com/example/cms/dto/CustomerRequest.java`
- Modify: `backend/src/main/java/com/example/cms/dto/ContractRequest.java`
- Modify: `backend/src/main/java/com/example/cms/dto/CustomerWithContractRequest.java`

**Interfaces:**
- Produces: customer fields `accountInfo`, `isAgent`, `contactBirthday`, `accountantInfo`, `relatedCompanyNames`; contract fields `sourceText`, `partnerStaffId`; integrated fields `firstPaymentAmount`, `firstPaymentDateText`.

- [ ] Add nullable customer and contract columns and foreign key definitions.
- [ ] Add `customer_relation_groups` and `customer_relation_members` with nullable customer links and group/name uniqueness.
- [ ] Add idempotent migrations that preserve existing data and copy `referrer` into `accountant_info`.
- [ ] Extend Java records with the exact request fields defined above.
- [ ] Run the focused backend tests and verify schema initialization succeeds while service behavior tests remain red.

### Task 3: Backend customer, renewal, and relation behavior

**Files:**
- Modify: `backend/src/main/java/com/example/cms/controller/CmsController.java`
- Modify: `backend/src/main/java/com/example/cms/service/CmsQueryService.java`

**Interfaces:**
- Produces: `GET /api/customers/{id}/latest-contract`; customer detail `relatedCompanies`; contract rows with `partner_staff_name` and `source_text`.

- [ ] Extend customer create/update/detail SQL for the new fields.
- [ ] Create relation groups only when related names are supplied; deduplicate names and link exact unique customer matches.
- [ ] Resolve previously unresolved group members after creating a matching customer.
- [ ] Return every other group member from customer detail, including unresolved names.
- [ ] Return latest contract by descending `contract_id` with office, signer, partner, and source display fields.
- [ ] Reject equal signer and partner IDs.
- [ ] Run the focused backend tests and verify renewal, relation, and validation tests pass.

### Task 4: Transactional first payment and dashboard

**Files:**
- Modify: `backend/src/main/java/com/example/cms/service/CmsQueryService.java`
- Modify: `backend/src/main/java/com/example/cms/controller/CmsController.java`

**Interfaces:**
- Consumes: integrated request first-payment amount/date.
- Produces: dashboard `notifications.incompleteContracts` and no birthday notification data.

- [ ] Validate first-payment amount/date as a pair before writing customer data.
- [ ] Create the optional first rent-payment row in the existing customer/contract transaction.
- [ ] Replace birthday notification generation with unpaid-contract query using `NOT EXISTS` on `rent_payments`.
- [ ] Return customer and contract IDs required by the frontend "新增對帳" action.
- [ ] Run all backend tests and verify they pass.

### Task 5: Frontend API models and renewal behavior

**Files:**
- Modify: `frontend/src/app/core/cms-api.service.ts`
- Modify: `frontend/src/app/app.component.ts`

**Interfaces:**
- Consumes: new customer, latest-contract, related-company, and dashboard API fields.
- Produces: form state, validation, renewal prefill, and query-parameter preselection for rent entry.

- [ ] Extend TypeScript payload/detail/dashboard interfaces.
- [ ] Add API method for latest contract and integrated multipart payment fields.
- [ ] Rename navigation label to "續約租約" while retaining `contract-new` and `/contracts/new`.
- [ ] Add "停業" and "個人名義" options.
- [ ] On customer selection, fetch latest contract and prefill reusable fields while clearing dates and image/payment data.
- [ ] Enforce signer/partner inequality and paired first-payment fields before submission.
- [ ] Read customer/contract query parameters when opening `/rent-payments/new` and preselect the matching values.

### Task 6: Frontend templates and styles

**Files:**
- Modify: `frontend/src/app/app.component.html`
- Modify: `frontend/src/app/app.component.scss`

**Interfaces:**
- Consumes: form state and actions from Task 5.
- Produces: accessible customer fields, repeatable related-company inputs, renewal page, and dashboard reminder UI.

- [ ] Replace every customer label "連絡人" with "聯絡人" and "推薦人" with "會計師資訊".
- [ ] Add account information, agency checkbox, contact birthday, and repeatable related-company controls with plus and remove icons.
- [ ] Add source and searchable single-partner controls; show validation when partner equals signer.
- [ ] Add optional first-payment amount/date controls to integrated customer creation.
- [ ] Remove the monthly birthday notification section.
- [ ] Add the unpaid-contract list with "新增對帳" actions.
- [ ] Rename the contract creation page and submit button to "續約租約".
- [ ] Keep responsive grid widths stable for the added controls.

### Task 7: SQL documentation, seed data, and final verification

**Files:**
- Modify: `table.txt`
- Modify: `backend/src/main/resources/data/seed-data.json`
- Modify: `backend/src/main/java/com/example/cms/config/SeedDataLoader.java`

**Interfaces:**
- Produces: fresh MySQL setup and demo data aligned with runtime schema.

- [ ] Update MySQL create/drop/insert statements for all new fields and relation tables.
- [ ] Extend compact seed records and loader inserts without creating invalid signer/partner pairs.
- [ ] Run `..\tools\apache-maven-3.9.9\bin\mvn.cmd test` from `backend` and confirm zero failures.
- [ ] Run `npm run build` from `frontend` and confirm exit code 0.
- [ ] Verify `http://localhost:8080/api/dashboard` and `http://localhost:4200/contracts/new` after restarting the local servers.
- [ ] Review `git diff --check` and `git status --short` for unintended files.
