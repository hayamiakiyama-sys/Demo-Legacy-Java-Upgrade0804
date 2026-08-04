# Java 8 → 21 Migration — QA Test Plan (overview)

This test plan defines how to verify that migrating the PetClinic veterinary
system from **Java 8 to Java 21** preserves current behavior. It is built on
the recovered specification in [`docs/as-is/`](../as-is/00-overview.md); every
functional expectation traces back to a confirmed as-is finding (with its
`file:line` citation) so that "correct" means "behaves as the Java 8 code does
today", not "behaves as we assume".

## Guiding principle

The as-is analysis flagged several behaviors that depend on the JVM/runtime
rather than explicit code (default charset, default time zone/locale,
reflection into JDK internals, Java serialization). These are exactly the
behaviors Java 21 changes. **The migration is therefore behavior-risky even
though the business code may not change**, and this plan concentrates test
effort there.

## Documents

| File | Contents |
| --- | --- |
| `01-migration-strategy-risk.md` | Migration strategy, blast-radius / risk analysis, risk register |
| `02-incompatibility-coverage.md` | Full Java 8→21 incompatibility checklist mapped to this codebase |
| `03-test-perspective-matrix.md` | Perspective × target function × priority × method × expected result |
| `04-regression-baseline-strategy.md` | How to baseline current Java 8 behavior (golden masters, characterization tests) |
| `05-execution-order-criteria.md` | Test execution order, entry/exit gates, pass/fail criteria |
| `06-regression-results.md` | Implemented Java 8 characterization tests + executed results, coverage, and pinned discrepancies |

## Scope

- **In scope:** the `spring-petclinic` WAR (`pom.xml:6-10`), all screens, the
  two REST endpoints, and the billing subsystem, across the supported
  persistence profiles (`jpa` default, `jdbc`, `spring-data-jpa`) and the three
  databases (HSQLDB/MySQL/PostgreSQL).
- **Out of scope:** new features, fixing the open questions in
  `docs/as-is/06-open-questions.md` (they must be *decided*, not silently
  changed during migration), and UI redesign.

## Confidence tagging

Because the target-state stack (Spring 6, Jakarta EE 10, Tomcat 10/11,
Hibernate 6, etc.) is a **recommendation** — the repo is still on Java 8 —
statements are tagged:

- **[Confirmed-current]** — a fact about the *current* code (cited).
- **[Migration-fact]** — a documented Java/JEP behavior change (JEP number
  cited) that applies regardless of this app.
- **[Recommendation]** — a proposed target version or approach; the team must
  confirm.

## Baseline environment (current) [Confirmed-current]

- Java 8, WAR packaging (`pom.xml:15`, `pom.xml:10`).
- Spring IO Platform BOM `Cairo-SR3` → Spring Framework 5.0.x
  (`pom.xml:20`, `pom.xml:50-56`).
- Servlet/JSP via Tomcat 8.0.50 provided APIs; run via `tomcat7-maven-plugin`
  2.2 on port 9966 (`pom.xml:32,62-77`, `pom.xml:362-370`).
- Hibernate (`hibernate-entitymanager`), Bean Validation
  (`hibernate-validator`), JAXB 2.2.11 + activation, ehcache, AspectJ, Spring
  Data JPA/JDBC (`pom.xml:169-269`).
- **No automated tests exist** (`src/test` absent — verified;
  `README.md:10-11`). This is the single biggest QA constraint and drives the
  regression strategy in `04-...`.
- `maven-compiler-plugin` 3.0, `surefire` 2.13, `jacoco` 0.8.1
  (`pom.xml:294-296,308-311,371-375`).
