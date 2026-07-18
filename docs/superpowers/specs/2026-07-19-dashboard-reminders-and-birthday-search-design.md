# Dashboard Reminders and Birthday Search Design

## Scope

This change improves customer birthday search and limits dashboard summary and notification content to information that users can act on. Existing routes and rent reconciliation workflows remain unchanged.

## Customer Birthday Search

- Add an owner birthday month filter and a contact birthday month filter to customer search.
- Each filter is a month selector with values 1 through 12.
- Birthday filtering compares only the month portion of the stored birthday and ignores the year and day.
- Either birthday filter can be used independently or combined with all existing customer filters.
- Existing supported date formats continue to apply.

## Dashboard Summary

- Render the KPI summary only on the home view.
- Remove customer count, active contract count, rent payment count, and total rent amount from the UI.
- Add these two summary values:
  - Office customers: customers whose latest contract rental status is `辦公室` or `登記+辦公室`.
  - Registration customers: customers whose latest contract rental status is `登記` or `登記+辦公室`.
- A customer using both services appears in both values.
- Only the latest contract per customer is considered so historical contracts do not create duplicate counts.
- No placeholder KPI cards are shown for metrics that have not been confirmed.

## Contract Expiration Reminders

- Start showing an expiration reminder 45 days before the contract end date.
- Stop showing a reminder after the end date.
- Only the latest contract for each customer can produce an expiration reminder.
- The latest contract must have lease status `綁約中`.
- A renewed contract therefore replaces the old contract in reminder calculations.
- Ended or terminated contracts do not produce reminders.
- Each reminder displays customer name, end date, and rental item instead of lease status.

## Incomplete Contract Reminders

- Keep the existing definition: a latest active contract with no rent payment record is incomplete.
- Display customer name, signed date, signer name, and rent.
- The action remains `新增對帳` and opens the existing reconciliation page with the customer and contract preselected.

## Unpaid Rent Reminders

- An unpaid-rent reminder applies only to a latest active contract that already has at least one rent payment.
- Determine the latest covered date from the greatest valid `rent_payments.fee_end_date_text` for that contract.
- The next period starts on the day after that latest covered date.
- Start showing the reminder 30 days before the next period starts and keep it visible until a payment record covers that period.
- Do not show an unpaid-rent reminder when the next period starts after the contract end date.
- Contracts with no payment stay exclusively in the incomplete-contract section and do not appear in unpaid rent.
- Each reminder displays customer name, next period start date, payment months, and suggested amount.
- Suggested amount is `contract.rent * payment_months` when both values are available.
- The `新增對帳` action opens the existing reconciliation page with customer, contract, and suggested amount preselected.

## Notification Pagination

- Expiring contracts, unpaid rent, and incomplete contracts each have an independent paginator.
- Each page displays five reminders.
- Each paginator shows current page, total pages, previous, and next controls.
- Controls are disabled when the current page has no previous or next page.
- Reloading dashboard data clamps page numbers back into the available range.
- Empty sections show a clear empty state and no paginator.
- Pagination is client-side because dashboard notification lists are currently loaded together and the requirement is to prevent excessive vertical rendering.

## API Shape

`GET /api/dashboard` retains the existing response and adds or changes:

- `officeCustomers`
- `registrationCustomers`
- `notifications.expiringContracts[].rental_item`
- `notifications.unpaidRent[]` with customer ID, contract ID, company name, next period start, payment months, and suggested amount
- `notifications.incompleteContracts` remains available

Customer list requests add optional query parameters:

- `ownerBirthdayMonth`
- `contactBirthdayMonth`

## Error and Date Handling

- Records with blank or unparseable birthday values do not match birthday month filters.
- Rent records with blank or unparseable fee end dates are ignored when determining the latest covered date.
- A contract with payments but no valid covered date does not produce an unpaid-rent reminder because its next due period cannot be determined reliably.
- Existing supported Gregorian, slash-separated, and ROC date formats remain supported.

## Verification

- Backend tests cover birthday month filters and their combination with existing filters.
- Backend tests cover dashboard counts based on latest contracts.
- Backend tests cover the 45-day expiration window, renewed contracts, and inactive contracts.
- Backend tests cover the 30-day unpaid-rent window, payment coverage, contract boundaries, and exclusion of incomplete contracts.
- Frontend production build must pass.
- Browser checks cover home-only KPIs, all three independently paginated notification sections, and birthday month filters.
