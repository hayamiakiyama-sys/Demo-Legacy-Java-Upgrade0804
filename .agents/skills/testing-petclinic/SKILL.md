---
name: testing-petclinic
description: How to build, run and manually test the Spring PetClinic (Java 21 / Spring 6.1 / Hibernate 6.4 / JSP war) demo app locally.
---

# Testing the PetClinic demo app

## Run it

```bash
cd <repo>
fuser -k 9966/tcp 2>/dev/null   # nothing may already listen on 9966
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  mvn -B clean package -DskipTests \
  org.eclipse.jetty.ee10:jetty-ee10-maven-plugin:12.0.14:run-war
```

Takes ~90s. App: http://localhost:9966/petclinic/ . In-memory HSQLDB, seeded data, **no login required**.
Watch `mvn` output for `Started ServerConnector...0.0.0.0:9966`; poll `curl -s -o /dev/null -w '%{http_code}' http://localhost:9966/petclinic/` until 200.

## Gotcha: `.html` suffix URLs after Spring 6

Spring 5.3+/6 removed suffix pattern matching. Legacy PetClinic JSPs/tags link to `/owners/find.html`,
`/owners.html`, `/owners/{id}.html`, `/owners/{id}/pets/new.html`, `/oups.html` while the controllers map the
suffix-less paths. On a Spring 6 build these UI links may return **HTTP 400 or 404**.

If you hit that: the feature itself is probably fine — retry with the suffix-less URL
(`/owners/find`, `/owners?lastName=X`, `/owners/1`, `/owners/1/pets/new`, `/oups`) to test the business logic,
and report the broken links separately. `vets.html`/`vets.json`/`vets.xml` are explicitly mapped and do work.

## Key URLs

- Owner search: `/owners?lastName=` (all), `?lastName=Franklin` (redirects to detail), `?lastName=Zzzz` ("has not been found")
- Vets: `/vets.html`, `/vets.json`, `/vets.xml`
- Monthly closing (Japanese, i18n/encoding check): `/billing/monthly?period=2013/01` — expect `月次締め` and grand total `16,720`
- CSV export: POST via the "Export CSV for accounting" button; the file lands in `java.io.tmpdir`
  (`/tmp/invoices_2013-01.csv`) unless `billing.export.dir` is set in `src/main/resources/billing/billing.properties`.
  Verify with `iconv -f UTF-8 -t UTF-8 <file>` and check the header `請求番号,顧客名,...` (`file` is not installed on the box).
- Exception view: `/oups`

## New Pet form quirk

The Birth Date field has a jQuery UI datepicker attached; clicking elsewhere on the form can overwrite the typed
date and the calendar overlay can swallow clicks on the Type `<select>`. Type the date, click a neutral area
(e.g. the page heading) to dismiss the picker, then select the type, then submit.

## Known pre-existing defect

The visit list feature has no `visitList` JSP view and always fails — see `docs/as-is/02-business-rules.md`
open question 1. Not a regression.

## Devin Secrets Needed

None — the app is fully local with no authentication.
