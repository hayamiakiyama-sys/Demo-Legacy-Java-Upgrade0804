# 4. Regression test strategy — baselining current Java 8 behavior

## 4.1 The core constraint

**There are no automated tests** (`src/test` is absent — verified;
`README.md:10-11`). Therefore the current behavior is undocumented executable
knowledge. The regression strategy is **characterization testing** (a.k.a.
golden-master / approval testing): capture what the Java 8 system *does today*,
freeze it as the oracle, then require Java 21 to reproduce it.

Order matters: **capture the baseline on Java 8 before changing anything.** A
golden master captured after the migration proves nothing.

## 4.2 Baseline environment to freeze

Record and pin the exact baseline so "Java 8 behavior" is reproducible
(`README.md:16-18`):

- JDK: Java 8 (exact vendor/build), `JAVA_HOME` as in README.
- OS **default charset** and **default time zone/locale** (they influence R1/R2/R6);
  capture `file.encoding`, `user.timezone`, `user.language`/`user.country`.
- Maven build: `mvn clean package` with the default `HSQLDB` profile, plus
  `-P MySQL` / `-P PostgreSQL`.
- Seed data: `db/hsqldb/populateDB.sql` (6 vets, 6 types, 10 owners, 13 pets,
  4 visits dated 2013-01-01..04) is the deterministic fixture.

**[Recommendation]** Run the baseline capture inside a fixed Docker image (Java
8 + pinned TZ=`Asia/Tokyo` + pinned locale + a chosen `file.encoding`) so the
golden masters are reproducible and the "before" charset/TZ is explicit rather
than inherited from a developer laptop.

## 4.3 What to capture (golden masters)

| Golden master | How captured | Feeds perspective |
| --- | --- | --- |
| HTTP responses for S1–S19, A1–A2 | scripted GET/POST against the running Java 8 app; save normalized HTML/JSON/XML | P-UI, P-API, P-JAK |
| `/vets.json` and `/vets.xml` bodies | save verbatim | P-API |
| Monthly closing invoices for chosen periods | GET `/billing/monthly?period=…` for crafted periods; save the rendered numbers | P-BILL |
| **Exported CSV bytes** | POST `/billing/monthly/export`; keep the raw file (byte-for-byte, not text) | P-ENC-1 (the key JEP 400 oracle) |
| Snapshot `.ser` | keep the file written on Java 8 | P-SER-1 (cross-version read) |
| Report date strings | format known dates via `reportFormat` | P-DATE-2 |
| Two-digit-year import results | import a fixture spanning the 1980 pivot | P-DATE-3 |
| Perf profile | latency/GC for a fixed closing workload | P-PERF-1 |

Deterministic fixtures needed beyond the seed data (**[Recommendation]**, add
under `src/test/resources` during migration):
- Owners/pets/visits engineered to exercise **every** billing branch: each pet
  type incl. unknown & null-type, a Saturday visit, a Sunday visit, a weekday
  visit, a lizard weekday, a snake weekday (to prove snake ≠ lizard), a
  first-of-month and last-of-month visit, and a null-date visit.
- A legacy import file in the legacy charset and one in UTF-8, with two-digit
  years `79`,`80`,`81`,`99`,`00`.
- A CSV-injection fixture: an owner/pet name containing a comma and a newline
  (probes the unescaped-CSV finding, `docs/as-is/05-edge-cases.md`; the
  behavior is whatever Java 8 does — capture it, do not "fix" it here).

## 4.4 How to turn golden masters into tests

**[Recommendation]** Introduce a test module (JUnit 5 + Spring Test +
MockMvc/`WebTestClient`, AssertJ) — the deps in `pom.xml:213-238` already hint
at JUnit/AssertJ/Mockito.

1. **Approval tests** for HTML/JSON/XML/CSV: assert current output equals the
   stored golden file; normalize only non-deterministic bits (absolute temp
   paths in `exportedFile`, timestamps) and document each normalization.
2. **Characterization unit tests** for the billing math
   (`MonthlyClosingService`, `Invoice`, `InvoiceLine`, `WeekdayDiscountPlugin`,
   `BillingRates`) asserting the exact numbers from
   `docs/as-is/03-business-rules.md` — these must pass **unchanged** on Java 8
   and Java 21 and are the primary correctness anchor (R11).
3. **Byte-level tests** for the CSV export and `.ser` snapshot (P-ENC-1,
   P-SER-1).
4. **Boundary/TZ tests** run under multiple `-Duser.timezone` values to expose
   R6 before and after the TZ pinning fix.

The same test suite runs on Java 8 (must be green = valid oracle) and on Java
21 (must stay green = no regression). Differences are the migration's findings.

## 4.5 Handling intended behavior changes

Some Java-21 differences are **acceptable or required** (e.g. CSV now UTF-8 if
OQ-11 decides UTF-8 is correct; snapshot break if OQ-3 says cross-version compat
is not required). For those:

- Do **not** silently update the golden master. Record the decision (link the
  open question), then re-baseline deliberately with a changelog entry noting
  "expected difference, approved by <owner>, reason".
- Everything not explicitly approved must match the Java 8 golden.

## 4.6 Coverage measurement

Enable JaCoCo (bump to 0.8.11+, `pom.xml:44`) to measure characterization-test
coverage of the **billing package and controllers** specifically; target high
line/branch coverage on `service/billing/**` since that is where behavior is
both custom and migration-sensitive. Coverage is a guide, not a gate — the gate
is golden-master equivalence.
