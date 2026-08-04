# 1. Migration strategy & risk analysis

## 1.1 Recommended migration strategy

**[Recommendation]** A "characterize first, then migrate in ordered layers"
approach, because there are **no existing tests** (`README.md:10-11`) and
several runtime-dependent behaviors (charset, time zone, reflection,
serialization) identified in `docs/as-is/05-edge-cases.md`.

Phases:

1. **Baseline (Java 8).** Build and run on Java 8 exactly as today
   (`README.md:16-18`), and capture golden-master outputs and characterization
   tests (see `04-regression-baseline-strategy.md`). Nothing is migrated yet.
2. **Toolchain to Java 21, code unchanged where possible.** Raise
   `maven.compiler.release` to 21; update Maven plugins to Java-21-capable
   versions; keep application code on the current APIs only where they still
   compile.
3. **Framework/EE upgrade (the unavoidable jakarta step).** Spring 5 does not
   run on the `jakarta.*` namespace, and Tomcat 10+ only serves `jakarta.*`
   web apps. This forces a coordinated jump to Spring Framework 6.x, Hibernate
   6.x, Bean Validation 3.x, JAXB 4.x, and a Jakarta-based servlet container.
   **[Migration-fact]**
4. **Runtime-behavior pinning.** Explicitly set charset, time zone and locale
   rather than inheriting JVM defaults (addresses JEP 400 and the as-is
   findings). Requires the intent decisions in
   `docs/as-is/06-open-questions.md` (OQ-11, OQ-12).
5. **Regression + acceptance.** Re-run the full perspective matrix
   (`03-...`) on Java 21 and diff against the Java 8 golden masters.

**Why a big-bang framework step is unavoidable [Migration-fact]:** the
`javax.* → jakarta.*` rename cannot be done incrementally within a single
deployable — Spring 6 / Hibernate 6 require `jakarta.*`, and the servlet
container must match. Persistence, validation, servlet and JAXB namespaces all
move together. Plan step 3 as one coordinated change with a single regression
pass, not per-file trickle.

## 1.2 Blast radius (what the migration touches)

Concrete inventory from the current code:

| Area | Blast radius (current code) | Evidence |
| --- | --- | --- |
| `javax.persistence` → `jakarta.persistence` | **46 imports** across all 8 entity/mapped-superclass classes + 4 JPA repository impls | `model/*.java`, `repository/jpa/*.java` |
| `javax.validation` → `jakarta.validation` | **4 imports**: `@Valid` in 3 controllers, `@Digits`/constraints in `Owner` | `web/OwnerController.java:21`, `web/PetController.java:30`, `web/VisitController.java:20`, `model/Owner.java:29` |
| `javax.xml.bind` (JAXB) → `jakarta.xml.bind` | **10 imports**: `Vets`, `Vet`, billing rate JAXB types | `model/Vets.java:21-22`, `model/Vet.java:30`, `service/billing/BillingRates.java` |
| `javax.servlet` → `jakarta.servlet` | **2 imports** in the bootstrap initializer | `PetclinicInitializer.java:24-25` |
| `javax.sql.DataSource` | **3 imports** — **stays `javax.sql`** (Java SE, not Jakarta EE); do **not** rename | `repository/jdbc/*`, injected via constructor |
| Total Java files importing `javax.*` | **23 files** | `grep -rl "import javax\." src/main/java` |
| Web/config XML & JSP | Spring XML config, JSP/JSTL taglibs, `web.xml`-less bootstrap | `spring/*.xml`, `webapp/WEB-INF/**` |
| Build | compiler/surefire/war/tomcat7/jacoco/wro4j plugin versions | `pom.xml:293-427` |

**Note on `javax.sql`:** `javax.sql.DataSource` is part of Java SE
(`java.sql` module), **not** Jakarta EE, so those 3 imports must **not** be
mechanically renamed to `jakarta.sql`. A blind find/replace is a real risk
here. **[Migration-fact]**

## 1.3 Highest-risk hotspots (from the as-is analysis)

These are the code points most likely to change behavior silently on Java 21.
Each maps to a targeted test perspective in `03-test-perspective-matrix.md`.

| ID | Hotspot | Why risky on 21 | As-is evidence |
| --- | --- | --- | --- |
| R1 | CSV export uses `FileWriter` (JVM default charset); Japanese header | JEP 400: default charset became UTF-8 in Java 18+, so the file's bytes can change | `docs/as-is/05-edge-cases.md §5.4`, `InvoiceCsvExporter.java:14-16,29` |
| R2 | Legacy import uses `FileReader` (JVM default charset) | Same JEP 400 exposure on the read side | `LegacyVisitImporter.java:17-19,35` |
| R3 | Reflection writes private `SimpleDateFormat.defaultCenturyStart` | Strong encapsulation (JEP 396/403) blocks reflection into `java.base`; falls back with a warning → two-digit-year window may differ | `docs/as-is/05-edge-cases.md §5.6`, `LegacyDateFormats.java:43-57` |
| R4 | Discount plugin loaded via `setAccessible(true)` on a private ctor | App-class reflection still works, but any security-manager/module assumptions must be checked | `DiscountPluginLoader.java:26-34` |
| R5 | Java serialization of billing snapshot (`serialVersionUID=20140401L`) | Class/library changes across the migration can break deserialization of pre-migration `.ser` files | `docs/as-is/05-edge-cases.md §5.7`, `ClosingSnapshotStore.java:71` |
| R6 | Default time zone / locale for all billing dates and the `E` report token | JVM/OS/locale-data (CLDR) differences change month boundaries and day-of-week text | `docs/as-is/05-edge-cases.md §5.3`, `LegacyDateFormats.java:34-36,45` |
| R7 | JAXB (removed from the JDK; provided by libs) | JAXB left Java SE after 8; must move to `jakarta.xml.bind` 4.x with a runtime provider | `pom.xml:247-269`, `model/Vets.java` |
| R8 | Hibernate/JPA HQL + `left join fetch`, `getSingleResult` semantics | Hibernate 6 changes some query/typing/exception behaviors | `docs/as-is/01-function-list.md §1.4`, `JpaOwnerRepositoryImpl.java` |
| R9 | ehcache/`hibernate-ehcache` second-level & `@Cacheable("vets")` | ehcache 2.x + Hibernate cache integration changed significantly in Hibernate 6 | `pom.xml:179-192`, `ClinicServiceImpl.java:101-106` |
| R10 | AspectJ JMX call monitor | AspectJ/Spring AOP + JMX version compatibility on 21 | `util/CallMonitoringAspect.java`, `pom.xml:139-146` |
| R11 | `money` math with `Math.round`/`long` | No JVM change expected; used as a **stability anchor** (results must be byte-identical) | `docs/as-is/03-business-rules.md` |

## 1.4 Risk register (likelihood × impact)

Scale L/M/H.

| ID | Risk | Likelihood | Impact | Mitigation / test |
| --- | --- | --- | --- | --- |
| R1/R2 | Accounting CSV/import encoding changes (mojibake, wrong bytes) | **H** | **H** (financial data to external system) | Decide required charset (OQ-11), set it explicitly, byte-compare golden CSV (P-ENC) |
| R3 | Two-digit-year window resolves differently | M | M | Replace reflection with `set2DigitYearStart`; test import boundary years (P-REF/P-DATE) |
| R7 | JAXB provider missing/incompatible → `/vets.xml` 500 | **H** | M | Add `jakarta.xml.bind` 4.x + provider; test XML endpoint (P-API) |
| javax→jakarta | App fails to compile/deploy on Tomcat 10+ | **H** | **H** | Coordinated Spring 6/Hibernate 6 step; smoke deploy (P-BUILD/P-SMOKE) |
| R6 | Month boundary/day-of-week shifts | M | **H** (wrong invoices) | Pin TZ/locale (OQ-12); boundary + weekend/weekday tests (P-DATE) |
| R5 | Old `.ser` snapshots unreadable post-migration | M | L (feature currently unused, OQ-3) | Decide if cross-version compat is required (OQ); if not, document one-time break |
| R8/R9 | Hibernate 6 query/cache behavior change | M | M | Repository-level regression across all 3 profiles + 3 DBs (P-DATA) |
| R10 | AOP/JMX monitor breaks | L | L | Verify MBean attributes exposed (P-OPS) |
| Deps | MySQL `com.mysql.jdbc.Driver` (deprecated), PG driver `jre7`, tomcat-jdbc, wro4j, ehcache 2.x EOL | **H** | M | Dependency compatibility pass (see `02-...` §2.10) |

## 1.5 Rollback

**[Recommendation]** Keep the Java 8 baseline branch/artifact deployable
throughout; the migration lands on a separate branch and is not merged until
the full matrix passes. Because the snapshot `.ser` format may break (R5), note
any one-way data steps before cutover.
