# 6. Regression test results (Java 8 baseline)

This document records the **characterization tests actually implemented and executed** for the
priority A/B (P1/P2) perspectives of the test plan. The tests treat the current Java 8 behavior as
correct (the oracle). They must all pass on Java 8 before any migration change; Java 21 must then
reproduce them (or a difference must be explicitly approved and re-baselined).

## 6.1 Execution environment

| Property | Value |
| --- | --- |
| JDK | OpenJDK `1.8.0_492` (build `25.492-b09`, 64-Bit Server VM) |
| Build tool | Apache Maven 3.6.3, `maven-surefire-plugin` 2.13 (includes `**/*Tests.java`) |
| OS | Ubuntu 22.04.5 LTS, x86_64 |
| `file.encoding` | UTF-8 |
| `sun.jnu.encoding` | UTF-8 |
| `user.language` / `user.country` | `en` / (unset) |
| `user.timezone` | unset (JVM uses the host default) |

> Note on charset: this baseline host already defaults to UTF-8, so the JEP 400 charset change is
> not directly observable here. The CSV/import tests therefore pin behavior against
> `Charset.defaultCharset()` (whatever it is) rather than a fixed byte sequence, so they correctly
> characterize the platform-default dependency on any host (including a Windows/Shift_JIS host where
> the difference *would* appear).

## 6.2 How to run

```bash
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
./mvnw test              # or: mvn test
./mvnw test jacoco:report  # adds target/site/jacoco/
```

## 6.3 Result summary

**54 tests, 0 failures, 0 errors, 0 skipped — all green on Java 8.**

| Test class | Tests | Focus (perspective) |
| --- | --- | --- |
| `service.billing.BillingRatesTests` | 5 | Fee lookup, case-insensitivity, wildcard + hard-coded 3000 fallback (BR-1, BR-2) |
| `service.billing.WeekdayDiscountPluginTests` | 5 | Weekday 5% / lizard +10% / weekend 0, reflective load (BR-5..7, P-REF-2) |
| `service.billing.InvoiceTests` | 5 | Line amount, subtotal, half-up tax, total, negative amount (BR-8..11) |
| `service.billing.MonthlyClosingServiceTests` | 8 | Period window, boundaries, null-date skip, closing-day ignored, scope (BR-12..18) |
| `service.billing.InvoiceCsvExporterTests` | 3 | File name, header, sequence, default charset, no field escaping (BR-19) |
| `service.billing.LegacyVisitImporterTests` | 6 | Parsing, trimming, blank lines, errors, two-digit year (BR-20) |
| `service.billing.LegacyDateFormatsTests` | 6 | Month window helpers, patterns, two-digit-year pivot |
| `service.billing.ClosingSnapshotStoreTests` | 3 | Java-serialization save/load round-trip (P-SER) |
| `model.VetsMarshallingTests` | 2 | `/vets.xml` JAXB + `/vets.json` Jackson payload shape (P-API) |
| `web.VetControllerTests` | 2 | Vet HTML view/model + JSON endpoint (P-UI, P-API) |
| `web.OwnerControllerTests` | 3 | Owner search: 0 / 1 / many result branches (P-UI) |
| `web.PetValidatorTests` | 6 | Pet form validation rules (P-UI) |

## 6.4 Coverage (JaCoCo 0.8.1, `target/site/jacoco/`)

| Package | Instruction | Branch |
| --- | --- | --- |
| `service.billing` (the migration-risky logic) | **89.7%** (1128/1258) | **84.8%** |
| `model` | 57.9% (198/342) | 44.4% |
| `web` | 22.9% (138/603) | 27.3% |

The billing package — fee calculation, monthly closing, CSV export, import, serialization — is now
well covered, which is where the user asked coverage to be added. `web` coverage is intentionally
partial: only the pure/standalone-testable controllers (`OwnerController.processFindForm`,
`VetController`, `PetValidator`) are exercised here. The remaining controllers and the persistence
layer need a running Spring context + database and are deferred to the integration stage of the plan
(`05-execution-order-criteria.md`, Stages 4/6), which is a migration-phase activity.

## 6.5 Behavior-vs-intent discrepancies pinned by these tests

Per the request, where the current behavior diverges from the apparent intent, the test pins the
**actual** behavior and the spec is updated. Discovered/confirmed:

1. **Two-digit-year century pivot is wrong on Java 8 (new finding, OQ-15).**
   `LegacyDateFormats.importedVisitFormat` sets `SimpleDateFormat`'s private `defaultCenturyStart`
   field by reflection but never refreshes the derived `defaultCenturyStartYear` pivot. As a result
   the intended `[1980, 2079]` window is not achieved: on this JVM `"79"` parses to **1979**, not
   the intended 2079 (`"80"`→1980, `"00"`→2000, `"13"`→2013 happen to match intent).
   *Migration impact:* on Java 21 the reflection is blocked by strong encapsulation (JEP 403), the
   `catch` falls back to the public `set2DigitYearStart(...)`, which **does** refresh the pivot — so
   the same input then parses to **2079**. This is a genuine, migration-visible behavior change and
   must be an explicit decision, not an accident. Pinned in
   `LegacyDateFormatsTests.importedVisitFormatCenturyWindowReflectsStalePivot` and
   `LegacyVisitImporterTests.twoDigitYearPivotReflectsStaleCenturyStart`.

2. **`closing-day = 25` is loaded but does not bound the period (OQ-1).** The closing window is the
   full calendar month; a visit on the 26th–31st is still billed for that month. Pinned in
   `MonthlyClosingServiceTests.closingDayDoesNotBoundThePeriod`.

3. **Accounting CSV performs no field escaping.** A comma (or newline) in an owner name is written
   raw, producing a malformed row (11 fields vs a 10-column header). Pinned in
   `InvoiceCsvExporterTests.fieldsAreNotEscapedSoACommaInTheNameCorruptsTheRow`.

4. **Invoice line amount can go negative.** `unitPrice + surcharge - discount` is not clamped at 0,
   so a discount exceeding the charge yields a negative amount. Pinned in
   `InvoiceTests.lineAmountCanGoNegativeWhenDiscountExceedsCharge`.

5. **Hard-coded 3000 default is invisible from `rates.xml`.** When no `*` wildcard is configured,
   `unitPriceFor` returns a hard-coded `3000L` rather than any file value (BR-2). Pinned in
   `BillingRatesTests.hardcodedDefaultUsedWhenNoWildcardConfigured`.

Items 1 (OQ-15) is added to `docs/as-is/06-open-questions.md`; items 2–5 were already captured in
`docs/as-is/03-business-rules.md` / `05-edge-cases.md` / `06-open-questions.md` and are now locked
by executable tests.
