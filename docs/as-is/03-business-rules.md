# 3. Business rules & calculation logic

All rules cite the source file and line numbers. Rules are **[Confirmed]**
unless marked otherwise. The billing subsystem is the only place with
non-trivial business logic; standard CRUD validation is listed at the end.

## 3.1 Fee master (unit prices)

Loaded from `src/main/resources/billing/rates.xml` via JAXB and cached in
memory after first load (`service/billing/BillingRateLoader.java:24-47`).

Current fee master values (`src/main/resources/billing/rates.xml:2-12`):

| Pet type | Unit price (JPY) |
| --- | --- |
| dog | 4500 |
| cat | 4000 |
| lizard | 5200 |
| snake | 5200 |
| bird | 3600 |
| hamster | 3000 |
| `*` (wildcard / default) | 3800 |
| currency | `JPY` (attribute) |
| holiday-surcharge-rate | 0.25 |
| closing-day | 25 |

**BR-1 Unit price lookup** — the unit price for a pet type is the first rate
whose `pet-type` matches **case-insensitively**; otherwise the default
(`BillingRates.unitPriceFor`, `service/billing/BillingRates.java:47-54`).

**BR-2 Default unit price** — when no explicit match, the price of the `*`
rate is used; if there is no `*` rate either, a **hardcoded `3000`** is
returned (`BillingRates.java:56-63`). With the current `rates.xml` the `*`
rate (3800) applies, so the hardcoded 3000 is only reachable if `*` is removed.
See open question OQ-6.

**BR-3 Pet type resolution** — a pet with no `PetType` is billed as type `"*"`
(`MonthlyClosingService.java:66`), which resolves to the default unit price via
BR-2.

## 3.2 Holiday surcharge

**BR-4** — for a visit that falls on **Saturday or Sunday**, a surcharge of
`round(unitPrice * holidaySurchargeRate)` is added; otherwise the surcharge is
`0` (`MonthlyClosingService.surchargeFor`,
`service/billing/MonthlyClosingService.java:84-90`). `holidaySurchargeRate` =
`0.25` (`rates.xml:10`, default `0.25` in `BillingRates.java:26`).

- "Weekend" is determined via `Calendar.SATURDAY` / `Calendar.SUNDAY` on a
  `Calendar` created in the **platform default time zone**
  (`LegacyDateFormats.toCalendar`, `LegacyDateFormats.java:75-80`).
- Despite the name "holiday" surcharge, **only Sat/Sun are considered** — no
  public-holiday calendar exists in code. See OQ-8. **[Confirmed]** behavior /
  **[Open question]** intent.
- Rounding uses `Math.round` (half-up toward positive infinity)
  (`MonthlyClosingService.java:87`).
- Example: dog (4500) on a Sunday → surcharge `round(4500 * 0.25)` = `1125`.

## 3.3 Discount (pluggable)

The discount rule is a `DiscountPlugin` implementation whose class name is read
from `billing.properties` key `billing.discount.plugin` and instantiated
**reflectively via its declared no-arg constructor** (which may be non-public);
default is `WeekdayDiscountPlugin`
(`service/billing/DiscountPluginLoader.java:23-58`,
`src/main/resources/billing/billing.properties:3`).

Default rule — `WeekdayDiscountPlugin.discountFor`
(`service/billing/WeekdayDiscountPlugin.java:17-29`):

**BR-5** — on Saturday/Sunday the discount is `0`.

**BR-6** — on a weekday (Mon–Fri) the base discount rate is `0.05` (5%).

**BR-7** — if the pet type is `"lizard"` (case-insensitive) an additional
`0.10` (10%) "campaign" discount is added, i.e. **15%** on weekdays
(`WeekdayDiscountPlugin.java:12,25-27`).

- Discount = `round(unitPrice * rate)` (`WeekdayDiscountPlugin.java:28`).
- The lizard campaign has **no start/end date** in code — it is effectively
  permanent (see OQ-9). Only `lizard` is special-cased; `snake` (same price) is
  not. **[Confirmed]**
- Example: lizard (5200) on a weekday → discount `round(5200 * 0.15)` = `780`.

## 3.4 Line amount, invoice totals, tax

**BR-8 Line amount** — `amount = unitPrice + surcharge - discount`
(`service/billing/InvoiceLine.java:66-68`).

**BR-9 Subtotal** — sum of all line amounts in the invoice
(`service/billing/Invoice.java:56-62`).

**BR-10 Tax** — `tax = round(subtotal * 0.1)`; the **10% rate is hardcoded**
in `Invoice` (`Invoice.java:64-66`). See OQ-7.

**BR-11 Invoice total** — `total = subtotal + tax`
(`Invoice.java:68-70`).

**BR-12 Grand total** — the screen's grand total is the sum of each invoice's
`total` (i.e. tax is applied per invoice, then summed), not tax on the summed
subtotals (`web/BillingController.java:66-72`).

Worked example (weekday, dog, single visit): unit 4500, surcharge 0, discount
`round(4500*0.05)=225` → line 4275 → subtotal 4275 → tax `round(4275*0.1)=428`
→ total 4703.

## 3.5 Closing period selection

**BR-13** — `period` must be formatted `yyyy/MM`; otherwise
`IllegalArgumentException` (`MonthlyClosingService.parsePeriod`,
`MonthlyClosingService.java:92-99`; pattern `LegacyDateFormats.PERIOD_PATTERN`,
`LegacyDateFormats.java:21`).

**BR-14** — the billing window is the **entire calendar month**: `from` =
first day 00:00, `to` = last day 00:00 of that month, in the platform default
time zone (`LegacyDateFormats.startOfMonth`/`endOfMonth`,
`LegacyDateFormats.java:59-73`; called at `MonthlyClosingService.java:45-46`).

**BR-15** — a visit is charged iff `!(visitDate.before(from) ||
visitDate.after(to))` and its date is non-null
(`MonthlyClosingService.java:69-75`). Because `to` is set to `00:00` of the
last day and visit dates are date-only (also `00:00`), the last calendar day
**is** included. **[Confirmed]**

**BR-16** — `closing-day` (25 in `rates.xml:11`) is loaded and logged
(`BillingRateLoader.java:36`) but **never used** in the period computation. The
window is a plain calendar month regardless of `closing-day`. **[Confirmed]
behavior** — intent is OQ-1.

**BR-17 Default period** — when no `period` is supplied to the GET screen, the
current month in the server's default time zone is used
(`BillingController.currentPeriod`, `BillingController.java:45,74-76`).

## 3.6 Invoice scope & identity

**BR-18** — the closing iterates **all owners** (`findOwnerByLastName("")`),
one `Invoice` per owner, and includes only invoices that have at least one line
(`MonthlyClosingService.java:50-59`).

**BR-19** — invoice owner name is composed as `lastName + " " + firstName`
(`MonthlyClosingService.java:52`) and used verbatim in the CSV `顧客名` column
(`InvoiceCsvExporter.java:36`).

**BR-20 CSV row numbering** — CSV lines are numbered by a running sequence
starting at 1, formatted `%06d` (zero-padded to 6 digits), across all invoices
in the export (`InvoiceCsvExporter.java:31-44`). The number is a per-export
sequence, **not** a stable invoice id. **[Confirmed]**

## 3.7 Standard CRUD validation rules

- Owner: firstName, lastName, address, city all `@NotEmpty`; telephone
  `@NotEmpty` and `@Digits(fraction=0, integer=10)` — up to 10 digits, no
  fraction (`model/Owner.java:47-58`, `model/Person.java:31-37`).
- Pet: name required; type required when the pet is new; birth date required
  (`web/PetValidator.java:37-53`). Duplicate pet name for the same owner is
  rejected on create (`web/PetController.java:80-83`; case-insensitive match in
  `model/Owner.java:126-138`).
- Visit: description `@NotEmpty` (`model/Visit.java:47-49`).
- Pet type parsing: unknown type name → `ParseException`
  (`web/PetTypeFormatter.java:56-63`).
- Pets are listed sorted by name; visits sorted by date descending
  (`model/Owner.java:99-103`, `model/Pet.java:100-104`).
