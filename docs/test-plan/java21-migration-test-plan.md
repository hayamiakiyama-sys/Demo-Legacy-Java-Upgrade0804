# Java 8 → Java 21 移行テスト計画 / Java 8 to Java 21 migration test plan

## 1. 目的と方針 / Purpose and approach

現行仕様書（`docs/as-is/`）がテストの正解定義。移行前に Java 8 上で特性化テストを作り現行挙動を固定し、
移行後に同一テストが緑であることを合格条件とする。/
`docs/as-is/` defines the expected behavior. Characterization tests pin the current behavior on Java 8 before the
migration; the migration passes when the same tests stay green afterwards.

| 段階 / Stage | 実行環境 / Runtime | 目的 / Purpose |
| --- | --- | --- |
| S1 ベースライン / baseline | Java 8 + Spring 5.0.x | 現行挙動の記録 / record current behavior |
| S2 移行後 / after migration | Java 21 + Spring 6.x | 同一テストの再実行 / re-run the same tests |
| S3 差分判定 / diff review | — | 意図した変更と回帰の切り分け / separate intended changes from regressions |

## 2. 観点マトリクス / Test perspective matrix

| # | 観点 / Perspective | 影響箇所 / Affected code | リスク / Risk | テスト方法 / Test | 合否基準 / Pass criteria |
| --- | --- | --- | --- | --- | --- |
| T-01 | `javax.servlet` → `jakarta.servlet` | `PetclinicInitializer`, `web.xml`, JSP | 高 / high | war起動と全画面のHTTPステータス / boot the war and check every screen | 全画面 200、遷移が同一 / all 200, same navigation |
| T-02 | `javax.validation` → `jakarta.validation` | `OwnerController`, `VisitController`, `model/*` | 高 / high | バリデーション単体・結合テスト / validation unit and integration tests | BR-01, BR-02, BR-06, BR-10 が同一メッセージ / same messages |
| T-03 | `javax.persistence` → `jakarta.persistence`（Hibernate 6）/ Hibernate 6 namespace | `model/*`, `repository/jpa/*` | 高 / high | 3プロファイルのリポジトリテスト / repository tests for all three profiles | 取得件数・順序が同一 / same rows and ordering |
| T-04 | JAXB がJDKから削除 / JAXB removed from the JDK | `BillingRateLoader`, `mvc-view-config.xml`, `model/Vets` | 高 / high | 料金マスタ読込と `/vets.xml` / fee master load and `/vets.xml` | 単価が同一、XMLが同一構造 / same prices, same XML shape |
| T-05 | EhCache 2 非互換 / EhCache 2 incompatible | `ClinicServiceImpl#findVets`, `ehcache.xml` | 中 / medium | キャッシュ有効時の呼出回数 / call count with the cache on | 2回目はDBアクセスなし / no DB access on the second call |
| T-06 | 既定文字コードが UTF-8 に変更（JEP 400）/ default charset becomes UTF-8 | `InvoiceCsvExporter`, `LegacyVisitImporter` | 高 / high | CSV出力バイト列の比較 / compare exported bytes | 経理システムが期待する文字コードで一致（未確認事項5の決定に従う）/ matches the agreed charset |
| T-07 | 強カプセル化でリフレクション不可（JEP 403）/ strong encapsulation blocks reflection | `LegacyDateFormats#importedVisitFormat` | 高 / high | 2桁年の取込テスト / two-digit year import test | 1980年起点の解釈が同一、例外なし / same century window, no exception |
| T-08 | リフレクションによるプラグイン生成 / reflective plugin instantiation | `DiscountPluginLoader` | 中 / medium | 割引額のテスト / discount tests | BR-19 の割引額が同一 / same discount |
| T-09 | Java シリアライズ互換 / Java serialization compatibility | `ClosingSnapshotStore`, `Invoice`, `InvoiceLine` | 中 / medium | Java 8 で書いた `.ser` を Java 21 で読む / read a Java 8 snapshot on Java 21 | 読込成功、金額一致 / loads, amounts match |
| T-10 | 日付・タイムゾーン・ロケール / date, time zone and locale | `LegacyDateFormats`, `MonthlyClosingService`, `@DateTimeFormat` | 高 / high | JVM既定TZ/ロケールを変えた締めテスト / closing tests under different TZ and locale | 締め対象件数と金額が同一 / same lines and amounts |
| T-11 | CLDRロケールデータ差 / CLDR locale data differences | `yyyy年MM月dd日(E)` の書式 / report format | 中 / medium | 書式化文字列の比較 / compare formatted strings | 帳票表記が同一、差異があれば明記 / identical, or documented |
| T-12 | AspectJ / JMX | `CallMonitoringAspect` | 中 / medium | JMX属性の取得と加算 / read and accumulate JMX attributes | 呼出回数が計上される / call count increases |
| T-13 | 依存互換性 / dependency compatibility | `pom.xml`（Spring 5→6, Hibernate 5→6, JSTL, Jackson, HSQLDB, AspectJ） | 高 / high | ビルドと依存ツリー確認 / build and dependency tree | ビルド成功・重複/競合なし / builds cleanly |
| T-14 | Mavenプラグイン・APサーバ / Maven plugins and app server | `maven-compiler-plugin 3.0`, `tomcat7-maven-plugin` | 高 / high | Java 21でのビルド・起動 / build and boot on Java 21 | war生成・起動成功 / war builds and boots |
| T-15 | 削除・非推奨API / removed and deprecated APIs | 全体（`Date`, `Calendar`, `NotEmpty` など）/ whole code base | 中 / medium | コンパイル警告の棚卸 / review compiler warnings | ビルドがエラー0、警告は棚卸済み / no errors, warnings triaged |
| T-16 | 性能・GC / performance and GC | 締め処理 / the closing process | 低 / low | 同一データでの締め時間比較 / compare closing duration | 劣化なし（未確認事項7の目標値）/ no regression |

## 3. 実行順序 / Execution order

1. S1: Java 8 で `mvn test` を実行し、全テストが緑であることを記録。/ Run `mvn test` on Java 8 and record a green baseline.
2. ビルド設定・依存の更新（T-13, T-14）→ コンパイル通過。/ Update the build and dependencies until it compiles.
3. 名前空間移行（T-01〜T-04）→ 単体テスト緑。/ Migrate the namespaces until the unit tests are green.
4. 挙動系（T-06, T-07, T-09, T-10, T-11）→ 差分を意図/回帰に分類。/ Classify behavior differences as intended or regressions.
5. war起動と全画面の手動確認（T-01, T-05, T-12）。/ Boot the war and walk through every screen.
6. S3: 差分一覧をPR本文に記載。/ List the differences in the pull request.

## 4. 合否基準 / Pass and fail criteria

- 合格 / Pass: S1と同一のテストがJava 21で全て緑、かつ意図した挙動変更がPRに明記されている。/
  Every baseline test is green on Java 21 and each intended behavior change is documented in the PR.
- 不合格 / Fail: 説明できないテスト失敗、または `docs/as-is/` の業務ルールに反する挙動が1件でもある。/
  Any unexplained failure, or any behavior that contradicts a business rule in `docs/as-is/`.
