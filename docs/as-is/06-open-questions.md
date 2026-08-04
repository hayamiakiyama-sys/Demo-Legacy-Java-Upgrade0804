# 6. Open questions

Points whose **intent** cannot be judged from code alone. Each notes the
observed fact (with citation) and the decision a human must make. These are
**not** defects claimed against the code — they are ambiguities to confirm
before the Java 21 migration locks behavior in.

## OQ-1 — `closing-day` is configured but unused

Observed: `rates.xml` sets `closing-day = 25` and it is loaded and logged, but
the billing window is always a full calendar month; `getClosingDay()` is only
read for the log line (`rates.xml:11`, `BillingRateLoader.java:36`,
`BillingRates.java:43-45`, `MonthlyClosingService.java:44-46`).

Decision: Should the monthly closing use a 26th→25th (or similar) cutoff
anchored on `closing-day`, or is a calendar month correct and `closing-day`
vestigial? This directly changes which visits land in which period.

## OQ-2 — `LegacyVisitImporter` has no caller

Observed: `LegacyVisitImporter.read(File)` and `ImportedVisit` are a `@Component`
with no invoker anywhere in `src/main`; `getStaffName()` is never consumed
(`LegacyVisitImporter.java:22,30,88-119`).

Decision: Is legacy import a still-required batch entry point (to be wired to a
CLI/scheduler), a manual/ad-hoc utility, or dead code that can be dropped? What
is the trigger and the source file location/naming convention?

## OQ-3 — Closing snapshot is written but never read

Observed: `ClosingSnapshotStore.save(...)` is called by the export screen, but
`loadLatest(...)` has no caller; the Javadoc says "the batch scheduler reads it
back" yet no scheduler exists in the repo
(`ClosingSnapshotStore.java:17-19,42`, `web/BillingController.java:58`).

Decision: Where is the "batch re-print" consumer? Is it a separate
application/job outside this repo (which would make the `.ser` format a
cross-process contract that must stay binary-compatible), or is the snapshot
obsolete?

## OQ-4 — Snapshot filename has no period

Observed: the snapshot is always `petclinic-closing-snapshot.ser`, so each
export overwrites the previous period's snapshot; only the latest is retained
(`ClosingSnapshotStore.java:26,28-33`).

Decision: Is "keep only the most recent closing" intended, or should snapshots
be retained per period (like the CSV, whose name includes the period)?

## OQ-5 — "Holiday" surcharge covers only weekends

Observed: the surcharge applies to Saturday/Sunday only; there is no
public-holiday calendar (`MonthlyClosingService.java:84-90`).

Decision: Should Japanese public holidays (or clinic-specific non-business
days) also incur the surcharge? If so, where is the holiday source of truth?

## OQ-6 — Two default unit prices (`*` rate vs hardcoded 3000)

Observed: `unitPriceFor` falls back to the `*` rate (3800), and only if no `*`
rate exists to a hardcoded `3000` (`BillingRates.java:47-63`, `rates.xml:9`).

Decision: Which is the authoritative default, and is the hardcoded `3000` a
deliberate safety net or stale? They disagree (3800 vs 3000).

## OQ-7 — Tax rate hardcoded at 10%

Observed: `Invoice.getTax()` uses `0.1` inline, unlike unit price / surcharge /
discount which are externally configured (`Invoice.java:64-66`).

Decision: Should the consumption-tax rate be configurable (e.g. in `rates.xml`)
and/or vary by date or line (reduced rates)? Rounding is per-invoice
(`round(subtotal*0.1)`) then summed — confirm that matches accounting rules
(BR-10/BR-12).

## OQ-8 — Currency is declared but not applied

Observed: `rates.xml` declares `currency="JPY"` and `BillingRates` exposes it,
but no code reads `getCurrency()`; amounts are bare `long` with no currency
formatting in the CSV (`BillingRates.java:19-20,31-33`,
`InvoiceCsvExporter.java:34-44`).

Decision: Is multi-currency ever expected, or is JPY assumed everywhere
(whole-yen `long` math)? Confirm before any locale/format changes.

## OQ-9 — Lizard "campaign" discount has no end date

Observed: `WeekdayDiscountPlugin` adds a permanent extra 10% for `lizard`;
`snake` (same 5200 price) is not special-cased
(`WeekdayDiscountPlugin.java:12,25-27`).

Decision: Is the lizard campaign still active, and should it be time-boxed /
data-driven rather than hardcoded? Should other types be included?

## OQ-10 — Export re-runs the calculation

Observed: the POST export calls `close(period)` again instead of reusing the
invoices already computed for the GET screen
(`web/BillingController.java:46,55`).

Decision: Is recomputation intentional (accepting that data changed between
view and export could alter the exported figures), or should the export
persist exactly what the user reviewed?

## OQ-11 — Default charset dependence for accounting files

Observed: CSV export and legacy import both rely on the JVM default charset
(`InvoiceCsvExporter.java:14-16,29`, `LegacyVisitImporter.java:17-19,35`).

Decision: What exact encoding does the accounting system require (e.g.
Shift_JIS / Windows-31J vs UTF-8)? This must be pinned explicitly before Java
18+ changes the default to UTF-8 (JEP 400). Recovering intent here is required,
not just the code fact.

## OQ-12 — Time zone / locale assumptions

Observed: all closing date math and the report date format use the platform
default time zone and locale by design (`LegacyDateFormats.java:13-16,34-36`).

Decision: What time zone/locale does production run in? Boundary visits and the
`E` (day-of-week) report token depend on it; it should be pinned rather than
inherited from the host.

## OQ-13 — Persistence profile in production

Observed: three persistence implementations exist; default profile is `jpa`
(`PetclinicInitializer.java:52`, `business-config.xml:35-95`). The
`CallMonitoringAspect` only instruments `jpa`/`jdbc`
(`CallMonitoringAspect.java:30`).

Decision: Which profile does production actually use? That determines which
repository code path (and monitoring behavior) the migration must preserve.

## OQ-14 — Visit update is unsupported in the JDBC path

Observed: `JdbcVisitRepositoryImpl.save` throws
`UnsupportedOperationException` for an existing visit, whereas the JPA path
merges (`JdbcVisitRepositoryImpl.java:62-69`, `JpaVisitRepositoryImpl.java:46-53`).

Decision: Is "visits are immutable once created" a real business rule, or an
incomplete JDBC implementation? Behavior differs by profile.

## OQ-15 — Two-digit-year century pivot is wrong today (found via characterization)

Observed: `LegacyDateFormats.importedVisitFormat` writes `SimpleDateFormat`'s
private `defaultCenturyStart` field by reflection but never refreshes the
derived `defaultCenturyStartYear` pivot (`LegacyDateFormats.java:43-57`). The
intended `[1980, 2079]` window is therefore not achieved: on Java 8 `"79"`
parses to **1979** (not 2079); `"80"`→1980, `"00"`→2000, `"13"`→2013. On
Java 21 the reflective write is blocked (JEP 403) and the `catch` fallback
`set2DigitYearStart(...)` refreshes the pivot, so the same input parses to
**2079** — a behavior change across the migration. Confirmed and pinned by
`LegacyDateFormatsTests` and `LegacyVisitImporterTests`.

Decision: Which interpretation is correct for imported visit dates — the
current Java-8 result (`"79"`→1979) or the intended 1980 pivot (`"79"`→2079)?
The migration must deliberately choose one and re-baseline, rather than let the
value silently flip. (This importer currently has no caller in `src/main`; see
OQ-2.)
