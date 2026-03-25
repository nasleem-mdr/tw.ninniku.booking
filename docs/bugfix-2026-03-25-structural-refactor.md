# Bug Fix Record — 2026-03-25 Structural Refactor

**版本：** 3.32 → master (post-3.32)
**相關 spec：** `docs/superpowers/specs/2026-03-25-refactoring-design.md`
**相關 plan：** `docs/superpowers/plans/2026-03-25-refactoring-plan.md`

---

## 修復清單

### BUG-01（高）— event card HTML/JS 注入（XSS）

**位置：** `BookingTimeline.java` → `renderEventCards()`

**問題：**
- 預約名稱、備註、使用者名稱直接拼接進 HTML 字串與 `onclick` 屬性的 JS 字串，未做任何跳脫。
- 惡意輸入如 `<img onerror=alert(1) src=x>` 或 `'; alert(1); '` 可在其他使用者的瀏覽器執行任意 JS（Stored XSS）。

**修復：**
- 新增 `BookingValidator.escapeForJs()`：處理 JS 字串上下文（`onclick` 屬性內）。
  - 跳脫順序：`\` → `'` → `"` → `\n` → `\r`
- 新增 `BookingValidator.escapeForHtml()`：處理 HTML 文字節點上下文（card body）。
  - 跳脫順序：`&` → `<` → `>` → `"` → `'`
- `renderEventCards()` 中所有動態值改為透過上述方法輸出。

**涉及檔案：** `BookingValidator.java`、`BookingTimeline.java`

---

### BUG-02（高）— JSON 解析未捕捉例外

**位置：** `BookingTimeline.java` → `onEvent()`（`bookingUpdated`、`bookingDeleted`、`dateLast` handler）

**問題：**
- 直接呼叫 `json.get("s_booking_id")` 等方法，若 key 不存在或格式錯誤會拋出未捕捉的 `JSONException`，導致整個 ZK form 出錯。

**修復：**
- 新增 `BookingDTO.fromJson(String raw)`：集中解析，所有 `JSONException` 包裝為 `BookingValidationException`，訊息為 `"Invalid form data"`。
- Controller event handler 改為 try-catch `BookingValidationException`，顯示 `Clients.showNotification()`。

**涉及檔案：** `BookingDTO.java`、`BookingValidationException.java`、`BookingTimeline.java`

---

### BUG-03（中）— 不安全的型別轉換（json.get() 強轉 Integer）

**位置：** `BookingTimeline.java` 原第 142、577 行

**問題：**
- 使用 `(Integer) json.get(...)` 或 `Integer.valueOf((String) json.get(...))` 在 key 不存在或值非數字時拋出 `ClassCastException` / `NumberFormatException`。

**修復：**
- `BookingDTO.parseLenientInt()`：使用 `json.optString()` + `Integer.parseInt()`，捕捉 `NumberFormatException` 並轉為 `BookingValidationException`。
- `BookingDTO.parseTimestamp()`：同理處理 epoch ms 字串。

**涉及檔案：** `BookingDTO.java`

---

### BUG-04（中）— 全域 JS 變數汙染

**位置：** `booking.js`、`booking_weekview.js`

**問題：**
- `items`、`groups`、`timeline` 等變數為全域（`window.*`），多使用者同時開啟時各自的 vis.js 狀態互相覆蓋。
- `booking.js` 與 `booking_weekview.js` 重複定義相同的 `window.*` 函式（`openCustomAddDialog` 等），載入順序決定哪份生效，容易引發難以重現的問題。

**修復：**
- `booking.js` 重構為 `BookingApp.Timeline = (function(){ ... })()` IIFE，所有狀態改為 closure 私有變數。
- `booking_weekview.js` 重構為 `BookingApp.WeekView = (function(){ ... })()` IIFE，成為 Week/Day view 唯一權威擁有者。
- `BookingApp.Timeline` 公開 API：`initChart`, `drawChart`, `setGroups`, `setItems`, `getGroups`, `clickNew`, `openEditDialog`。
- `BookingApp.WeekView` 公開 API：`clickNew`, `openEditDialog`, `openCustomAddDialog`, `openCustomEditDialog`, `openCustomAddDialogRange`, `onWeekDayClick`, `onWeekEventClick`, `onWeekEventDelete`, `weekViewScrollTo8Am`, `updateTimeIndicator`。
- `booking.js` 中多餘的 `window.*` 指派與重複的 drag-drop IIFE 全數移除。

**涉及檔案：** `booking.js`、`booking_weekview.js`、`BookingTimeline.java`（JS eval 字串更新）

---

### BUG-05（中）— ~670 行內嵌 ZUL script（不可測試）

**位置：** `meetingroom.zul`

**問題：**
- `<script><![CDATA[...]]></script>` 區塊約 670 行，包含所有 Week view 邏輯、drag-drop、工具函式，無法單獨測試，與 ZUL 生命週期緊耦合。

**修復：**
- 全部函式移入 `BookingApp.WeekView` IIFE（`booking_weekview.js`）成為私有成員。
- `meetingroom.zul` 只保留 `<script src="...">` 外部引用，不含任何內嵌邏輯。

**涉及檔案：** `booking_weekview.js`、`meetingroom.zul`

---

### BUG-06（低）— 重疊檢查競態條件（Race Condition）

**位置：** `MResourceAssignment.java`、`MBooking.java` → `isOverlap()`

**問題：**
- 使用 check-then-act 模式，非原子操作。兩個並發的 save 可能同時通過 `isOverlap()` 並同時寫入，造成雙重預約（double-booking）。

**處理方式：** 加入文件說明，不修改（需資料庫層唯一約束或 advisory lock，超出本次範圍）。

```java
// KNOWN LIMITATION: This check-then-act pattern is not atomic at the database level.
// Two concurrent saves can both pass isOverlap() and both insert, causing a double-booking.
// Fixing this requires a DB-level unique constraint or advisory lock (out of scope).
```

**涉及檔案：** `MResourceAssignment.java`、`MBooking.java`

---

### BUG-07（低）— pom.xml Java 版本不一致

**位置：** `pom.xml`

**問題：**
- `<source>11</source>` / `<target>11</target>`，但 `META-INF/MANIFEST.MF` 宣告 `Bundle-RequiredExecutionEnvironment: JavaSE-17`，在 Java 17 環境編譯可能產生非預期行為。

**修復：** 改為 `<source>17</source>` / `<target>17</target>`。

**涉及檔案：** `pom.xml`

---

### BUG-08（低）— plugin 版本硬編碼

**位置：** `BookingTimeline.java` 第 53 行

**問題：**
- `String version = "3.32";` 每次 release 需手動更新，容易漏改，顯示版本與實際 bundle 版本不同步。

**修復：** 改為從 OSGi bundle registry 讀取：

```java
Bundle hostBundle = FrameworkUtil.getBundle(BookingTimeline.class);
// 查找 symbolic name = "tw.ninniku.booking" 的 bundle，取其 version
```

**涉及檔案：** `BookingTimeline.java`

---

## 新增檔案

| 檔案 | 用途 |
|---|---|
| `src/tw/ninniku/booking/form/BookingValidationException.java` | Checked exception，攜帶使用者可讀訊息 |
| `src/tw/ninniku/booking/form/BookingDTO.java` | 型別化 DTO，取代原始 JSONObject 解析 |
| `src/tw/ninniku/booking/form/BookingValidator.java` | 業務規則驗證 + JS/HTML 跳脫工具 |
| `src/tw/ninniku/booking/form/BookingService.java` | CRUD 業務邏輯層，擁有 Transaction 生命週期 |
