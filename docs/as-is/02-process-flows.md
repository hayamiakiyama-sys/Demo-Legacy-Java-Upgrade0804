# 2. Process flows — main use cases

Diagrams are derived directly from the cited code. Behavior is **[Confirmed]**
unless annotated otherwise.

## 2.1 Owner search (S2 → S3 → S4)

`OwnerController.processFindForm` (`web/OwnerController.java:82-105`).

```mermaid
flowchart TD
    A[GET /owners?lastName=...] --> B{lastName == null?}
    B -- yes --> C[lastName = "" broadest search]
    B -- no --> D[keep lastName]
    C --> E[clinicService.findOwnerByLastName]
    D --> E
    E --> F["repository: WHERE lastName LIKE 'arg%'"]
    F --> G{result size}
    G -- 0 --> H[reject 'lastName' notFound, show findOwners]
    G -- 1 --> I[redirect /owners/id]
    G -- ">1" --> J[model 'selections', show ownersList]
```

Note: an empty `lastName` becomes `LIKE '%'`, i.e. **all owners**
(`OwnerController.java:86-88`, `JpaOwnerRepositoryImpl.java:56-57`). The same
"empty means everything" behavior is what the monthly closing relies on
(§2.3).

## 2.2 Add a visit (S13 → S14)

`VisitController` (`web/VisitController.java:67-90`).

```mermaid
sequenceDiagram
    actor User
    participant VC as VisitController
    participant CS as ClinicService
    participant Repo as VisitRepository
    User->>VC: GET .../pets/{petId}/visits/new
    VC->>CS: findPetById(petId)  (via @ModelAttribute loadPetWithVisit)
    CS-->>VC: Pet
    VC->>VC: new Visit() (date = today), pet.addVisit(visit)
    VC-->>User: form createOrUpdateVisitForm
    User->>VC: POST .../visits/new (Visit, @Valid)
    alt binding/validation errors
        VC-->>User: redisplay form
    else valid
        VC->>CS: saveVisit(visit)
        CS->>Repo: save(visit)
        VC-->>User: redirect /owners/{ownerId}
    end
```

`Visit` defaults its date to `LocalDate.now()` in the constructor
(`model/Visit.java:62-64`); description is `@NotEmpty`
(`model/Visit.java:47-49`). The JDBC repository **cannot update** an existing
visit — only insert (`JdbcVisitRepositoryImpl.java:62-69`).

## 2.3 Monthly closing — recalculate (S17)

`BillingController.showMonthlyClosing` → `MonthlyClosingService.close`.

```mermaid
sequenceDiagram
    actor User
    participant BC as BillingController
    participant MCS as MonthlyClosingService
    participant RL as BillingRateLoader
    participant PL as DiscountPluginLoader
    participant CS as ClinicService
    User->>BC: GET /billing/monthly?period=yyyy/MM
    BC->>BC: period == null ? currentPeriod() : period
    BC->>MCS: close(period)
    MCS->>MCS: parsePeriod → from=startOfMonth, to=endOfMonth
    MCS->>RL: load() (JAXB, cached)
    RL-->>MCS: BillingRates
    MCS->>PL: load() (reflection, per call)
    PL-->>MCS: DiscountPlugin
    MCS->>CS: findOwnerByLastName("")  (all owners)
    loop each owner
        MCS->>MCS: new Invoice(owner)
        loop each pet of owner
            MCS->>CS: findVisitsByPetId(pet.id)
            loop each visit
                MCS->>MCS: skip if date null / outside [from,to]
                MCS->>MCS: unitPrice = rates.unitPriceFor(petType)
                MCS->>MCS: surcharge = weekend ? round(unit*rate) : 0
                MCS->>PL: discountFor(day, petType, unit)
                MCS->>MCS: invoice.addLine(...)
            end
        end
        MCS->>MCS: keep invoice only if not empty
    end
    MCS-->>BC: List<Invoice>
    BC->>BC: grandTotal = sum(invoice.total)
    BC-->>User: view billing/monthlyReport
```

Source: `web/BillingController.java:42-51,66-76`;
`service/billing/MonthlyClosingService.java:42-90`.

### Per-visit charge calculation (flowchart)

```mermaid
flowchart TD
    V[Visit in period] --> N{date == null?}
    N -- yes --> SKIP[skip visit]
    N -- no --> R{date in [from, to]?}
    R -- no --> SKIP
    R -- yes --> U["unitPrice = rates.unitPriceFor(petType)"]
    U --> W{Sat or Sun?}
    W -- yes --> SUR["surcharge = round(unit * holidaySurchargeRate)"]
    W -- no --> SUR0[surcharge = 0]
    SUR --> DISC
    SUR0 --> DISC["discount = plugin.discountFor(day, petType, unit)"]
    DISC --> LINE["line amount = unit + surcharge - discount"]
```

Source: `MonthlyClosingService.java:64-90`,
`WeekdayDiscountPlugin.java:17-29`, `InvoiceLine.java:66-68`. See
`03-business-rules.md` for the exact numbers.

## 2.4 Monthly closing — export CSV + snapshot (S18)

`BillingController.exportMonthlyClosing` (`web/BillingController.java:53-64`).

```mermaid
sequenceDiagram
    actor User
    participant BC as BillingController
    participant MCS as MonthlyClosingService
    participant EX as InvoiceCsvExporter
    participant SS as ClosingSnapshotStore
    participant FS as Filesystem
    User->>BC: POST /billing/monthly/export (period)
    BC->>MCS: close(period)  (recomputes, same as §2.3)
    MCS-->>BC: List<Invoice>
    BC->>BC: dir = billing.export.dir or java.io.tmpdir
    BC->>EX: export(invoices, dir, period)
    EX->>FS: write invoices_<period>.csv (default charset)
    BC->>SS: save(dir, period, invoices)
    SS->>FS: write petclinic-closing-snapshot.ser (Java serialization)
    BC-->>User: view billing/monthlyReport (exportedFile shown)
```

The export **re-runs the whole closing calculation** rather than reusing the
already-computed invoices from the GET screen
(`BillingController.java:55`). The snapshot filename is fixed
(`petclinic-closing-snapshot.ser`) and contains no period, so each export
overwrites the previous one (`ClosingSnapshotStore.java:26,28-33`).

## 2.5 Legacy visit import (component B1 — not wired)

`LegacyVisitImporter.read` (`service/billing/LegacyVisitImporter.java:30-65`).
Shown for completeness; **no caller exists** (see `06-open-questions.md`).

```mermaid
flowchart TD
    F[File handed over by old accounting system] --> RD[BufferedReader + FileReader default charset]
    RD --> L{read line}
    L -- non-blank --> P["split(',', -1); require >= 4 columns"]
    P --> PD["parse col1 as yy/MM/dd using pinned century window"]
    PD --> IV["ImportedVisit(petId, date, description, staffName)"]
    L -- blank --> L
    L -- EOF --> DONE[return List<ImportedVisit>]
```

Two-digit years are resolved with a century window whose start year comes from
`billing.two.digit.year.start` (default 1980), pinned by writing
`SimpleDateFormat.defaultCenturyStart` via reflection
(`LegacyDateFormats.java:43-57`, `LegacyVisitImporter.java:67-86`).

## 2.6 Exception handling flow

```mermaid
flowchart LR
    RQ[Any request] --> CTRL[Controller]
    CTRL -- throws --> RES[SimpleMappingExceptionResolver]
    RES --> EV["exception view (WEB-INF/jsp/exception.jsp)"]
```

Source: `spring/mvc-core-config.xml:60-66`, demonstrated by
`web/CrashController.java:33-37`.
