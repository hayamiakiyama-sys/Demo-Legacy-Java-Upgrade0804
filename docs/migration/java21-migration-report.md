# Java 21 移行結果 / Java 21 migration report

対象 / Scope: Java 8 + Spring Framework 5 + Hibernate 5 → Java 21 + Spring Framework 6.1 + Hibernate 6.4
検証 / Verification: `mvn test`（21件緑 / 21 tests green）、`mvn jetty-ee10:run-war` で全画面・全I/Fを確認 / all screens and interfaces exercised

## 実施した変更 / Changes

| # | 変更 / Change | 理由 / Reason |
| --- | --- | --- |
| M-01 | `java.version` 1.8 → 21、`maven-compiler-plugin` 3.0 → 3.13.0（`release`）/ compiler release 21 | T-14 |
| M-02 | Spring IO Platform BOM 廃止 → `spring-framework-bom` 6.1.14 + `spring-data-jpa` 3.2.11 / explicit BOM | 旧BOMはJava 21世代の依存を管理できない |
| M-03 | `javax.servlet/validation/persistence/xml.bind` → `jakarta.*`（本体・テスト・JSP taglib URI）/ namespace migration | T-01〜T-04 |
| M-04 | JSP taglib URI を `jakarta.tags.*` に変更、JSTL を `org.glassfish.web:jakarta.servlet.jsp.jstl` 3.0.1 に更新 | T-01 |
| M-05 | `hibernate-entitymanager` 5 → `org.hibernate.orm:hibernate-core` 6.4.10、`hibernate-validator` 8.0.1（+ Expressly 5）/ Hibernate 6 | T-03, T-13 |
| M-06 | JAXB を `jakarta.xml.bind-api` 4.0.2 + `org.glassfish.jaxb:jaxb-runtime` 4.0.5 に置換、`javax.activation` 削除 | T-04 |
| M-07 | EhCache 2（`EhCacheCacheManager` / `ehcache.xml`）を削除し `ConcurrentMapCacheManager`（`vets`）に置換 | EhCache 2はJakarta/Java 21非対応 (T-05) |
| M-08 | `spring-data-jdbc-core` 2.0.0.M1（`OneToManyResultSetExtractor`）を削除し `JdbcPetVisitExtractor` を自前実装 / dropped the milestone-only dependency | 依存が現行世代に存在しない (T-13) |
| M-09 | 接続プールを tomcat-jdbc から HikariCP 5.1.0 に変更（`datasource-config.xml`）/ HikariCP | tomcat-jdbc の juli ログがコンテナと衝突 (T-13) |
| M-10 | `tomcat7-maven-plugin` を `jetty-ee10-maven-plugin` 12.0.14 に置換（ポート9966・パス`/petclinic`は維持）/ Servlet 6 対応の実行環境 | T-14 |
| M-11 | wro4j（JRuby/Rhino依存、Java 21で起動不能）を廃止し、コンパイル済み `resources/css/petclinic.css` を同梱 / dropped wro4j | ビルドがJava 21で失敗する |
| M-12 | `messages_*.properties` を ISO-8859-1 → UTF-8 に変換 | Java 9以降 properties は UTF-8 既定、ビルドの filtering も UTF-8 (T-06) |
| M-13 | CSV出力・旧経理ファイル取込の文字コードを UTF-8 で明示（`FileWriter`/`FileReader` 廃止）/ explicit UTF-8 | JEP 400 で既定文字コードが変わる (T-06) |
| M-14 | `SimpleDateFormat#defaultCenturyStart` へのリフレクション書き換えを `set2DigitYearStart` に置換 | JEP 403 強いカプセル化で `java.base` の private フィールドは操作不可 (T-07) |
| M-15 | `VetController` の `/vets.json` `/vets.xml` を `produces` 指定に分離 | Spring 6 は拡張子ベースのコンテントネゴシエーションを廃止 |
| M-16 | Maven プラグイン更新（surefire 3.5.1 / war 3.4.0 / resources 3.3.1 / assembly 3.7.1 / jacoco 0.8.12）、テスト依存更新（JUnit 4.13.2 / Mockito 5.14.2 / AssertJ 3.26.3 / Hamcrest 2.2）、`org.mockito.Matchers` → `ArgumentMatchers` | Java 21 対応 (T-14) |
| M-17 | HSQLDB 2.7.3 / MySQL `com.mysql:mysql-connector-j` 9.0.0（`com.mysql.cj.jdbc.Driver`）/ PostgreSQL 42.7.4、Jackson 2.17.2、AspectJ 1.9.22.1、SLF4J 2 / Logback 1.5 に更新 | T-13 |
| M-18 | Jetty 9 向け `WEB-INF/jetty-web.xml` を削除、`repo.spring.io/milestone` リポジトリ定義を削除 | 不要・到達不能 |

## 意図した挙動変更 / Intentional behavior changes

| # | 変更前 / Before | 変更後 / After | 影響 / Impact |
| --- | --- | --- | --- |
| B-01 | 2桁年の起点1980が実際には効かず `79` → 1979（`defaultCenturyStart` を直接書いてもキャッシュが更新されないため）/ the configured window was silently ineffective | `set2DigitYearStart` により起点1980が有効、`79` → 2079 | BR-22 が設定どおりに動作する。過去データの解釈が変わるため業務確認が必要（`docs/as-is/02-business-rules.md` 未確認事項8） |
| B-02 | CSV出力・取込は実行環境の既定文字コード / platform default charset | UTF-8 固定 | 経理システムが Shift_JIS を期待する場合は設定化が必要（未確認事項5） |
| B-03 | `vets` キャッシュは EhCache 2（設定ファイルで上限・TTL指定）/ EhCache 2 | `ConcurrentMapCacheManager`（上限・TTLなし）| 獣医マスタは小規模のため機能影響なし。TTLが必要なら JCache + EhCache 3 を検討 |
| B-04 | LESS を wro4j がビルド時に生成 / CSS generated at build time | 生成済みCSSを同梱 | 画面表示は同一。LESS変更時は `lessc` で再生成が必要 |
| B-05 | `Accept` ヘッダ無しの `/vets.json` は拡張子から JSON を選択 | `produces` 指定で明示（結果は同一）/ same result, explicit mapping | なし |

## 挙動が変わらないことを確認した項目 / Confirmed unchanged

- 月次締め（2013/01）の総合計 16,720 円、明細金額・休日加算・割引・消費税は Java 8 と一致。/ Monthly closing totals match the Java 8 baseline.
- CSV は日本語ヘッダを含めて Java 8 と同一内容。/ The CSV content is identical.
- Javaシリアライズのスナップショットは保存・再読込が成功。/ The serialized snapshot round-trips.
- 飼い主検索（0件/1件/複数件）、飼い主・ペット・来院の登録、獣医一覧 HTML/JSON/XML、例外画面。/ Owner search, create flows, vet endpoints and the error page behave as documented.
- 既知の不具合（`visitList` ビュー欠落, `docs/as-is` 未確認事項1）は移行後も未修正のまま。/ The pre-existing `visitList` defect is deliberately left as is.
