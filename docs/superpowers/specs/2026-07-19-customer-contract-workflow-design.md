# Customer and Contract Workflow Design

## Scope

This change improves customer onboarding, contract renewal, first-payment tracking, and dashboard follow-up without changing the existing Angular routes. The existing `/contracts/new` route remains in place while its user-facing label and behavior become "續約租約".

## Customer Data

Add optional customer fields for free-text account information, agency handling, contact birthday, and accountant information. Existing `referrer` values migrate to `accountant_info`; the UI consistently uses "聯絡人" and "會計師資訊".

Related companies use optional groups. A customer without related companies has no group. A group contains the current customer plus zero or more resolved or unresolved company members. Unresolved members store a normalized company name with a null customer ID. When a customer with that exact company name is later created, the unresolved member links to the new customer. Group members are displayed bidirectionally, excluding the customer currently being viewed.

## Contract Data and Renewal

Add `source_text` and nullable `partner_staff_id` to contracts. The partner is selected by staff name, stored by staff ID, and cannot equal the signer. Rental item options include "停業" and rental status options include "個人名義".

The existing `/contracts/new` page is renamed "續約租約". Selecting a customer loads the contract with the greatest `contract_id` and pre-fills reusable fields: rental item, rental status, office, payment months, rent, deposit, signer, partner, and source. Dates, termination date, lease image, and first-payment data remain blank.

## First Payment

The integrated customer-and-contract form accepts optional `firstPaymentAmount` and `firstPaymentDateText`. Both must be supplied together or both omitted. When present, the same database transaction creates a `rent_payments` row linked to the new customer and contract. Payment state is never duplicated on `contracts`.

## Dashboard Notifications

Remove the monthly birthday notification. Add `incompleteContracts`, derived from contracts that have no related `rent_payments` row. The reminder appears immediately after contract creation and disappears after the first payment is recorded.

Each reminder returns customer name, signed date, signer, start date, rent, customer ID, and contract ID. The "新增對帳" action navigates to the existing `/rent-payments/new` route with query parameters so the form can preselect the customer and contract.

## Validation and Error Handling

- Company name and existing required contract fields remain required.
- Partner is optional but cannot equal signer.
- First-payment amount and date must be entered together.
- Related-company names are trimmed, cannot equal the current company name, and are deduplicated case-insensitively within a group.
- Customer, contract, related-company group, optional payment, and optional lease image path are created transactionally.
- A missing or ambiguous unresolved company match remains unresolved rather than linking to the wrong customer.

## Verification

Backend integration tests cover schema fields, transactional first-payment creation, unpaid-contract notifications, notification disappearance after payment, partner validation, renewal latest-contract data, and bidirectional related-company groups. Frontend compilation verifies the updated models, labels, fields, prefill flow, and dashboard action. Existing backend tests and the Angular production build must pass.
