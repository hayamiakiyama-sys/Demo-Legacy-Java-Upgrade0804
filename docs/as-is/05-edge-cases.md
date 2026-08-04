# 5. Edge cases & exception handling

Observations are **[Confirmed]** from code; risk assessments are marked
**[Inference]**.

## 5.1 Null & missing values

- **Null visit date** — visits with `date == null` are silently skipped in the
  closing (`MonthlyClosingService.java:69-71`). New `Visit`s default to
  `LocalDate.now()` (`model/Visit.java:62-64`) and the DB column is nullable
  (`db/hsqldb/initDB.sql:60`), so nulls are possible via direct data.
- **Null pet type** — a pet with no `PetType` is billed as `"*"` → default unit
  price (`MonthlyClosingService.java:66`, BR-2/BR-3).
- **Unknown pet type on a form** — `PetTypeFormatter.parse` throws
  `ParseException` for a name not in the DB (`web/PetTypeFormatter.java:56-63`).
- **Owner with no pets / no chargeable visits** — produces an empty invoice
  that is excluded from results (`MonthlyClosingService.java:56-58`,
  `Invoice.isEmpty` `Invoice.java:72-74`).
- **Empty owner search** — `lastName == null` becomes `""` → `LIKE '%'` → all
  owners (`web/OwnerController.java:86-88`).
- **`findVisitsByPetId` is not `@Transactional`** (`ClinicServiceImpl.java:108-111`);
  the closing method itself is `@Transactional(readOnly = true)`
  (`MonthlyClosingService.java:42`), so calls run within that transaction.

## 5.2 Boundary values

- **Month boundaries** — the window is `[startOfMonth 00:00, endOfMonth 00:00]`
  and the filter uses `before/after` (exclusive of strictly-before/after), so
  both the 1st and the last calendar day are included; date-only values sit at
  midnight (`LegacyDateFormats.java:59-73`, `MonthlyClosingService.java:69-75`).
  A visit with a non-midnight time on the last day would fall **after** `to`
  and be excluded — not reachable via the current date-only model, but a risk
  if times are ever stored. **[Inference]**
- **Weekend classification** uses `Calendar.SATURDAY`/`SUNDAY`
  (`MonthlyClosingService.java:85-88`); no public holidays (OQ-8).
- **Rounding** — `Math.round` is used for surcharge, discount and tax
  (`MonthlyClosingService.java:87`, `WeekdayDiscountPlugin.java:28`,
  `Invoice.java:65`). `Math.round` is half-up toward +∞; monetary values are
  `long` (whole JPY), so fractions from the 0.25 / 0.05 / 0.10 / 0.10 factors
  are rounded, not truncated.
- **Telephone** — `@Digits(integer=10, fraction=0)` allows at most 10 digits
  (`model/Owner.java:55-58`); the DB column is `VARCHAR(20)`
  (`initDB.sql:42`).
- **Default unit price fallback** to hardcoded `3000` only if the `*` rate is
  absent (`BillingRates.java:56-63`, OQ-6).

## 5.3 Time zones

- All billing date arithmetic uses `TimeZone.getDefault()` / the platform
  default (`LegacyDateFormats.java:45,60,68,76`;
  `MonthlyClosingService.toDate` `MonthlyClosingService.java:101-105`). The
  code comment states this is intentional to match the paper forms
  (`LegacyDateFormats.java:13-16`).
- `Visit.date` is a `LocalDate` (no zone) but is converted to `java.util.Date`
  via a default-timezone `Calendar` for comparison
  (`MonthlyClosingService.java:101-105`). **Risk:** running the JVM in a
  different time zone than production can shift which month a boundary visit
  falls into. **[Inference]**
- The default period is computed with a default-timezone formatter
  (`BillingController.java:74-76`).

## 5.4 Encodings

- **CSV export** uses `FileWriter` (JVM default charset)
  (`InvoiceCsvExporter.java:29`); the header contains Japanese text
  (`InvoiceCsvExporter.java:22`). If the JVM default charset is not what the
  accounting system expects, the file is mojibake. The code comment explicitly
  chooses "platform default encoding" (`InvoiceCsvExporter.java:14-16`).
  **Risk under migration:** the JVM default charset changed to UTF-8 in Java 18+
  (JEP 400), which can silently change the produced file's encoding.
  **[Inference]**
- **Legacy import** uses `FileReader` (JVM default charset)
  (`LegacyVisitImporter.java:35,17-19`) — same sensitivity.
- **Web layer** is UTF-8 end to end: `CharacterEncodingFilter("UTF-8", true)`
  (`PetclinicInitializer.java:75-78`) and the monthly report JSP declares UTF-8
  (`webapp/WEB-INF/jsp/billing/monthlyReport.jsp:1`). The fee master XML is
  UTF-8 (`rates.xml:1`).

## 5.5 Concurrency

- **`BillingRateLoader`** — `load()` is `synchronized` and memoizes into
  `cached`; the fee master is effectively read-once for the JVM lifetime
  (`BillingRateLoader.java:22-27`). Editing `rates.xml` at runtime has no effect
  until restart. **[Confirmed]**
- **`DiscountPluginLoader`** — instantiates a **new plugin on every call**, no
  caching (`DiscountPluginLoader.java:23-38`); one closing run reloads the
  plugin once (`MonthlyClosingService.java:48`).
- **`ClosingSnapshotStore`** — no locking; writes a **fixed filename**
  (`petclinic-closing-snapshot.ser`) so concurrent/successive exports overwrite
  each other, and readers can observe a partially written file
  (`ClosingSnapshotStore.java:26,28-39`). **[Inference — data race on the file]**
- **CSV filename** includes the period, so concurrent exports of different
  periods do not collide, but two exports of the **same** period overwrite
  (`InvoiceCsvExporter.java:25`). **[Inference]**
- **`CallMonitoringAspect`** — increments counters inside a `synchronized(this)`
  block (`util/CallMonitoringAspect.java:87-90`).
- **`SimpleDateFormat`** instances are created per use (not shared), avoiding
  its known thread-unsafety (`LegacyDateFormats.java:30-57`,
  `InvoiceCsvExporter.java:26`). **[Confirmed]**

## 5.6 Reflection

- **Discount plugin** loaded by `Class.forName` + `getDeclaredConstructor()` +
  `setAccessible(true)`; the default plugin's constructor is intentionally
  private (`DiscountPluginLoader.java:26-34`,
  `WeekdayDiscountPlugin.java:14`). A missing class, missing no-arg ctor, or a
  class not implementing `DiscountPlugin` → `IllegalStateException`
  (`DiscountPluginLoader.java:30-37`).
- **Two-digit year window** is pinned by writing the **private field**
  `SimpleDateFormat.defaultCenturyStart` via reflection; on failure it falls
  back to `set2DigitYearStart` (`LegacyDateFormats.java:43-57`).
  **Discrepancy (OQ-15), confirmed by characterization test:** writing
  `defaultCenturyStart` directly does **not** refresh the derived
  `defaultCenturyStartYear` pivot, so the intended `[1980, 2079]` window is not
  actually achieved. On Java 8 `"79"` parses to **1979** (not 2079), while
  `"80"`→1980, `"00"`→2000, `"13"`→2013. **Risk under migration:** JDK strong
  encapsulation (Java 16+/JEP 403) blocks the reflective write; the `catch`
  falls back to `set2DigitYearStart`, which **does** refresh the pivot — so the
  same input parses to **2079** on Java 21. The two-digit-year interpretation
  therefore changes across the migration for years in the affected range.
  Pinned in `LegacyDateFormatsTests` /
  `LegacyVisitImporterTests`. **[Confirmed]**

## 5.7 Serialization

- The snapshot is Java-serialized; `Snapshot`, `Invoice`, `InvoiceLine` all
  declare `serialVersionUID = 20140401L`
  (`ClosingSnapshotStore.java:71`, `Invoice.java:13`, `InvoiceLine.java:11`).
  Changing these classes' fields without keeping the UID / structure compatible
  will break `loadLatest` of older snapshots (which is itself currently
  unused). **[Inference]**
- Deserialization uses plain `ObjectInputStream` with no filtering
  (`ClosingSnapshotStore.java:49-50`) — reading an untrusted `.ser` is unsafe.
  **[Inference — security note]**

## 5.8 Error/exception handling

- Billing loaders/exporters wrap all failures as `IllegalStateException` with a
  message and cause (`BillingRateLoader.java:38-39`,
  `DiscountPluginLoader.java:35-37`, `ClosingSnapshotStore.java:35-36,51-52`,
  `InvoiceCsvExporter.java:50-51`, `LegacyVisitImporter.java:45-46`).
- Invalid `period` string → `IllegalArgumentException`
  (`MonthlyClosingService.java:95-97`); an import record with `<4` columns →
  `IllegalArgumentException` (`LegacyVisitImporter.java:60-61`).
- Uncaught exceptions in controllers are mapped to the `exception` view by
  `SimpleMappingExceptionResolver` (`spring/mvc-core-config.xml:60-66`).
  There is **no dedicated error handling** in `BillingController`, so a bad
  `period` surfaces as the generic error page. **[Confirmed]**
- Stream close failures are swallowed and logged at debug level
  (e.g. `BillingRateLoader.java:41-45`).
