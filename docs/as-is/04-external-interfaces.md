# 4. External interfaces

Everything below is **[Confirmed]** from the cited source unless annotated.

## 4.1 Databases

Persistence goes through `ClinicService` → repositories. The active
implementation is chosen by Spring profile (`jpa` default, or `jdbc` /
`spring-data-jpa`) (`PetclinicInitializer.java:52`,
`spring/business-config.xml:35-95`).

Supported databases and connection settings (Maven profiles, injected into
`data-access.properties` placeholders):

| Profile | Dialect | Driver | URL | User / password | Source |
| --- | --- | --- | --- | --- | --- |
| `HSQLDB` (default) | HSQL | `org.hsqldb.jdbcDriver` | `jdbc:hsqldb:mem:petclinic` | `sa` / (empty) | `pom.xml:431-450` |
| `MySQL` | MYSQL | `com.mysql.jdbc.Driver` | `jdbc:mysql://localhost:3306/petclinic?useUnicode=true` | `root` / `petclinic` | `pom.xml:452-470` |
| `PostgreSQL` | POSTGRESQL | `org.postgresql.Driver` | `jdbc:postgresql://localhost:5432/petclinic` | `postgres` / `petclinic` | `pom.xml:471-489` |

- The data source is a Tomcat JDBC pool
  (`spring/datasource-config.xml:28-30`). A `javaee` profile switches to a JNDI
  lookup `java:comp/env/jdbc/petclinic` (`datasource-config.xml:39-42`).
- On startup the schema and seed data are executed from
  `classpath:db/${db.script}/initDB.sql` then `populateDB.sql`
  (`spring/datasource-config.xml:32-37`, `data-access.properties:8-9`).
- Schema: `vets`, `specialties`, `vet_specialties`, `types`, `owners`, `pets`,
  `visits` with FKs and indexes (`db/hsqldb/initDB.sql:1-64`). HSQLDB uses
  `VARCHAR_IGNORECASE` for `owners.last_name` → case-insensitive owner search
  (`initDB.sql:39`). Seed data in `db/hsqldb/populateDB.sql:1-53` (6 vets,
  6 pet types, 10 owners, 13 pets, 4 visits dated 2013-01-01..04).
- Credentials for MySQL/PostgreSQL are **hardcoded defaults in `pom.xml`**; can
  be overridden via system properties because the placeholder resolver uses
  `system-properties-mode="OVERRIDE"` (`business-config.xml:23`,
  `datasource-config.xml:23`).

## 4.2 Inbound HTTP APIs

- `/vets.json` — JSON list of vets (Jackson) (`web/VetController.java:54-63`).
- `/vets.xml` — XML list of vets (JAXB `MarshallingView` bound to `Vets`)
  (`web/VetController.java:54-63`, `spring/mvc-view-config.xml:30-38`).
- All HTML screens in `01-function-list.md §1.1`.

## 4.3 Outbound / external system integration

- **No outbound network calls** to external systems exist in the code (no HTTP
  client, JMS, etc.). **[Confirmed]**
- Integration with the accounting system is **file-based** (§4.4): the CSV
  export is the outbound contract, and the legacy visit file is the (unwired)
  inbound contract.

## 4.4 Files

| Direction | File | Format / encoding | Producer / consumer | Source |
| --- | --- | --- | --- | --- |
| Output | `<dir>/invoices_<period>.csv` (`/` in period → `-`) | CSV, header in Japanese, **platform default charset** | written by export screen; read by external accounting system | `service/billing/InvoiceCsvExporter.java:22,24-49` |
| Output | `<dir>/petclinic-closing-snapshot.ser` | Java serialization (`Snapshot{period, closedAt, invoices}`) | written by export screen; `loadLatest` unused | `service/billing/ClosingSnapshotStore.java:26,28-56` |
| Input | visit file handed over by the previous accounting system | CSV `petId,yy/MM/dd,description,staffName`, **platform default charset** | read by `LegacyVisitImporter` (no caller) | `service/billing/LegacyVisitImporter.java:17-19,30-65` |
| Input | fee master | `billing/rates.xml` (classpath, UTF-8) | JAXB, `BillingRateLoader` | `service/billing/BillingRateLoader.java:20,28-36` |

- CSV columns (header `請求番号,顧客名,診療日,ペット名,種別,診療内容,単価,休日加算,割引,金額`):
  sequence(`%06d`), owner name, visit date (`yyyy/MM/dd`), pet name, pet type,
  description, unit price, surcharge, discount, amount
  (`InvoiceCsvExporter.java:22,34-44`).
- The export directory resolves to `billing.export.dir` if set, else
  `System.getProperty("java.io.tmpdir")` (`web/BillingController.java:78-100`).
- The CSV/import use `FileWriter`/`FileReader`, which use the **JVM default
  charset** — a deliberate compatibility choice noted in code comments
  (`InvoiceCsvExporter.java:14-16`, `LegacyVisitImporter.java:17-19`). See
  `05-edge-cases.md §5.4`.

## 4.5 Environment variables & system properties

No OS environment variables are read directly. Relevant JVM/system properties:

| Property | Purpose | Source |
| --- | --- | --- |
| `spring.profiles.active` | selects persistence profile (default `jpa`) | `PetclinicInitializer.java:44-52` |
| `java.io.tmpdir` | fallback export directory | `web/BillingController.java:96-98` |
| `user.timezone` / default TZ | all billing date math uses `TimeZone.getDefault()` | `LegacyDateFormats.java:45,60,68,76`, `MonthlyClosingService.java:102` |
| `user.language`/`user.country` / default locale | report date formatter locale | `LegacyDateFormats.reportFormat` `LegacyDateFormats.java:34-36` |
| default charset (`file.encoding`) | CSV export / legacy import encoding | `InvoiceCsvExporter.java:29`, `LegacyVisitImporter.java:35` |
| `jdbc.*`, `jpa.database`, `jpa.showSql`, `db.script` | placeholders overridable as system properties (OVERRIDE mode) | `spring/data-access.properties:8-19`, `business-config.xml:23` |

## 4.6 Configuration files

| File | Purpose | Source |
| --- | --- | --- |
| `billing/rates.xml` | fee master: per-type unit prices, `holiday-surcharge-rate`, `closing-day`, currency | `src/main/resources/billing/rates.xml` |
| `billing/billing.properties` | `billing.discount.plugin` (FQCN), `billing.two.digit.year.start` (1980), `billing.export.dir` (empty → tmpdir) | `src/main/resources/billing/billing.properties:1-10` |
| `spring/business-config.xml` | services, tx, persistence profiles, entity manager | `src/main/resources/spring/business-config.xml` |
| `spring/datasource-config.xml` | data source + DB initialization | `src/main/resources/spring/datasource-config.xml` |
| `spring/data-access.properties` | JDBC/JPA settings (placeholder targets) | `src/main/resources/spring/data-access.properties` |
| `spring/mvc-core-config.xml` | component scan, conversion service, message source, exception resolver | `src/main/resources/spring/mvc-core-config.xml` |
| `spring/mvc-view-config.xml` | view resolvers, JAXB marshaller for vets XML | `src/main/resources/spring/mvc-view-config.xml` |
| `spring/tools-config.xml` | AOP call-monitoring aspect, JMX export, ehcache cache manager | `src/main/resources/spring/tools-config.xml` |
| `cache/ehcache.xml` | ehcache regions (used for `vets` cache) | referenced `spring/tools-config.xml:43-44` |
| `logback.xml` | logging config (scan every 30s) | `src/main/resources/logback.xml` |
| `messages/messages*.properties` | i18n (default, `_en`, `_de`) | `spring/mvc-core-config.xml:53-54` |
| `META-INF/persistence.xml` (`persistenceUnitName=petclinic`) | referenced by the entity manager factory | `business-config.xml:49` |

## 4.7 JMX

`CallMonitoringAspect` is exported as JMX MBean `petclinic:type=CallMonitor`
with attributes `enabled`, `callCount`, `callTime` and operation `reset`
(`util/CallMonitoringAspect.java:37-74`, `spring/tools-config.xml:19-35`).

## 4.8 Caching

Ehcache via Spring cache abstraction; `ClinicService.findVets()` is
`@Cacheable("vets")` (`service/ClinicServiceImpl.java:101-106`,
`spring/tools-config.xml:37-44`).
