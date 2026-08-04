# As-Is Specification — PetClinic Veterinary System (Demo-Start)

Recovered from source code only. No design documents were available and the
original developers have left, so **the code is the sole source of truth**
(see `README.md:10-11`).

## How to read these documents

Every statement is tagged so that confirmed facts are never mixed with
guesses:

- **[Confirmed]** — directly readable in the cited source file and lines.
- **[Inference]** — a reasonable interpretation that the code supports but does
  not state explicitly. Treated as a hypothesis, not fact.
- **[Open question]** — intent cannot be determined from code alone; a human
  must decide (collected in `06-open-questions.md`).

Citations use the form `path/to/File.java:START-END` and were captured against
the `Demo-Start` branch. Line numbers may shift if the code changes.

## Scope of the analysis

Analyzed revision: branch `Demo-Start`, commits `2d351ef` / `8f68999`
(`Import PetClinic veterinary system (Java 8, Spring 5, war)`).

The application is derived from
`spring-petclinic/spring-framework-petclinic` v5.0.8, modified for a Java 8 →
Java 21 migration demo (`README.md:74-77`). The standard PetClinic CRUD
(owners, pets, visits, vets) is present as-is; the **billing / monthly closing
subsystem** under `service/billing` is the clinic-specific custom code and is
where most of the business rules, external interfaces and edge cases live.

## Document index

| File | Contents |
| --- | --- |
| `01-function-list.md` | Screens, REST endpoints, batch/standalone components, service & repository functions |
| `02-process-flows.md` | Mermaid sequence diagrams and flowcharts for the main use cases |
| `03-business-rules.md` | Billing calculation logic and business rules, each with source citations |
| `04-external-interfaces.md` | Databases, HTTP APIs, files, environment/system properties, config files |
| `05-edge-cases.md` | Null / boundary handling, time zones, encodings, concurrency, reflection, serialization |
| `06-open-questions.md` | Points whose intent cannot be judged from code alone |

## Technology snapshot [Confirmed]

- Java 8 (`pom.xml:15` `java.version=1.8`), packaged as a `war`
  (`pom.xml:10`).
- Spring Framework 5.0.x via Spring IO Platform BOM `Cairo-SR3`
  (`pom.xml:20`, `pom.xml:47-57`); XML-based configuration.
- Spring MVC + JSP/JSTL view layer; no `web.xml` — bootstrapped programmatically
  by `PetclinicInitializer` (`src/main/java/org/springframework/samples/petclinic/PetclinicInitializer.java:39-79`).
- Persistence via three interchangeable profiles: `jpa` (default), `jdbc`,
  `spring-data-jpa` (`PetclinicInitializer.java:52`,
  `src/main/resources/spring/business-config.xml:35-95`); Hibernate/JPA.
- Databases: HSQLDB in-memory (default), MySQL, PostgreSQL (`pom.xml:430-489`).
- JAXB (`javax.xml.bind`) used for the fee master and the vets XML view
  (`pom.xml:250-269`).
