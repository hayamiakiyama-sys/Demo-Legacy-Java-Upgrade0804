# 1. Function list

All function names, HTTP methods, request/response shapes and view names below
are **[Confirmed]** from the cited source unless marked otherwise.

## 1.1 Screens (HTML, Spring MVC controllers)

| # | Screen / action | HTTP | URL | Inputs | Output (view / redirect) | Source |
| --- | --- | --- | --- | --- | --- | --- |
| S1 | Home | GET | `/` | — | view `welcome` | `spring/mvc-core-config.xml:35`, `webapp/WEB-INF/jsp/welcome.jsp` |
| S2 | Owner search form | GET | `/owners/find` | — | view `owners/findOwners` (empty `Owner`) | `web/OwnerController.java:76-80` |
| S3 | Owner search results | GET | `/owners` | query `lastName` (optional; bound onto `Owner`) | 0 results → re-show form with `notFound` error; 1 → redirect `/owners/{id}`; many → view `owners/ownersList` | `web/OwnerController.java:82-105` |
| S4 | Owner detail | GET | `/owners/{ownerId}` | path `ownerId:int` | view `owners/ownerDetails` (owner + pets + visits) | `web/OwnerController.java:131-135` |
| S5 | New owner form | GET | `/owners/new` | — | view `owners/createOrUpdateOwnerForm` | `web/OwnerController.java:59-64` |
| S6 | Create owner | POST | `/owners/new` | form `Owner` (`@Valid`) | errors → form; ok → redirect `/owners/{id}` | `web/OwnerController.java:66-74` |
| S7 | Edit owner form | GET | `/owners/{ownerId}/edit` | path `ownerId:int` | view `owners/createOrUpdateOwnerForm` | `web/OwnerController.java:107-112` |
| S8 | Update owner | POST | `/owners/{ownerId}/edit` | path `ownerId`, form `Owner` (`@Valid`) | errors → form; ok → redirect `/owners/{ownerId}` | `web/OwnerController.java:114-123` |
| S9 | New pet form | GET | `/owners/{ownerId}/pets/new` | path `ownerId` | view `pets/createOrUpdatePetForm` (+ `types` model) | `web/PetController.java:71-77` |
| S10 | Create pet | POST | `/owners/{ownerId}/pets/new` | path `ownerId`, form `Pet` (`@Valid` + `PetValidator`) | duplicate-name / errors → form; ok → redirect `/owners/{ownerId}` | `web/PetController.java:79-92` |
| S11 | Edit pet form | GET | `/owners/{ownerId}/pets/{petId}/edit` | path `petId` | view `pets/createOrUpdatePetForm` | `web/PetController.java:94-99` |
| S12 | Update pet | POST | `/owners/{ownerId}/pets/{petId}/edit` | form `Pet` (`@Valid`) | errors → form; ok → redirect `/owners/{ownerId}` | `web/PetController.java:101-110` |
| S13 | New visit form | GET | `/owners/*/pets/{petId}/visits/new` | path `petId` | view `pets/createOrUpdateVisitForm` | `web/VisitController.java:76-79` |
| S14 | Create visit | POST | `/owners/{ownerId}/pets/{petId}/visits/new` | path `ownerId`,`petId`, form `Visit` (`@Valid`) | errors → form; ok → redirect `/owners/{ownerId}` | `web/VisitController.java:82-90` |
| S15 | Visit list (fragment) | GET | `/owners/*/pets/{petId}/visits` | path `petId` | view `visitList` (model `visits`) | `web/VisitController.java:92-95` |
| S16 | Vet list (HTML) | GET | `/vets.html` | — | view `vets/vetList` | `web/VetController.java:44-52` |
| S17 | Monthly closing report | GET | `/billing/monthly` | query `period` (`yyyy/MM`, optional; default = current month) | view `billing/monthlyReport` (invoices + grand total) | `web/BillingController.java:42-51` |
| S18 | Export monthly closing CSV | POST | `/billing/monthly/export` | form `period` (`yyyy/MM`, required) | writes CSV + snapshot, then view `billing/monthlyReport` with `exportedFile` | `web/BillingController.java:53-64` |
| S19 | Crash demo | GET | `/oups` | — | throws `RuntimeException` → error view `exception` | `web/CrashController.java:33-37`, `spring/mvc-core-config.xml:60-66` |

Notes:
- The navigation menu links to Home, Find owners (`/owners/find.html`), Vets
  (`/vets.html`), Monthly closing (`/billing/monthly`) and Error (`/oups.html`)
  (`webapp/WEB-INF/tags/menu.tag:21-45`). The `.html` variants resolve because
  the DispatcherServlet is mapped to `/` with a default-servlet handler
  (`PetclinicInitializer.java:70-72`, `spring/mvc-core-config.xml:37-39`).
- `id` is disallowed as a bindable form field on owner/pet/visit forms
  (`OwnerController.java:54-57`, `PetController.java:61-64`,
  `VisitController.java:52-55`).

## 1.2 REST / data endpoints (JSON, XML)

| # | Endpoint | HTTP | URL | Response | Source |
| --- | --- | --- | --- | --- | --- |
| A1 | Vets as JSON | GET | `/vets.json` | `@ResponseBody` `Vets` serialized to JSON (Jackson) | `web/VetController.java:54-63` |
| A2 | Vets as XML | GET | `/vets.xml` | `Vets` marshalled via JAXB `MarshallingView` | `web/VetController.java:54-63`, `spring/mvc-view-config.xml:30-38` |

`Vets` is a JAXB root element wrapping a list of `Vet`, each with sorted
specialties (`model/Vets.java:30-41`, `model/Vet.java:43-76`). Content
negotiation is driven by the path extension
(`spring/mvc-view-config.xml:16-28`).

## 1.3 Batch / standalone components (not wired to HTTP)

These are Spring `@Component`s in `service/billing` that are **not invoked by
any controller or scheduler in this codebase** (see `06-open-questions.md`).

| # | Component | Public method | Inputs | Outputs | Source |
| --- | --- | --- | --- | --- | --- |
| B1 | `LegacyVisitImporter` | `read(File)` | text file, comma-separated, `>=4` columns per line: `petId,visitDate(yy/MM/dd),description,staffName` | `List<ImportedVisit>` | `service/billing/LegacyVisitImporter.java:30-65` |
| B2 | `ClosingSnapshotStore` | `save(dir,period,invoices)` / `loadLatest(dir)` | invoices to persist / directory to read | writes/reads `petclinic-closing-snapshot.ser` (Java serialization) | `service/billing/ClosingSnapshotStore.java:28-56` |

- `LegacyVisitImporter.read(...)` has **no caller anywhere** in
  `src/main` — verified by full-text search. `ImportedVisit.getStaffName()` is
  never consumed. **[Confirmed]** (dead/unwired entry point).
- `ClosingSnapshotStore.save(...)` is called only by the export screen (S18);
  `loadLatest(...)` has **no caller** in `src/main`. **[Confirmed]** The class
  Javadoc says "the batch scheduler reads it back"
  (`ClosingSnapshotStore.java:17-19`) but no scheduler exists in the code
  (see `06-open-questions.md`).

## 1.4 Service layer (`ClinicService`)

Facade over the repositories; single entry point for controllers
(`service/ClinicService.java:33-52`, impl `service/ClinicServiceImpl.java`).

| Method | Behavior | Tx | Source |
| --- | --- | --- | --- |
| `findPetTypes()` | all `PetType`, ordered by name | readOnly | `ClinicServiceImpl.java:57-61` |
| `findOwnerById(int)` | owner with pets (join fetch) | readOnly | `ClinicServiceImpl.java:63-67` |
| `findOwnerByLastName(String)` | owners whose last name **starts with** the argument (`LIKE arg%`) | readOnly | `ClinicServiceImpl.java:69-73`, `repository/jpa/JpaOwnerRepositoryImpl.java:52-59` |
| `saveOwner(Owner)` | insert or update | write | `ClinicServiceImpl.java:75-79` |
| `saveVisit(Visit)` | insert (update unsupported in JDBC impl) | write | `ClinicServiceImpl.java:82-86`, `repository/jdbc/JdbcVisitRepositoryImpl.java:62-69` |
| `findPetById(int)` | single pet | readOnly | `ClinicServiceImpl.java:89-93` |
| `savePet(Pet)` | insert or update | write | `ClinicServiceImpl.java:95-99` |
| `findVets()` | all vets, **cached** in ehcache region `vets` | readOnly | `ClinicServiceImpl.java:101-106` |
| `findVisitsByPetId(int)` | visits for a pet (no `@Transactional`) | none | `ClinicServiceImpl.java:108-111` |

## 1.5 Billing service functions

| Method | Behavior | Source |
| --- | --- | --- |
| `MonthlyClosingService.close(String period)` | Runs the monthly closing for `period` (`yyyy/MM`), returns one `Invoice` per owner with at least one chargeable visit | `service/billing/MonthlyClosingService.java:42-62` |
| `BillingRateLoader.load()` | Loads & caches the fee master from `/billing/rates.xml` via JAXB | `service/billing/BillingRateLoader.java:24-47` |
| `BillingRates.unitPriceFor(String)` | Unit price for a pet type, with `*` / hardcoded fallback | `service/billing/BillingRates.java:47-63` |
| `DiscountPluginLoader.load()` | Reflectively instantiates the discount plugin named in `billing.properties` | `service/billing/DiscountPluginLoader.java:23-58` |
| `WeekdayDiscountPlugin.discountFor(...)` | Default discount rule (weekday + lizard campaign) | `service/billing/WeekdayDiscountPlugin.java:17-29` |
| `InvoiceCsvExporter.export(...)` | Writes the accounting CSV for a period | `service/billing/InvoiceCsvExporter.java:24-56` |
| `Invoice` / `InvoiceLine` | Money aggregation (subtotal, tax, total, line amount) | `service/billing/Invoice.java:56-70`, `service/billing/InvoiceLine.java:66-68` |

## 1.6 Domain model (entities)

| Entity | Table | Key fields | Source |
| --- | --- | --- | --- |
| `Owner` (extends `Person`) | `owners` | firstName, lastName, address, city, telephone (10-digit), `Set<Pet>` | `model/Owner.java:44-61` |
| `Pet` (extends `NamedEntity`) | `pets` | name, `birthDate:LocalDate`, `PetType`, `Owner`, `Set<Visit>` (EAGER) | `model/Pet.java:45-62` |
| `PetType` (extends `NamedEntity`) | `types` | name | `model/PetType.java:25-28` |
| `Visit` (extends `BaseEntity`) | `visits` | `date:LocalDate` (defaults to `now()`), description, `Pet` | `model/Visit.java:33-64` |
| `Vet` (extends `Person`) | `vets` | `Set<Specialty>` (EAGER, many-to-many) | `model/Vet.java:43-50` |
| `Specialty` (extends `NamedEntity`) | `specialties` | name | `model/Specialty.java:26-28` |
| `Person` (mapped superclass) | — | firstName, lastName (`@NotEmpty`) | `model/Person.java:28-37` |
| `BaseEntity` (mapped superclass) | — | `id:Integer` `IDENTITY`; `isNew()` = id null | `model/BaseEntity.java:29-45` |

## 1.7 Cross-cutting

- `CallMonitoringAspect` — AOP `@Around` on `@Repository` beans; JMX-managed
  call count / average time (`util/CallMonitoringAspect.java:37-95`,
  `spring/tools-config.xml:19-29`). Active for `jpa`/`jdbc` profiles only
  (no `@Repository` join point under Spring Data JPA per its Javadoc,
  `CallMonitoringAspect.java:30`).
- `PetTypeFormatter` — parses/prints `PetType` by name; unknown name →
  `ParseException` (`web/PetTypeFormatter.java:50-64`).
- `PetValidator` — name required, type required for new pets, birth date
  required (`web/PetValidator.java:37-53`).
- `CharacterEncodingFilter("UTF-8", true)` applied to all requests
  (`PetclinicInitializer.java:75-78`).
