# 現行機能一覧 / Current function list

コードを唯一の根拠として復元。行番号は本ドキュメント作成時点のもの。/
Recovered from the code as the only source of truth; line numbers are as of this document.

| # | 機能 / Function | 入口 / Entry point | 実装 / Implementation |
| --- | --- | --- | --- |
| F-01 | ホーム表示 / Home | `GET /` | `mvc-core-config.xml:44`（`view-controller` → `welcome.jsp`） |
| F-02 | 飼い主検索フォーム / Owner search form | `GET /owners/find` | `OwnerController#initFindForm` |
| F-03 | 飼い主検索実行 / Owner search | `GET /owners` | `OwnerController#processFindForm` |
| F-04 | 飼い主一覧 / Owner list | 同上（複数件時）/ same, when several hits | `owners/ownersList.jsp` |
| F-05 | 飼い主詳細 / Owner detail | `GET /owners/{ownerId}` | `OwnerController#showOwner` |
| F-06 | 飼い主登録 / Create owner | `GET,POST /owners/new` | `OwnerController#initCreationForm`, `#processCreationForm` |
| F-07 | 飼い主編集 / Edit owner | `GET,POST /owners/{ownerId}/edit` | `OwnerController#initUpdateOwnerForm`, `#processUpdateOwnerForm` |
| F-08 | ペット登録 / Create pet | `GET,POST /owners/{ownerId}/pets/new` | `PetController#initCreationForm`, `#processCreationForm` |
| F-09 | ペット編集 / Edit pet | `GET,POST /owners/{ownerId}/pets/{petId}/edit` | `PetController#initUpdateForm`, `#processUpdateForm` |
| F-10 | 来院登録 / Add visit | `GET,POST /owners/{ownerId}/pets/{petId}/visits/new` | `VisitController#initNewVisitForm`, `#processNewVisitForm` |
| F-11 | 来院一覧 / Visit list | `GET /owners/*/pets/{petId}/visits` | `VisitController#showVisits` — **ビュー `visitList` が存在せず実行時エラー / the `visitList` view does not exist, so this fails at runtime** |
| F-12 | 獣医一覧（HTML）/ Vet list (HTML) | `GET /vets.html` | `VetController#showVetList` |
| F-13 | 獣医一覧（JSON/XML）/ Vet list (JSON/XML) | `GET /vets.json`, `GET /vets.xml` | `VetController#showResourcesVetList` |
| F-14 | 例外画面確認 / Error page check | `GET /oups` | `CrashController#triggerException` |
| F-15 | 月次締め表示 / Monthly closing | `GET /billing/monthly` | `BillingController#showMonthlyClosing` |
| F-16 | 月次締めCSV出力 / Closing CSV export | `POST /billing/monthly/export` | `BillingController#exportMonthlyClosing` |
| F-17 | 旧経理システム来院取込 / Legacy visit import | 画面なし（バッチ用クラス）/ no screen, batch class | `LegacyVisitImporter#read` |
| F-18 | リポジトリ呼出監視 / Repository call monitoring | JMX `petclinic:type=CallMonitor` | `CallMonitoringAspect` |

## 主要フロー / Key flows

### 飼い主検索 / Owner search

```mermaid
flowchart TD
    A[GET /owners?lastName=xxx] --> B{lastName == null?}
    B -- yes --> C[lastName = ""]
    B -- no --> D[findOwnerByLastName]
    C --> D
    D --> E{件数 / hits}
    E -- 0 --> F[findOwners.jsp + notFound エラー]
    E -- 1 --> G[redirect /owners/id]
    E -- 2以上 --> H[ownersList.jsp]
```

### 月次締め / Monthly closing

```mermaid
flowchart TD
    A[GET /billing/monthly?period=yyyy/MM] --> B[SimpleDateFormat で期間解析]
    B --> C[Calendar で月初・月末を算出<br/>既定タイムゾーン]
    C --> D[rates.xml を JAXB で読込]
    D --> E[billing.properties の割引プラグインを<br/>リフレクションで生成]
    E --> F[全飼い主 → ペット → 来院を走査]
    F --> G{来院日が期間内?}
    G -- no --> F
    G -- yes --> H[単価 + 休日加算 - 割引]
    H --> I[請求明細追加]
    I --> J[小計 / 消費税10% / 合計]
    J --> K[monthlyReport.jsp]
    K --> L{CSV出力?}
    L -- yes --> M[既定文字コードでCSV出力<br/>+ Javaシリアライズでスナップショット保存]
```

### 来院登録 / Add visit

```mermaid
flowchart TD
    A[GET .../visits/new] --> B[loadPetWithVisit:<br/>Pet を取得し空の Visit を紐付け]
    B --> C[createOrUpdateVisitForm.jsp]
    C --> D[POST .../visits/new]
    D --> E{バリデーションエラー?}
    E -- yes --> C
    E -- no --> F[saveVisit → redirect /owners/ownerId]
```
