# 2. Java 8 → 21 incompatibility coverage

A checklist of every migration hazard the task calls out, mapped to **where it
occurs in this codebase** and **how it is tested**. "Applies here" is
**[Confirmed-current]** from citations; the JEP/behavior facts are
**[Migration-fact]**.

## 2.1 Removed / relocated JDK APIs

| Removed/relocated | Applies here? | Where | Test hook |
| --- | --- | --- | --- |
| JAXB (`java.xml.bind`) removed from Java SE after 8/11 | **Yes** | `/vets.xml` marshalling, billing `rates.xml` unmarshalling (`model/Vets.java:21-22`, `service/billing/BillingRateLoader.java`) | P-API `/vets.xml`, P-BILL fee-master load |
| `javax.activation` removed | **Yes** (already added as dep for Java 9, `pom.xml:247-269`) | JAXB runtime dependency | Build + `/vets.xml` |
| CORBA, JAX-WS, JTA-as-JDK, Nashorn, `sun.misc.*`, applets | **No** — none used | — | grep-verified absent |
| `Thread.stop`/`destroy`, finalization deprecation | **No** direct use | — | — |
| `Integer(int)` etc. deprecated-for-removal constructors | **Audit** | none obvious; verify during compile-warning pass | P-BUILD warnings |

**Action:** run the build on Java 21 with deprecation/removal warnings enabled
and treat new `error: cannot find symbol` / removal warnings as findings
(P-BUILD).

## 2.2 `javax.*` → `jakarta.*` namespace (Jakarta EE 9+)

**[Migration-fact]** Jakarta EE 9 renamed the EE namespaces. Affected here
(counts from `grep`):

| Namespace | Move to | Count | Notes |
| --- | --- | --- | --- |
| `javax.persistence` | `jakarta.persistence` | 46 | all entities + JPA repos |
| `javax.validation(.constraints)` | `jakarta.validation` | 4 | `@Valid`, `@Digits`, `@NotEmpty` |
| `javax.xml.bind` | `jakarta.xml.bind` (JAXB 4.x) | 10 | `Vets`, `Vet`, billing |
| `javax.servlet` | `jakarta.servlet` | 2 | `PetclinicInitializer` |
| `javax.sql` | **stays** `javax.sql` | 3 | Java SE — **do not rename** |

Also non-Java: JSP taglib URIs (`http://java.sun.com/jsp/jstl/...`) move to
`jakarta.tags.*` under JSTL 3 (`webapp/WEB-INF/jsp/**`, `menu.tag`).

**Test:** compile + deploy on the Jakarta container (P-BUILD/P-SMOKE); every
screen and both APIs must render (P-UI/P-API). Verify `javax.sql` was **not**
renamed (targeted code review + DB smoke, P-DATA).

## 2.3 Module system & strong encapsulation (JEP 396 / 403)

**[Migration-fact]** From Java 16 (JEP 396) and permanently in Java 17
(JEP 403), reflective access to JDK-internal members is denied by default;
`--add-opens` is required to re-open.

| Occurrence here | Effect | Test |
| --- | --- | --- |
| `LegacyDateFormats` reflects into private `SimpleDateFormat.defaultCenturyStart` (`LegacyDateFormats.java:43-57`) | Access **denied** on 17+; code catches it, logs a warning, falls back to `set2DigitYearStart` | P-REF: assert no `InaccessibleObjectException` escapes; verify the two-digit-year window is still correct after fallback (P-DATE) |
| App does not run as a JPMS module (classpath WAR) | Lower risk, but reflective libs (Hibernate, JAXB, CGLIB/ByteBuddy, Spring) may need `--add-opens` | P-SMOKE with clean JVM args; capture any required `--add-opens` as config |

**Action:** the intended fix is to remove the JDK-internal reflection (use the
public `set2DigitYearStart`) rather than add `--add-opens`. Test both the
"fixed" path and, if `--add-opens` is used anywhere, document it.

## 2.4 Date/Time API, formatting & locale (CLDR)

**[Migration-fact]** Since Java 9, the **default locale data provider is CLDR**
(JEP 252), which changed some formatted texts, first-day-of-week, and
date/number patterns versus Java 8's JRE data.

| Occurrence | Risk | Test |
| --- | --- | --- |
| `REPORT_PATTERN = "yyyy年MM月dd日(E)"` with `Locale.getDefault()` (`LegacyDateFormats.java:34-36`) | The `E` day-of-week text and locale-sensitive formatting can differ under CLDR | P-DATE: format known dates, diff vs Java 8 golden text |
| `startOfMonth`/`endOfMonth`/`toCalendar` use `TimeZone.getDefault()` (`LegacyDateFormats.java:59-80`) | First-day-of-week/`Calendar` behavior is locale-data sensitive; boundary month math must match | P-DATE boundary cases |
| Period parse `yyyy/MM` (`LegacyDateFormats.java:21`) | Low risk but verify lenient/strict parse unchanged | P-DATE invalid-period |
| `Visit` uses `java.time.LocalDate` (`model/Visit.java`) then converts via `Calendar` (`MonthlyClosingService.java:101-105`) | Mixing `LocalDate` and default-TZ `Calendar` is the boundary-shift risk (R6) | P-DATE weekend/boundary + P-BILL |

**Action:** pin `user.timezone` and locale explicitly (OQ-12) and re-baseline;
tests compare formatted report strings and computed invoice periods.

## 2.5 UTF-8 as default charset (JEP 400, Java 18)

**[Migration-fact]** From Java 18 the JVM default charset is UTF-8 regardless of
the OS locale. Any code relying on the previous platform default changes bytes.

| Occurrence | Risk | Test |
| --- | --- | --- |
| `InvoiceCsvExporter` `new FileWriter(file)` (`InvoiceCsvExporter.java:29`), Japanese header (`:22`) | Output file bytes change (e.g. was Shift_JIS/Windows-31J on a JP host → now UTF-8) | P-ENC: byte-level compare of the CSV vs Java 8 golden; verify against the accounting system's required charset (OQ-11) |
| `LegacyVisitImporter` `new FileReader(file)` (`LegacyVisitImporter.java:35`) | Reads legacy files with a different charset → mojibake / parse errors | P-ENC: import a known Shift_JIS fixture, assert parsed fields |
| Web layer already UTF-8 (`PetclinicInitializer.java:75-78`, `monthlyReport.jsp:1`) | Low risk (already explicit) | P-UI content check |

**Action:** replace `FileWriter`/`FileReader` with explicit-charset
`OutputStreamWriter`/`InputStreamReader` once OQ-11 decides the required
encoding; test both the produced and consumed files.

## 2.6 Reflection restrictions

Covered by §2.3 (JDK-internal, R3) and R4 (`setAccessible(true)` on the
plugin's private ctor, `DiscountPluginLoader.java:26-34`). App-class reflection
(the plugin) still works on 21, but:

- Test that `DiscountPluginLoader.load()` still instantiates
  `WeekdayDiscountPlugin` and applies BR-5..BR-7 (P-REF/P-BILL).
- Test the failure paths still throw `IllegalStateException` (missing class,
  non-`DiscountPlugin` class) — `docs/as-is/05-edge-cases.md §5.6`.

## 2.7 Serialization

**[Migration-fact]** Java serialization still works, but class/library changes
across the migration can break compatibility; JEP 415 adds context-specific
deserialization filters.

| Occurrence | Risk | Test |
| --- | --- | --- |
| `ClosingSnapshotStore` writes/reads `Snapshot{period,closedAt,invoices}`; `Invoice`/`InvoiceLine`/`Snapshot` `serialVersionUID=20140401L` (`ClosingSnapshotStore.java:71`, `Invoice.java`, `InvoiceLine.java`) | Post-migration classes may not deserialize a pre-migration `.ser` (R5) | P-SER: write on 8 → read on 21 and vice-versa; assert round-trip or a documented, intentional break (depends on OQ-3/OQ-5 intent) |
| Unfiltered `ObjectInputStream` (`ClosingSnapshotStore.java:49-50`) | Security note; consider a deserialization filter | P-SER security check (optional) |

## 2.8 Garbage collector & performance characteristics

**[Migration-fact]** Java 8 defaulted to Parallel GC; Java 9+ defaults to **G1**.
Java 21 also offers ZGC/Shenandoah and Generational ZGC.

- This app is small; correctness is unaffected, but throughput/latency/heap
  behavior differs. **[Recommendation]**
- Test: P-PERF — run a fixed monthly-closing workload (all owners, seeded data
  `populateDB.sql`) on Java 8 vs Java 21 with the same heap, record
  latency/GC-pause/allocation; assert no regression beyond an agreed threshold
  (e.g. ±20%). Explicitly record which GC is selected (`-XX:+UseG1GC` etc.).
- Startup time and metaspace usage should also be captured as informational.

## 2.9 Application server compatibility

**[Migration-fact]** The current runner is `tomcat7-maven-plugin` 2.2 serving a
`javax.*` app on Tomcat 8 APIs (`pom.xml:32,362-370`). Jakarta EE apps require
**Tomcat 10.1+ (Servlet 6, `jakarta.*`)** — Tomcat 9 is the last `javax.*`
line.

| Item | Current | Target **[Recommendation]** | Test |
| --- | --- | --- | --- |
| Servlet container | Tomcat 8/9 (`javax`) | Tomcat 10.1+ (`jakarta`) | P-SMOKE deploy on target container |
| Dev run plugin | `tomcat7-maven-plugin` 2.2 (unmaintained, `javax` only) | `cargo-maven3-plugin` or run WAR on external Tomcat 10.1+ | P-BUILD/P-SMOKE |
| WAR bootstrap | `PetclinicInitializer` extends `AbstractAnnotationConfigDispatcherServletInitializer` (`javax.servlet`) | same class on `jakarta.servlet` (Spring 6) | P-SMOKE `/` loads |

## 2.10 Dependency / Maven-plugin version compatibility

Concrete current versions and the migration concern:

| Dependency | Current | Concern on Java 21 | Target **[Recommendation]** |
| --- | --- | --- | --- |
| Spring Framework | 5.0.x (BOM `Cairo-SR3`, `pom.xml:20`) | 5.x is `javax`-only; not supported on 21 for Jakarta | Spring 6.1+ |
| Spring Data JPA | via BOM; `spring-data-jdbc-core` 2.0.0.M1 (`pom.xml:21,108-118`) | old milestone; `jdbc` core split changed | Spring Data 2023.x |
| Hibernate | `hibernate-entitymanager` (5.x) (`pom.xml:169-173`) | `hibernate-entitymanager` **removed** in Hibernate 6; `jakarta.persistence` | Hibernate ORM 6.x (`hibernate-core`) |
| Bean Validation | `hibernate-validator` (6.x) (`pom.xml:174-177`) | `javax.validation` | Hibernate Validator 8.x (`jakarta.validation` 3.x) |
| JAXB | api 2.2.11 + `com.sun.xml.bind` impl (`pom.xml:250-264`) | 2.x is `javax.xml.bind` | `jakarta.xml.bind-api` 4.x + `glassfish jaxb` 4.x |
| ehcache | `net.sf.ehcache` 2.x + `hibernate-ehcache` (`pom.xml:179-192`) | ehcache 2.x EOL; `hibernate-ehcache` gone in Hibernate 6 | ehcache 3 via JCache, or Hibernate 6 JCache region factory |
| MySQL driver | 8.0.11, class `com.mysql.jdbc.Driver` (`pom.xml:40,457`) | driver class deprecated (`com.mysql.cj.jdbc.Driver`); verify TZ handling | mysql-connector-j 8.3+, update driver class |
| PostgreSQL driver | `9.4.1211.jre7` (`pom.xml:42`) | `jre7` build; old | 42.7.x |
| tomcat-jdbc pool | via BOM (`pom.xml:152-156`) | verify against Tomcat 10 | current |
| AspectJ | rt/weaver via BOM (`pom.xml:139-146`) | needs 1.9.20+ for Java 21 bytecode | AspectJ 1.9.21+ |
| Jackson | core/databind via BOM (`pom.xml:88-95`) | old 2.9-era; fine but bump | 2.15+ |
| Logback/SLF4J | via BOM (`pom.xml:159-167`) | old Logback not tested on 21 | Logback 1.4+/SLF4J 2 |
| wro4j-maven-plugin | 1.8.0 (`pom.xml:28,400-404`) | Less→CSS build; Rhino/JS engine may fail on 21 | wro4j 1.10+ or precompile assets |
| maven-compiler-plugin | 3.0 (`pom.xml:294-296`) | too old for `release`/21 | 3.11+ with `<release>21</release>` |
| maven-surefire-plugin | 2.13 (`pom.xml:308-311`) | too old for JUnit 5 / 21 | 3.2+ |
| jacoco | 0.8.1 (`pom.xml:44`) | no Java 21 class support | 0.8.11+ |
| maven-war-plugin | 2.3 (`pom.xml:318-321`) | ok but bump | 3.4+ |
| junit / mockito / assertj / hamcrest | JUnit 4, Mockito (BOM), AssertJ 2.2.0 (`pom.xml:213-238`) | test-only; modernize for characterization tests | JUnit 5, Mockito 5, AssertJ 3 |

**Test:** P-BUILD (`mvn -U clean verify` on 21 with each profile) must resolve
all versions and produce a WAR; a dependency-convergence / `versions:display-*`
report is an artifact of this perspective.
