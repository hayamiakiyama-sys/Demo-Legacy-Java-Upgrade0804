# 3. Test perspective matrix

Columns: **Perspective** (what quality aspect / migration hazard) ×
**Target function** (from `docs/as-is/01-function-list.md`) × **Priority**
(P1 highest) × **Method** × **Expected result** (traced to the as-is spec).

Expected results are the **Java 8 baseline behavior** captured per
`04-regression-baseline-strategy.md`; "== Java 8 golden" means the Java 21
output must equal the recorded Java 8 golden master.

## Priority legend
- **P1** — financial correctness or migration-breaking; blocks release.
- **P2** — functional correctness of a user-facing flow.
- **P3** — operational / non-functional / informational.

## 3.1 Build & deploy

| ID | Perspective | Target | Pri | Method | Expected result |
| --- | --- | --- | --- | --- | --- |
| P-BUILD-1 | Compile on 21 | whole module, all 3 DB profiles | P1 | `mvn -U -P<profile> clean verify` on JDK 21 | Builds; produces `petclinic.war`; no `javax.*` unresolved (`pom.xml`) |
| P-BUILD-2 | Deprecation/removal audit | whole module | P2 | compile with `-Xlint:all`, review warnings | No use of removed APIs; findings triaged |
| P-BUILD-3 | Dependency convergence | `pom.xml` | P2 | `mvn dependency:tree`, `versions:display-dependency-updates` | All deps Java-21-compatible per `02-...§2.10` |
| P-SMOKE-1 | Deploy on Jakarta container | WAR bootstrap | P1 | deploy to Tomcat 10.1+, GET `/` | Home page renders (`welcome.jsp`); no `jakarta`/`javax` linkage errors |
| P-SMOKE-2 | Clean JVM args | reflection libs | P2 | start with no `--add-opens`; capture any needed | Starts; any required `--add-opens` documented (§2.3) |

## 3.2 Namespace / framework (javax→jakarta)

| ID | Perspective | Target | Pri | Method | Expected result |
| --- | --- | --- | --- | --- | --- |
| P-JAK-1 | `jakarta.persistence` | all entities + JPA repos | P1 | run S1–S18 flows | Persistence works; owners/pets/visits CRUD == Java 8 |
| P-JAK-2 | `jakarta.validation` | owner/pet/visit forms | P1 | submit invalid forms | Same validation messages/behavior as as-is `§3.7` |
| P-JAK-3 | JSTL 3 taglibs | all JSPs | P2 | render every screen | No taglib/EL errors; markup equivalent |
| P-JAK-4 | `javax.sql` NOT renamed | JDBC repos | P1 | code review + P-DATA | `DataSource` remains `javax.sql`; JDBC profile works |

## 3.3 Functional regression — screens (from §1.1)

| ID | Perspective | Target | Pri | Method | Expected result |
| --- | --- | --- | --- | --- | --- |
| P-UI-1 | Owner search | S2/S3 `/owners` | P1 | search "", "Davis", none-match | 0→form+error, 1→redirect detail, many→list; empty→all owners (`OwnerController.java:82-105`) |
| P-UI-2 | Owner create/edit | S5–S8 | P2 | valid + invalid submits | redirect on success, form+errors on invalid (`OwnerController.java`) |
| P-UI-3 | Pet create/edit | S9–S12 | P2 | new pet, duplicate name, missing type | duplicate rejected; type required for new (`PetController.java:80-83`, `PetValidator`) |
| P-UI-4 | Visit create/list | S13–S15 | P2 | add visit, list | default date=today, `@NotEmpty` desc, redirect (`VisitController.java`) |
| P-UI-5 | Vet list HTML | S16 `/vets.html` | P2 | GET | vets + specialties rendered (`VetController.java:44-52`) |
| P-UI-6 | Crash mapping | S19 `/oups` | P3 | GET | `exception` view shown (`mvc-core-config.xml:60-66`) |

## 3.4 API / serialization endpoints

| ID | Perspective | Target | Pri | Method | Expected result |
| --- | --- | --- | --- | --- | --- |
| P-API-1 | JSON | A1 `/vets.json` | P1 | GET, compare JSON | Structure/values == Java 8 golden (`VetController.java:54-63`) |
| P-API-2 | XML (JAXB) | A2 `/vets.xml` | P1 | GET, compare XML | Well-formed JAXB output == golden; **no 500 from missing JAXB provider** (R7) |

## 3.5 Billing correctness (financial — highest priority)

All expected values come from `docs/as-is/03-business-rules.md`; results must be
**byte/number identical** to the Java 8 golden masters.

| ID | Perspective | Target | Pri | Method | Expected result |
| --- | --- | --- | --- | --- | --- |
| P-BILL-1 | Fee lookup + fallback | `unitPriceFor` | P1 | close a period incl. each type + unknown/null type | dog4500…`*`3800; null type→`*` (BR-1..3) |
| P-BILL-2 | Weekend surcharge | `surchargeFor` | P1 | visits on Sat/Sun vs weekday | `round(unit*0.25)` on weekend else 0 (BR-4) |
| P-BILL-3 | Discount + lizard campaign | `WeekdayDiscountPlugin` | P1 | weekday/weekend, lizard vs snake | weekday 5%, lizard +10%=15%, weekend 0 (BR-5..7) |
| P-BILL-4 | Line/subtotal/tax/total | `Invoice*` | P1 | multi-line invoice | amount=unit+sur−disc; tax=round(sub*0.1); total=sub+tax (BR-8..11) |
| P-BILL-5 | Grand total | `BillingController` | P1 | multi-owner period | sum of per-invoice totals (BR-12) |
| P-BILL-6 | Period window | `MonthlyClosingService` | P1 | 1st/last-day + null-date visits | calendar-month inclusive; null skipped; `closing-day` ignored (BR-13..16) |
| P-BILL-7 | Invoice scope | closing loop | P2 | owners with/without chargeable visits | one invoice/owner; empties excluded; name="last first" (BR-18/19) |

## 3.6 Migration-hazard perspectives (the risky runtime behaviors)

| ID | Perspective | Target | Pri | Method | Expected result |
| --- | --- | --- | --- | --- | --- |
| P-ENC-1 | CSV charset (JEP 400) | `InvoiceCsvExporter` | P1 | export a period, byte-compare file | Bytes match the **decided** required charset (OQ-11); header `請求番号…` intact |
| P-ENC-2 | Legacy import charset | `LegacyVisitImporter` | P1 | import a Shift_JIS + a UTF-8 fixture | Fields parse to expected values; no mojibake |
| P-DATE-1 | Default TZ boundaries | `LegacyDateFormats`/closing | P1 | run closing under 2+ TZs for boundary visits | Period membership pinned & == golden after TZ fix (R6/OQ-12) |
| P-DATE-2 | Report format/locale (CLDR) | `reportFormat` | P2 | format known dates | `yyyy年MM月dd日(E)` text == golden after locale pin |
| P-DATE-3 | Two-digit year window | `importedVisitFormat` | P1 | import years around 1980 pivot | Century assignment == golden (default start 1980) |
| P-REF-1 | JDK reflection (JEP 403) | `LegacyDateFormats:43-57` | P1 | run on 21, capture logs | No `InaccessibleObjectException` escapes; fallback path yields correct window (P-DATE-3) |
| P-REF-2 | Plugin reflection | `DiscountPluginLoader` | P2 | default + bad plugin class | default loads; bad class→`IllegalStateException` (§5.6) |
| P-SER-1 | Snapshot round-trip | `ClosingSnapshotStore` | P2 | write on 8 → read on 21 (and reverse) | Round-trips, OR documented intentional break per OQ-3 |
| P-PERF-1 | GC/perf | monthly closing workload | P3 | fixed workload on 8 vs 21, same heap | Latency/GC within agreed threshold; GC recorded (§2.8) |
| P-OPS-1 | JMX call monitor | `CallMonitoringAspect` | P3 | invoke repo calls, read MBean | `callCount`/`callTime` exposed & increment (`tools-config.xml:19-35`) |
| P-DATA-1 | Persistence parity | all 3 profiles × 3 DBs | P1 | run P-UI/P-BILL per matrix cell | Behavior identical across `jpa`/`jdbc`/`spring-data-jpa` where as-is says so; note JDBC visit-update `UnsupportedOperationException` (OQ-14) |
| P-CACHE-1 | Vets cache | `@Cacheable("vets")` | P3 | repeated `/vets.*` | Cached (1 DB hit); ehcache3/JCache works (R9) |

## 3.7 Coverage traceability

- Every screen S1–S19 and API A1–A2 in `docs/as-is/01-function-list.md` maps to
  at least one P-UI/P-API row.
- Every business rule BR-1..BR-20 maps to a P-BILL row.
- Every migration hazard in `02-incompatibility-coverage.md` (§2.1–§2.10) maps
  to a P-* row: removed APIs→P-API/P-BUILD, jakarta→P-JAK, JPMS→P-REF/P-SMOKE,
  date/locale→P-DATE, JEP400→P-ENC, reflection→P-REF, serialization→P-SER,
  GC→P-PERF, server→P-SMOKE, deps→P-BUILD.
