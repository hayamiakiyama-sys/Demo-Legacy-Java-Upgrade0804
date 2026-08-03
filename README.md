# PetClinic 動物病院システム / PetClinic Veterinary System

社内で長年稼働している動物病院の顧客・診療管理システムです。/
In-house customer and visit management system for a veterinary clinic, in production for many years.

- Java 21 / Spring Framework 6.1.x（XML設定）/ Spring MVC / JSP・JSTL / war
- Hibernate（JPA）・Spring JDBC・Spring Data JPA の3実装をプロファイルで切替
- 既定DB: HSQLDB（インメモリ）、本番相当: MySQL / PostgreSQL

> 現行仕様は `docs/as-is/`、移行テスト計画は `docs/test-plan/`、Java 21 移行の内容と挙動変更は
> `docs/migration/java21-migration-report.md` にあります。/
> The recovered specification is in `docs/as-is/`, the migration test plan in `docs/test-plan/`, and the
> migration result in `docs/migration/java21-migration-report.md`.

## ローカル実行 / Run locally

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
mvn clean package
mvn jetty-ee10:run-war
```

http://localhost:9966/petclinic/

MySQL / PostgreSQL を使う場合は `-P MySQL` / `-P PostgreSQL` を指定します。/
Use `-P MySQL` or `-P PostgreSQL` for the other databases.

## 主な画面 / Main screens

| URL | 内容 / Description |
| --- | --- |
| `/` | ホーム / Home |
| `/owners/find.html` | 飼い主検索・登録・編集 / Owner search, create, edit |
| `/owners/{id}` | 飼い主詳細（ペット・来院履歴）/ Owner detail |
| `/owners/{ownerId}/pets/new` | ペット登録・編集 / Pet create, edit |
| `/owners/*/pets/{petId}/visits/new` | 来院登録 / Add visit |
| `/vets.html` `/vets.json` `/vets.xml` | 獣医一覧（HTML/JSON/XML）/ Vet list |
| `/billing/monthly` | 月次締め（請求計算・CSV出力）/ Monthly closing (billing, CSV export) |

## 月次締め / Monthly closing

`org.springframework.samples.petclinic.service.billing` 配下に、経理システム連携用の締め処理があります。/
The closing process that feeds the accounting system lives under
`org.springframework.samples.petclinic.service.billing`.

- 料金マスタ `src/main/resources/billing/rates.xml` を JAXB で読み込み / fee master read with JAXB
- 休日加算・割引プラグイン（`billing.properties` のクラス名をリフレクションで生成）/ holiday surcharge and a
  reflectively instantiated discount plugin
- 経理システム向け CSV 出力とバッチ再印刷用のシリアライズスナップショット / CSV export and a Java-serialized snapshot
- 旧経理システムからの来院取込（2桁年の日付）/ visit import from the old system using two-digit years

## Devinデモ手順 / Devin demo flow

このリポジトリは Java 8 → Java 21 移行デモの出発点です。各フェーズをDevinに依頼します。/
This repository is the starting point of a Java 8 → Java 21 migration demo. Ask Devin to run each phase.

1. **現行仕様の復元 / Recover the current specification** — コードを唯一の根拠として `docs/as-is/` に
   機能一覧・処理フロー・業務ルール・外部I/F・エッジケース・未確認事項を出力（ファイル名と行番号付き）。/
   Using the code as the only source of truth, produce the function list, process flows, business rules,
   external interfaces, edge cases and open questions under `docs/as-is/`, citing file names and line numbers.
2. **移行テスト計画の作成 / Create the migration test plan** — `docs/test-plan/` に、削除API・javax→jakarta・
   強カプセル化・日付/書式・既定文字コード・リフレクション・シリアライズ・依存互換性の観点マトリクスと合否基準。/
   In `docs/test-plan/`, build the test perspective matrix and pass/fail criteria covering removed APIs,
   javax→jakarta, strong encapsulation, date/format, default charset, reflection, serialization and
   dependency compatibility.
3. **回帰テストの実装 / Implement regression tests** — Java 8 の現行挙動を固定する特性化テストを追加し、
   実行結果とカバレッジを提示。/ Add characterization tests that pin the current Java 8 behavior, then report
   results and coverage.
4. **Java 21 移行とPR / Migrate to Java 21 and open a PR** — ビルド設定・プラグイン・依存の更新、javax→jakarta、
   削除APIの置換を行い、テストを緑にしてPRを作成。意図した挙動変更は明記。/ Update the build, plugins and
   dependencies, migrate javax→jakarta, replace removed APIs, get the tests green and open a PR, documenting
   any intentional behavior change.

## ライセンス / License

Apache License 2.0（`LICENSE.txt`）。本リポジトリは
[spring-petclinic/spring-framework-petclinic](https://github.com/spring-petclinic/spring-framework-petclinic)
の v5.0.8 を基に、デモ用に改変したものです。/ Derived from spring-framework-petclinic v5.0.8 and modified for
demonstration purposes.
