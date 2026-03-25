# Booking Plugin Technical Guide

**Version:** 3.33 | **Updated:** 2026-03-26

## 1. Overview

The `tw.ninniku.booking` plugin provides a graphical resource booking interface for iDempiere. It allows users to visualize and manage resource assignments (e.g. meeting rooms, equipment) using three views: **Week**, **Day**, and **Timeline**.

## 2. Technology Stack

| Layer | Technology |
|-------|-----------|
| **Backend Framework** | iDempiere (OSGi Bundle) |
| **Server-Side UI** | ZK Framework (Java) |
| **Timeline Visualization** | vis.js (Vis Timeline) |
| **Dialogs & Interactions** | jQuery / jQuery UI |
| **Week/Day View Rendering** | Server-side HTML generation (Java → ZK Html component) |
| **Week/Day View Interactions** | `BookingApp.WeekView` IIFE in `booking_weekview.js` |
| **Styles** | Inline CSS in ZUL + ZK native components |
| **Database** | PostgreSQL / Oracle (standard iDempiere DBs) |
| **Tables** | `S_Resource`, `S_ResourceType`, `S_ResourceAssignment` |

## 3. Project Structure

```
tw.ninniku.booking/
├── src/tw/ninniku/booking/
│   ├── form/
│   │   ├── BookingTimeline.java          # Main controller (ADForm) — thin event wiring only
│   │   ├── BookingDTO.java               # Typed DTO replacing raw JSONObject parsing
│   │   ├── BookingValidationException.java # Checked exception for user-facing errors
│   │   ├── BookingValidator.java         # Business rule validation + JS/HTML escaping
│   │   └── BookingService.java           # CRUD business logic; owns transaction lifecycle
│   └── model/
│       ├── MResourceAssignment.java      # Extended model with overlap check
│       └── MBooking.java                 # Booking-specific model logic
├── WEB-INF/web/
│   ├── meetingroom.zul                   # Main UI (English) — no inline script
│   ├── meetingroom_tw.zul                # Main UI (Traditional Chinese)
│   └── js/
│       ├── booking.js                    # BookingApp.Timeline IIFE — Timeline view logic
│       ├── booking_weekview.js           # BookingApp.WeekView IIFE — Week/Day view logic
│       ├── vis-timeline-graph2d.min.js   # Vis.js library
│       └── ...
├── META-INF/MANIFEST.MF                  # OSGi bundle definition
├── OSGI-INF/                             # Service component definitions
└── docs/
    ├── manual/                           # User & technical guides
    └── superpowers/                      # Design specs & implementation plans
```

## 4. Key Components

### 4.1. Controller: `BookingTimeline.java`

Implements `ADForm`. After the 3.33 refactor it is a **thin event-wiring layer** — no direct DB access, no transaction management, no inline JSON parsing.

**Responsibilities:**

1. **Initialization** — Loads `meetingroom.zul`, initializes UI components, instantiates `BookingService`.
2. **Event Handling** — Listens for ZK events (button clicks, checkbox change) and hidden field `onChange` events (triggered by JS for CRUD).
3. **Delegation** — Passes all CRUD operations to `BookingService`; parses incoming JSON via `BookingDTO.fromJson()`.
4. **View Rendering** — `renderWeekView()` and `renderDayView()` generate HTML strings; `renderEventCards()` escapes user data via `BookingValidator`.
5. **Error display** — Catches `BookingValidationException` and shows `Clients.showNotification()`.

**Key Methods:**

| Method | Description |
|--------|-------------|
| `initForm()` | Initialize UI, wire event listeners, instantiate BookingService, load first view. |
| `refreshView()` | Re-render the current view mode (Timeline, Week, or Day). |
| `renderWeekView()` | Build 5-day (Mon–Fri) HTML grid with event cards. |
| `renderDayView()` | Build single-day HTML grid with resource-as-columns. |
| `buildResourceNameMap()` | Create `Map<Integer, String>` of resource ID → name. |
| `sortAndPackEvents()` | Pack overlapping events into non-overlapping columns. |
| `renderEventCards()` | Render positioned, styled, permission-aware event cards using `BookingValidator.escapeForHtml/Js()`. |
| `appendViewFooter()` | Close divs, add current-time indicator, inject HTML into ZK. |
| `getBookingJSON()` | Generate vis.js-compatible JSON for Timeline view. |
| `fetchBookings()` | Delegate to `BookingService.fetchBookings()` for a custom date range. |
| `getResourceJSON()` | Delegate to `BookingService.fetchGroups()` for the selected resource type. |
| `isWritable()` | Check if current user's role has Read/Write access to the form. |

**Removed in 3.33:** `updateBooking(JSONObject)`, `updateBooking(int, int, Timestamp, Timestamp)`, `deleteBooking(JSONObject)`, `isInteger(String)`, `errorMessage` field.

### 4.2. BookingDTO

**File:** `src/tw/ninniku/booking/form/BookingDTO.java`

Typed, immutable data transfer object. Replaces all raw `JSONObject` parsing at call sites.

**Fields:**

| Field | Type | JSON Key | Notes |
|-------|------|---------|-------|
| `bookingId` | `int` | `"s_booking_id"` | 0 = new record |
| `groupResourceId` | `int` | `"group"` | vis.js group ID |
| `sResourceId` | `int` | `"s_resource_id"` | iDempiere S_Resource_ID |
| `name` | `String` | `"booking-name"` | required, non-blank |
| `description` | `String` | `"description"` | optional |
| `startTime` | `Timestamp` | `"startTimestamp"` | epoch ms |
| `endTime` | `Timestamp` | `"endTimestamp"` | epoch ms |
| `assignFrom` | `Timestamp` | `"assign-date-from-timestamp"` | epoch ms |
| `assignTo` | `Timestamp` | `"assign-date-to-timestamp"` | epoch ms |
| `isWeekly` | `boolean` | `"is-weekly"` | `"Y"` or absent |
| `weeklyEndDate` | `Timestamp` | `"repeat-date-to-timestamp"` | null if !isWeekly |

**Factory:** `BookingDTO.fromJson(String raw)` — wraps all `JSONException` as `BookingValidationException("Invalid form data")`. Uses `optString()`/`optLong()` throughout; never raw casts.

### 4.3. BookingValidationException

**File:** `src/tw/ninniku/booking/form/BookingValidationException.java`

Checked exception carrying a user-facing message string. Thrown by `BookingDTO.fromJson()`, `BookingValidator.validate()`, and permission checks in `BookingService`. Caught in `BookingTimeline.onEvent()` and displayed via `Clients.showNotification()`.

### 4.4. BookingValidator

**File:** `src/tw/ninniku/booking/form/BookingValidator.java`

All validation rules and output-escaping in one place.

| Method | Purpose |
|--------|---------|
| `validate(BookingDTO dto)` | Checks: name non-blank, startTime < endTime, weeklyEndDate non-null and after endTime when isWeekly. |
| `escapeForJs(String value)` | Escapes for JS string literals in `onclick` attributes. Order: `\` → `'` → `"` → `\n` → `\r`. Returns `""` for null. |
| `escapeForHtml(String value)` | Escapes for HTML text nodes. Order: `&` → `<` → `>` → `"` → `'`. Returns `""` for null. |

### 4.5. BookingService

**File:** `src/tw/ninniku/booking/form/BookingService.java`

All CRUD business logic extracted from the controller. Owns the transaction lifecycle.

| Method | Description |
|--------|-------------|
| `saveBooking(BookingDTO, boolean isAdmin, int currentUserId)` | Create or update booking; weekly recurrence all-or-nothing within one `Trx`. |
| `updateBookingTime(int, int, Timestamp, Timestamp, boolean, int)` | Drag-drop time update; auto-commit single record. |
| `deleteBooking(int, boolean, int)` | Permission check then `booking.delete(true)`. |
| `fetchBookings(int resourceTypeId, Timestamp, Timestamp)` | Query `S_ResourceAssignment` within time window. |
| `fetchGroups(int resourceTypeId)` | Load resources via `PreparedStatement`; returns `List<Group>`. |

**Transaction contract:** `saveBooking` and `deleteBooking` call `Trx.get()` → `commit()` on success → `rollback()` + `close()` on any exception. `fetchBookings` and `fetchGroups` use the default auto-commit connection.

**Permission contract:** `saveBooking`, `updateBookingTime`, and `deleteBooking` accept `boolean isAdmin` and `int currentUserId` (resolved from `isWritable()` and `Env.getContextAsInt(ctx, "#AD_User_ID")` in the controller). They throw `BookingValidationException` for permission failures.

### 4.6. View: `meetingroom.zul` / `meetingroom_tw.zul`

Defines the layout using ZK's Native namespace (`xmlns:n="native"`) to embed raw HTML.

**Structure:**

* **Toolbar** — Version (read from OSGi bundle), view buttons (Week \| Day \| Timeline), resource dropdown, Add Booking, navigation, work hours checkbox, Refresh.
* **Booking Container** — A `div` that hosts either vis.js Timeline or server-rendered HTML for Week/Day.
* **Hidden Form (`#update-form`)** — jQuery UI dialog for booking details (Subject, Memo, Date Range, Recurring options).
* **Hidden Data Fields** — `visible="false"` textboxes acting as JS→Java communication channel:
  * `bookingUpdated` — triggers create/update flow
  * `bookingDeleted` — triggers delete flow
  * `itemData` — carries JSON payload

**As of 3.33:** The ZUL file contains **no inline `<script>` logic**. All JS is in external files loaded via `<script src="...">` tags.

### 4.7. Client Logic: `booking.js` / `booking_weekview.js`

Both files use IIFE namespace pattern to avoid global variable pollution.

**`booking.js` → `BookingApp.Timeline`**

Owns Timeline-view-specific symbols. Public API:

| Symbol | Purpose |
|--------|---------|
| `initChart()` | Initialize vis.js Timeline instance. |
| `drawChart()` | Refresh Timeline data and redraw. |
| `setGroups(g)` | Update vis.js groups array. |
| `setItems(data)` | Replace vis.js items DataSet. |
| `getGroups()` | Return current groups array (used by WeekView). |
| `clickNew(item, cb)` | Open new booking dialog. |
| `openEditDialog(item, cb)` | Open edit dialog with pre-filled data. |

**`booking_weekview.js` → `BookingApp.WeekView`**

Owns all Week/Day view symbols. Public API:

| Symbol | Purpose |
|--------|---------|
| `clickNew(item, cb)` | Open new booking dialog (delegates to BookingApp.Timeline). |
| `openEditDialog(item, cb)` | Open edit dialog (delegates to BookingApp.Timeline). |
| `openCustomAddDialog(dateStr, minOffset, resId)` | Programmatic open for click-to-create. |
| `openCustomEditDialog(id, name, desc, resId, startMs, endMs)` | Programmatic open for card click. |
| `openCustomAddDialogRange(startMs, endMs, resId)` | Open dialog after drag-to-create. |
| `onWeekDayClick(event, elem, dayKey, resId)` | Column click handler. |
| `onWeekEventClick(event, id, name, desc, resId, startMs, endMs)` | Event card click handler. |
| `onWeekEventDelete(event, id)` | Delete icon click handler. |
| `weekViewScrollTo8Am()` | Scroll view to 08:00. |
| `updateTimeIndicator()` | Reposition current-time line(s). |

Private helpers (not exported): `toTimestamp`, `convertFormToJSON`, `updateMeetingRoomSelector`, `showBeforeDate`, `validateBookingForm`, `initDragEvents`, `getTimeFromY`, `snapTo30`, `formatTime`, `formatDuration`, `getResourceColor`, `updateTooltip`, `triggerUpdate`.

**Script load order** in ZUL: `booking.js` must load before `booking_weekview.js` (WeekView delegates to Timeline at call time).

### 4.8. Model: `MResourceAssignment.java`

Extends `org.compiere.model.MResourceAssignment`.

* **Overlap Check**: `isOverlap()` — queries the DB to prevent double-booking of the same resource. Returns `true` if a conflict exists.
* **Known Limitation**: The check-then-act pattern is not atomic. Two concurrent saves can both pass `isOverlap()` and both insert. Requires a DB-level unique constraint or advisory lock to fix (out of scope).

## 5. View Rendering Architecture

### 5.1. Shared Rendering Pipeline (Week & Day Views)

```
bookingService.fetchBookings(resourceTypeId, start, end)
    ↓
Filter events by column (day or resource)
    ↓
sortAndPackEvents(events)          → Pack into non-overlapping columns
    ↓
renderEventCards(columns, ...)     → Generate escaped, positioned HTML cards
    ↓
appendViewFooter(sb, ...)          → Close HTML, add time indicator, inject into ZK
```

### 5.2. Event Packing Algorithm (`sortAndPackEvents`)

1. Sort events by start time (ties broken by longer duration first).
2. For each event, scan existing columns left-to-right.
3. Place in the first column where it doesn't overlap any existing event.
4. If no column fits, create a new column.
5. Return `List<List<MResourceAssignment>>` — each inner list is a column of non-overlapping events.

### 5.3. Event Card Rendering (`renderEventCards`)

Each card receives:

* **Position**: `top` = offset from day start (px), `height` = duration (px), min 15px.
* **Width**: `95% / numColumns`, offset by column index.
* **Color**: From a 19-color Material Design palette, assigned by resource ID hash.
* **Permissions**: If user is creator or has Admin access:
  * Card gets `.editable` CSS class (enables drag cursor).
  * Delete icon (`×`) rendered in top-right corner.
  * Resize handle rendered at bottom.
* **Content**: All user-controlled values escaped via `BookingValidator.escapeForHtml()` (card body) and `BookingValidator.escapeForJs()` (`onclick` attributes).
* **Click handler**: `BookingApp.WeekView.onWeekEventClick(...)` to open edit dialog.

### 5.4. Week View Specifics

* **Days**: Monday–Friday (5 columns), calculated from the Monday of the current week.
* **Navigation**: `<` / `>` moves by 1 week, `Today` jumps to current week.
* **Header**: Day name + date (e.g. `Mon 02/26`).
* **Grid background**: Repeating 40px horizontal stripes (alternating #f9f9f9 / white).

### 5.5. Day View Specifics

* **Columns**: One per resource (meeting room), filtered by selected resource type.
* **Navigation**: `<` / `>` moves by 1 day, `Today` jumps to today.
* **Title row**: Full-width centered date (e.g. `2026/02/26 (Thu)`).
* **Header**: Resource names, color-coded.

### 5.6. Timeline View

* Rendered entirely client-side using vis.js.
* Data loaded via `Clients.evalJavaScript()` calling `BookingApp.Timeline.setGroups(...)` and `BookingApp.Timeline.setItems(...)`.
* Options: editable (add, remove, updateGroup, updateTime), tooltip, snap to 30 min, hidden dates.

## 6. Permissions Model

Booking 的操作權限採用**雙層檢查**機制：UI 層（前端）阻擋非授權使用者的互動元素，Backend 層（Java）在實際執行前再次驗證，防止繞過 UI 的請求。

### 6.1. 管理者判定 (`isWritable()`)

「管理者」的定義為：**使用者的 Role 對此 Booking Form 具有 Read/Write 存取權限**。

```java
private boolean isWritable() {
    String sql = "SELECT isreadwrite FROM AD_Form_Access "
               + "WHERE ad_role_id = ? AND ad_form_id = ?";
    String result = DB.getSQLValueString(null, sql,
        new Object[] { Env.getCtx().getProperty("#AD_Role_ID"), getAdFormId() });
    return "Y".equals(result);
}
```

* 查詢 `AD_Form_Access` 表，以當前 `AD_Role_ID` + `AD_Form_ID` 為條件。
* `IsReadWrite = 'Y'` → 管理者（可操作所有人的預約）。
* `IsReadWrite = 'N'` 或無記錄 → 一般使用者（只能操作自己的預約）。
* 此設定可在 iDempiere **Role** 視窗 → **Form Access** 頁籤中針對每個 Role 個別調整。

### 6.2. 操作權限規則

每個預約（Event）的可操作判定公式：

```
canModify = isWritable() || (booking.CreatedBy == @#AD_User_ID@)
```

| 角色 | 條件 | 可執行操作 |
|------|------|-----------|
| **管理者** | `AD_Form_Access.IsReadWrite = 'Y'` | 拖曳、調整大小、刪除、編輯**所有人**的預約 |
| **建立者** | `booking.CreatedBy == @#AD_User_ID@` | 拖曳、調整大小、刪除、編輯**自己**的預約 |
| **其他使用者** | 以上兩者皆不符合 | 僅能**檢視**預約，無法修改或刪除 |

### 6.3. UI 層控制（前端）

**Week View & Day View** — `renderEventCards()` (BookingTimeline.java):

| `canModify` | `.editable` CSS class | Delete icon (`×`) | Resize handle | Drag-to-Move |
|-------------|----------------------|-------------------|---------------|-------------|
| `true` | 加上 | 顯示 | 顯示 | 可拖曳 |
| `false` | 不加 | 不顯示 | 不顯示 | 不可拖曳 |

**Timeline View** — `getBookingJSON()` (BookingTimeline.java):

* 每個 vis.js item 的 `editable` flag 在 Java 端根據 `canModify` 設定。
* vis.js options 中 `overrideItems: false`，確保全域設定不會覆蓋 per-item 的 `editable` flag。

### 6.4. Backend 層控制（Java）

即使前端被繞過，`BookingService` 在實際執行 CRUD 操作前會再次驗證權限：

**`saveBooking(dto, isAdmin, currentUserId)`** — 更新既有預約（id > 0）時：
```java
if (id > 0 && !isAdmin && existing.getCreatedBy() != currentUserId) {
    throw new BookingValidationException("Permission denied: ...");
}
```

**`updateBookingTime(bookingId, ..., isAdmin, currentUserId)`** — Drag-and-Drop 更新：
```java
if (!isAdmin && booking.getCreatedBy() != currentUserId) {
    throw new BookingValidationException("Permission denied: ...");
}
```

**`deleteBooking(bookingId, isAdmin, currentUserId)`** — 刪除：
```java
if (!isAdmin && booking.getCreatedBy() != currentUserId) {
    throw new BookingValidationException("Permission denied: ...");
}
```

**新增預約（`bookingId == 0`）不需要權限檢查**，所有使用者皆可建立。

### 6.5. 例外處理對照表

| Exception | 來源 | Controller 動作 |
|-----------|------|----------------|
| `BookingValidationException` | `BookingDTO.fromJson()`, `BookingValidator.validate()`, `BookingService` 權限檢查 | Catch → `Clients.showNotification(e.getMessage())` |
| `AdempiereException` (unchecked) | `BookingService` DB 操作失敗 | 傳播至 ZK 預設錯誤處理器 |

### 6.6. 權限檢查流程圖

```
使用者操作 (Drag / Delete / Edit)
  │
  ├─ UI 層檢查
  │   ├─ Week/Day: .editable class? delete icon 存在?
  │   └─ Timeline: item.editable flag?
  │   └─ 不通過 → 操作被前端阻擋（無互動元素）
  │
  └─ 通過 → JS 送出請求到 Java Backend
      │
      ├─ BookingDTO.fromJson() — 解析與格式驗證
      ├─ BookingValidator.validate() — 業務規則驗證
      └─ BookingService — 權限驗證 + 執行 CRUD
          └─ 不通過 → BookingValidationException → showNotification
          └─ 通過 → save() / delete() + refreshView()
```

## 7. Data Flow

### 7.1. Load Flow

```
User opens Form
  → BookingTimeline.initForm()
  → bookingService instantiated
  → Timeline: bookingService.fetchGroups() + getBookingJSON()
             → Clients.evalJavaScript("BookingApp.Timeline.setGroups(...); BookingApp.Timeline.setItems(...);")
  → Week/Day: bookingService.fetchBookings() → renderWeekView() / renderDayView()
  → View rendered in browser
```

### 7.2. Create/Update Flow

```
User action (drag/dialog submit)
  → JS serializes data to JSON → sets itemData hidden field → fires bookingUpdated.onChange
  → BookingTimeline.onEvent()
  → BookingDTO.fromJson(raw)           — parse + format validation
  → BookingValidator.validate(dto)     — business rule validation
  → bookingService.saveBooking(dto, isAdmin, userId)
      → permission check (id > 0)
      → Trx.get() → save records (weekly: loop) → Trx.commit()
      → on error: rollback + throw AdempiereException
  → refreshView()
```

### 7.3. Delete Flow

```
User clicks delete icon or dialog Delete button
  → JS fires bookingDeleted.onChange with {id}
  → bookingService.deleteBooking(bookingId, isAdmin, userId)
      → permission check → booking.delete(true)
  → refreshView()
```

### 7.4. Drag-Drop Time Update Flow

```
User drags event to new time (Timeline) or Week/Day view auto-save
  → JS fires dateLast.onChange with {s_booking_id, group, startTimestamp, endTimestamp}
  → bookingService.updateBookingTime(bookingId, resourceId, start, end, isAdmin, userId)
      → permission check → booking.setAssignDateFrom/To → booking.save()
  → refreshView()
```

## 8. CSS & Layout

### 8.1. Grid Layout

* **Time column**: Fixed width, shows hour labels.
* **Day/Resource columns**: `flex: 1`, equal width, `position: relative` for card positioning.
* **Scroll body**: `overflow-y: auto`, `max-height: 600px`.
* **Sticky header**: `position: sticky; top: 0; z-index: 10`.
* **Grid lines**: `background: repeating-linear-gradient(...)` — 40px per hour.

### 8.2. Event Card Styling

* `position: absolute` within column.
* `border-radius: 4px`, `box-shadow` for depth.
* `cursor: grab` for editable cards, `cursor: default` for read-only.
* Delete icon: `position: absolute; top: 2px; right: 4px`, red on hover.
* Resize handle: `height: 6px; cursor: ns-resize` at bottom of card.

### 8.3. Color Palette

19-color Material Design palette, assigned by `resourceId % 19`:

```
#4FC3F7, #81C784, #FFB74D, #E57373, #BA68C8,
#4DD0E1, #AED581, #FFD54F, #F06292, #7986CB,
#A1887F, #90A4AE, #FF8A65, #DCE775, #4DB6AC,
#9575CD, #FFF176, #64B5F6, #E0E0E0
```

## 9. Installation & Configuration

1. **Environment**: iDempiere 11+, Java 17.
2. **Build**: Maven (`mvn clean install`) or Eclipse PDE export.
3. **Deploy**: Install the `.jar` via Felix Web Console or place in `plugins/` directory. Ensure the `tw.ninniku.booking` bundle is in "Active" state.
4. **Database**: Requires standard tables `S_Resource`, `S_ResourceType`, `S_ResourceAssignment`.
5. **Menu**: Register the Form in iDempiere "Form" window referencing `tw.ninniku.booking.form.BookingTimeline`. Add the Form to the Menu.

## 10. Version History (3.x)

| Version | Changes |
|---------|---------|
| **3.00** | Added Week View with weekly calendar grid alongside existing Timeline View. |
| **3.01** | Added "Add Booking" button in toolbar for Week View. |
| **3.06** | Added delete function in Week View (delete icon on event cards). |
| **3.2x** | Drag-and-drop for Week View (create, move, resize). Timezone fix for drag-and-drop operations. Name validation on booking form. Permission restriction: drag/resize limited to Creator or Admin. Work Hours toggle checkbox (08:00–18:00 vs 00:00–23:00). |
| **3.30** | Added **Day View** (single-day, resource-as-columns). Removed Month View. Reordered toolbar buttons to Week \| Day \| Timeline. Week View changed to **Mon–Fri** (5-day) to match Flutter app. |
| **3.31** | Code refactoring: extracted shared helpers (`buildResourceNameMap`, `sortAndPackEvents`, `renderEventCards`, `appendViewFooter`). Removed dead code (`convertTimestamp`, `draw`, unused imports). Fixed duplicate `getFellow` calls. |
| **3.32** | Backend 權限檢查：`deleteBooking`、`updateBooking`（Dialog）、`updateBooking`（Drag-and-Drop）加入 `CreatedBy == AD_User_ID \|\| isWritable()` 驗證。刪除失敗時顯示 Permission denied 通知。 |
| **3.33** | 安全性與架構全面重構。新增 `BookingDTO`（型別化 JSON 解析）、`BookingValidationException`、`BookingValidator`（XSS 防護：`escapeForJs` + `escapeForHtml`）、`BookingService`（CRUD 業務層）。`BookingTimeline` 薄化為純事件接線層。`booking.js` 重構為 `BookingApp.Timeline` IIFE；`booking_weekview.js` 重構為 `BookingApp.WeekView` IIFE 並吸收 ZUL inline script（~670 行）。`meetingroom.zul` 移除所有內嵌 JS。Plugin 版本改由 OSGi bundle registry 動態讀取。`pom.xml` Java 版本對齊 17。 |
