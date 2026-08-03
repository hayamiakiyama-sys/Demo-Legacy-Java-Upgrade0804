# 業務ルール・外部インターフェース / Business rules and external interfaces

## 業務ルール / Business rules

| # | ルール / Rule | 根拠 / Evidence |
| --- | --- | --- |
| BR-01 | 飼い主の姓・名・住所・市区町村・電話番号は必須 / Owner first name, last name, address, city and telephone are mandatory | `model/Person.java:32,36`, `model/Owner.java:48,52,56`（`@NotEmpty`） |
| BR-02 | 電話番号は小数なし・整数10桁以内 / Telephone is an integer of at most 10 digits, no fraction | `model/Owner.java:57`（`@Digits(fraction = 0, integer = 10)`） |
| BR-03 | 姓の検索は前方一致（大文字小文字は DB 依存）/ Owner search is a prefix match; case sensitivity depends on the database | `repository/jpa/JpaOwnerRepositoryImpl`（`LIKE :lastName`, `lastName + "%"`） |
| BR-04 | 姓が未指定の場合は空文字として全件検索 / An unspecified last name is treated as an empty string, returning all owners | `web/OwnerController#processFindForm` |
| BR-05 | 検索結果0件は `notFound` エラー、1件は詳細へリダイレクト、複数件は一覧 / Zero hits produce a `notFound` error, one hit redirects to the detail page, several hits show the list | 同上 / same |
| BR-06 | ペットは名称・種別（新規時）・生年月日が必須 / Pet name, type (when new) and birth date are mandatory | `web/PetValidator#validate` |
| BR-07 | 同一飼い主内でペット名の重複は不可（大文字小文字を無視）/ Pet names must be unique per owner, ignoring case | `web/PetController#processCreationForm`, `model/Owner#getPet(String, boolean)` |
| BR-08 | 飼い主のペットは名称の昇順で保持 / An owner's pets are kept sorted by name | `model/Owner#getPets` |
| BR-09 | ペットの来院履歴は日付の降順 / A pet's visits are ordered by date descending | `model/Pet#getVisits` |
| BR-10 | 来院は説明が必須、日付の既定値は当日 / A visit requires a description; the date defaults to today | `model/Visit.java:40-48` |
| BR-11 | 日付の入出力形式は `yyyy/MM/dd` / Dates are read and written as `yyyy/MM/dd` | `model/Pet.java`, `model/Visit.java`（`@DateTimeFormat`） |
| BR-12 | 画面から `id` は変更できない / The `id` field cannot be bound from the screen | 各コントローラの `@InitBinder`（`setDisallowedFields("id")`） |
| BR-13 | ペット種別は名称の完全一致で解決、不一致は `ParseException` / Pet type is resolved by exact name match, otherwise `ParseException` | `web/PetTypeFormatter#parse` |
| BR-14 | 獣医一覧はキャッシュ `vets` に保持 / The vet list is cached under the `vets` cache | `service/ClinicServiceImpl#findVets`（`@Cacheable`） |
| BR-15 | 未捕捉例外は `exception.jsp` を表示 / Uncaught exceptions render `exception.jsp` | `mvc-core-config.xml`（`SimpleMappingExceptionResolver`） |
| BR-16 | 締め対象は指定月（`yyyy/MM`）の月初〜月末の来院、既定タイムゾーンで判定 / The closing covers visits from the first to the last day of the given month, evaluated in the default time zone | `service/billing/MonthlyClosingService#close`, `LegacyDateFormats` |
| BR-17 | 単価はペット種別ごとの料金マスタ、未定義は `*` の既定単価（3,800円）/ The unit price comes from the fee master per pet type, falling back to the `*` default of 3,800 | `billing/rates.xml`, `BillingRates#unitPriceFor` |
| BR-18 | 土日の来院は単価の25%を休日加算 / Weekend visits carry a 25% holiday surcharge | `MonthlyClosingService#surchargeFor`, `rates.xml`（`holiday-surcharge-rate`） |
| BR-19 | 平日は5%割引、平日かつ `lizard` はさらに10%（計15%）割引 / Weekdays get 5% off, and a weekday `lizard` visit gets a further 10% (15% total) | `WeekdayDiscountPlugin#discountFor` |
| BR-20 | 明細金額 = 単価 + 休日加算 - 割引、消費税は小計の10%（四捨五入）/ Line amount = unit price + surcharge − discount; tax is 10% of the subtotal, rounded half up | `InvoiceLine#getAmount`, `Invoice#getTax` |
| BR-21 | 明細が無い飼い主は請求書を作らない / Owners without lines get no invoice | `MonthlyClosingService#close` |
| BR-22 | 旧経理システムの来院取込は2桁年を1980年起点で解釈する設定だが、実挙動は起点未適用（`79` → 1979）/ Imported two-digit years are configured to start at 1980, but the setting is not effective (`79` reads as 1979) | `billing.properties`, `LegacyDateFormats#importedVisitFormat`, `LegacyVisitImporterTests` |

## 外部インターフェース / External interfaces

| # | I/F | 形式 / Format | 実装 / Implementation |
| --- | --- | --- | --- |
| IF-01 | 獣医一覧 JSON / Vet list JSON | HTTP `GET /vets.json`（Jackson） | `VetController#showResourcesVetList` |
| IF-02 | 獣医一覧 XML / Vet list XML | HTTP `GET /vets.xml`（JAXB `MarshallingView`） | `mvc-view-config.xml`, `model/Vets` |
| IF-03 | 料金マスタ / Fee master | クラスパス XML を JAXB で読込 / classpath XML read with JAXB | `billing/rates.xml`, `BillingRateLoader` |
| IF-04 | 割引ルール / Discount rule | クラス名を properties で指定しリフレクション生成 / class name in properties, instantiated reflectively | `billing.properties`, `DiscountPluginLoader` |
| IF-05 | 経理システム向けCSV / CSV for accounting | 既定文字コード・ヘッダ日本語 / platform default charset, Japanese header | `InvoiceCsvExporter` |
| IF-06 | 締めスナップショット / Closing snapshot | Java シリアライズ（`.ser`）/ Java serialization | `ClosingSnapshotStore` |
| IF-07 | 旧経理システム来院ファイル / Legacy visit file | CSV、`yy/MM/dd`、既定文字コード / CSV with two-digit years, default charset | `LegacyVisitImporter` |
| IF-08 | 呼出監視 / Call monitoring | JMX `petclinic:type=CallMonitor` | `util/CallMonitoringAspect` |
| IF-09 | データベース / Database | HSQLDB（既定）、MySQL、PostgreSQL、JNDI（`javaee` プロファイル） | `spring/datasource-config.xml` |

## 未確認事項 / Open questions

事実として確認できず、業務側の確認が必要な点。/ Points that could not be confirmed from the code and need a business decision.

1. F-11 来院一覧は `visitList` ビューが存在せず必ず失敗する。使われていない機能か、失われた画面か。/
   F-11 always fails because the `visitList` view is missing — dead code, or a lost screen?
2. 消費税10%はハードコード。税率変更・軽減税率の要否。/ The 10% tax rate is hard-coded; is a configurable or reduced rate needed?
3. 休日加算は土日のみで祝日を考慮していない。祝日カレンダーの要否。/ The surcharge covers weekends only, not public holidays.
4. `lizard` の10%割引はキャンペーン相当。現在も有効か。/ The 10% `lizard` discount looks like a campaign — is it still valid?
5. CSVの文字コードは実行環境の既定に依存。経理システムが期待する文字コード（Shift_JIS / UTF-8）の確定が必要。/
   The CSV charset follows the platform default; the charset expected by the accounting system must be confirmed.
6. 締めスナップショットの互換性要件（過去ファイルの読み直しが必要か）。/ Must previously written snapshots remain readable?
7. 締め処理は全飼い主を走査し来院を都度取得するため件数増加で劣化する。許容性能の定義が必要。/
   The closing scans all owners and re-queries visits, so performance needs an agreed target.
8. BR-22 の起点未適用は不具合か仕様か。修正すると過去データの解釈が変わる。/ Is the ineffective century window in BR-22 a defect? Fixing it changes how existing data is read.
9. 電話番号10桁制限は携帯・IP電話を許容するか。/ Does the 10-digit telephone limit still fit mobile numbers?
10. 本番稼働中のプロファイル（jpa / jdbc / spring-data-jpa）の特定が必要。/ Which persistence profile runs in production?
