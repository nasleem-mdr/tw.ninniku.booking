# Refactoring Design: Full Structural Refactor to Eliminate Hidden Bugs

**Date:** 2026-03-25
**Version:** 1.2
**Replaces:** `docs/plans/2026-02-25-refactoring-design.md`

---

## 1. Goals

Eliminate all identified hidden bugs in the tw.ninniku.booking plugin through a full structural refactor. The goals are:

1. **Safety** — eliminate XSS risk in inline HTML/JS generation and JSON parsing exceptions
2. **Correctness** — eliminate unsafe type casting, add proper input validation
3. **Maintainability** — separate concerns into focused classes; thin the controller
4. **Frontend robustness** — eliminate global JS variable pollution across concurrent users
5. **Build consistency** — fix Java version mismatch between pom.xml and MANIFEST.MF

---

## 2. Identified Bugs

| Priority | Issue | Location |
|---|---|---|
| High | XSS risk: inline HTML built by string concatenation with user data | `BookingTimeline.java:renderEventCards()` |
| High | Missing try-catch for JSONException during JSON parsing | `BookingTimeline.java:138–156` |
| Medium | Unsafe type casting without null/format validation (`json.get()` → Integer) | `BookingTimeline.java:142, 577` |
| Medium | Global JS variables collide across concurrent users/tabs | `booking.js:12–22` |
| Medium | ~400 lines of inline script in ZUL file (untestable, unmaintainable) | `meetingroom.zul` |
| Low | Race condition in overlap check (non-atomic check-then-act) | `MResourceAssignment.java`, `MBooking.java` |
| Low | `pom.xml` declares Java 11; `MANIFEST.MF` requires JavaSE-17 | build config |

### Race Condition Note
The overlap check in `MResourceAssignment.beforeSave()` and `MBooking.beforeSave()` uses a check-then-act pattern that is not atomic at the database level. Two concurrent saves could both pass `isOverlap()` and both insert, causing a double-booking. Fixing this properly requires a database-level unique constraint or advisory lock, which is out of scope for this refactor. A comment will be added to the code documenting this known limitation.

---

## 3. Architecture

### 3.1 Backend Layers

```
┌─────────────────────────────────────┐
│  BookingTimeline.java (Controller)  │  thin: wires ZK UI events to service calls
├─────────────────────────────────────┤
│  BookingService.java (new)          │  business logic: save, delete, load
├─────────────────────────────────────┤
│  BookingValidator.java (new)        │  all validation + sanitisation rules
├─────────────────────────────────────┤
│  BookingDTO.java (new)              │  typed data transfer object
├─────────────────────────────────────┤
│  MBooking / MResourceAssignment     │  model layer (unchanged, + risk comment)
└─────────────────────────────────────┘
```

### 3.2 Frontend Modules

The current codebase has duplicate symbol definitions: `booking.js` defines week-view `window.*` functions that are also defined in `booking_weekview.js` (the latter acts as a fallback). The refactor eliminates the duplication by establishing clear ownership:

```
booking.js           →  BookingApp.Timeline  — owns Timeline-view-specific symbols only
booking_weekview.js  →  BookingApp.WeekView  — owns Week/Day-view symbols + absorbs ZUL inline script
meetingroom.zul      →  no inline script block; calls BookingApp.Timeline.* or BookingApp.WeekView.*
```

Week-view symbol definitions are **removed** from `booking.js`. `BookingApp.WeekView` is the single authoritative owner.

---

## 4. Component Design

### 4.1 BookingDTO

A typed, immutable data transfer object that replaces all raw `JSONObject` parsing at call sites.

**File:** `src/tw/ninniku/booking/form/BookingDTO.java`

**Expected JSON schema** (keys as sent from the browser form):

| Key | Type in JSON | Java field | Notes |
|---|---|---|---|
| `"s_booking_id"` | String (numeric) | `int bookingId` | 0 for new bookings |
| `"group"` | String (numeric) | `int groupResourceId` | vis.js group ID (resource row ID in timeline) |
| `"s_resource_id"` | String (numeric) | `int sResourceId` | iDempiere S_Resource_ID (different from group ID) |
| `"booking-name"` | String | `String name` | required, non-blank |
| `"description"` | String | `String description` | optional |
| `"startTimestamp"` | String (epoch ms) | `Timestamp startTime` | required |
| `"endTimestamp"` | String (epoch ms) | `Timestamp endTime` | required |
| `"assign-date-from-timestamp"` | String (epoch ms) | `Timestamp assignFrom` | required |
| `"assign-date-to-timestamp"` | String (epoch ms) | `Timestamp assignTo` | required |
| `"is-weekly"` | String `"Y"` or absent | `boolean isWeekly` | absent = false |
| `"repeat-date-to-timestamp"` | String (epoch ms) | `Timestamp weeklyEndDate` | null if !isWeekly |

**Note:** `groupResourceId` (from `"group"`) and `sResourceId` (from `"s_resource_id"`) represent the same physical resource referenced by two different identifier systems: the vis.js timeline group ID vs. the iDempiere `S_Resource_ID`. Both are required for saving. The controller currently uses `"group"` for vis.js and `"s_resource_id"` for the DB record — `BookingDTO` carries both.

**Class stub:**

```java
public class BookingDTO {
    public final int bookingId;          // 0 = new record
    public final int groupResourceId;    // vis.js group ID
    public final int sResourceId;        // iDempiere S_Resource_ID
    public final String name;
    public final String description;
    public final Timestamp startTime;
    public final Timestamp endTime;
    public final Timestamp assignFrom;
    public final Timestamp assignTo;
    public final boolean isWeekly;
    public final Timestamp weeklyEndDate; // null if !isWeekly

    private BookingDTO(...) { ... }       // all-args constructor

    /**
     * Parses raw JSON string from ZK event data.
     * Wraps any JSONException as BookingValidationException.
     * Throws BookingValidationException for missing or malformed fields.
     */
    public static BookingDTO fromJson(String raw) throws BookingValidationException { ... }
}
```

**Responsibilities:**
- Parse JSON string once in `fromJson()` using `JSONObject`; wrap any `JSONException` in `BookingValidationException` with message "Invalid form data"
- Use `json.optString()` / `json.optLong()` for all field access — no raw casts
- Validate numeric strings before parsing; throw `BookingValidationException` on malformed values
- No business logic — pure data container

### 4.2 BookingValidationException

**File:** `src/tw/ninniku/booking/form/BookingValidationException.java`

A checked exception thrown by `BookingDTO.fromJson()` and `BookingValidator.validate()`. Carries a user-facing message string.

```java
public class BookingValidationException extends Exception {
    public BookingValidationException(String userMessage) { super(userMessage); }
}
```

### 4.3 BookingValidator

All business validation rules in one place.

**File:** `src/tw/ninniku/booking/form/BookingValidator.java`

```java
public class BookingValidator {
    /** Throws BookingValidationException if dto fails business rules. */
    public static void validate(BookingDTO dto) throws BookingValidationException { ... }

    /**
     * Escapes a string for safe embedding in a JavaScript single-quoted string literal.
     * Escape order: \ (first, to avoid double-escaping), then ', ", \n, \r.
     * Returns "" for null input.
     */
    public static String escapeForJs(String value) { ... }
}
```

`validate()` checks:
- `name` is non-null and non-blank
- `startTime` is before `endTime`
- When `isWeekly == true`: `weeklyEndDate` is non-null and after `endTime`

`escapeForJs()` replaces the manual escape chains in `renderEventCards()` and eliminates the XSS risk. No test stubs or `src/test/` scaffolding are created as part of this work (out of scope per section 9).

### 4.4 BookingService

All business logic extracted from `BookingTimeline.java`.

**File:** `src/tw/ninniku/booking/form/BookingService.java`

**Note on `Group` type:** `Group` is an existing class at `src/tw/ninniku/timeline/Group.java`. It is not modified by this refactor.

```java
public class BookingService {

    /**
     * Creates or updates a booking and its linked MResourceAssignment.
     * For weekly bookings, creates one MBooking per week until weeklyEndDate.
     * This is an all-or-nothing operation: if any iteration fails, the
     * entire transaction is rolled back (no partial saves).
     * @return the saved MBooking's S_Booking_ID (for use in view refresh)
     * @throws AdempiereException on DB failure (transaction is rolled back before throwing)
     */
    public int saveBooking(BookingDTO dto, MUser user) throws AdempiereException { ... }

    /**
     * Deletes a booking if the user has permission (creator or admin).
     * Cascade-deletes attendees and the linked MResourceAssignment.
     * Owns the transaction lifecycle (all-or-nothing).
     * @throws BookingValidationException if user lacks permission (shown as user-facing message)
     * @throws AdempiereException on DB failure (transaction is rolled back before throwing)
     */
    public void deleteBooking(int bookingId, MUser user)
            throws BookingValidationException, AdempiereException { ... }

    /**
     * Loads all bookings for the given resource type within the time window.
     * Returns an empty list if no bookings found.
     * @throws AdempiereException on DB failure
     */
    public List<MBooking> loadBookingsForResource(
            int resourceTypeId, Timestamp from, Timestamp to) throws AdempiereException { ... }

    /**
     * Loads timeline groups (resources) for the given resource type.
     * Returns an empty list if no groups found.
     * @throws AdempiereException on DB failure
     */
    public List<Group> loadGroups(int resourceTypeId) throws AdempiereException { ... }
}
```

**Transaction contract:**
- `saveBooking` and `deleteBooking` own their `Trx` lifecycle: `Trx.get` → `commit` on success → `rollback` + `close` on any exception
- `deleteBooking` throws `BookingValidationException` for permission failures so the controller can show a distinct user-facing message, not a generic system error
- `loadBookingsForResource` and `loadGroups` do not manage transactions; they use the default (auto-commit) connection

### 4.5 BookingTimeline (Refactored Controller)

Reduced from ~929 lines to ~400–500 lines.

**Responsibilities (after refactor):**
- ZK event wiring only — no `Trx`, no `DB.prepareStatement`, no direct `JSONObject` parsing
- For save events: `BookingDTO.fromJson(raw)` → `BookingValidator.validate(dto)` → `BookingService.saveBooking(dto, user)` → `refreshView()`
- For delete events: `BookingService.deleteBooking(id, user)` → `refreshView()`
- For load events: `BookingService.loadBookingsForResource(...)` + `BookingService.loadGroups(...)` → `renderView(bookings, groups)`

**Exception handling in the controller:**

| Exception | Source | Controller action |
|---|---|---|
| `BookingValidationException` | `BookingDTO.fromJson()`, `BookingValidator.validate()`, or `BookingService.deleteBooking()` | Catch, show `getMessage()` in ZK error label |
| `AdempiereException` (unchecked) | `BookingService` methods | Let propagate to ZK's default error handler (same behaviour as current code) |

`JSONException` is always wrapped by `BookingDTO.fromJson()` and surfaces as `BookingValidationException`. The controller does not need a separate `JSONException` catch.

**Rendering flow after save/load:**
- `renderEventCards()` stays in the controller (it is a view-rendering method, not business logic)
- It calls `BookingValidator.escapeForJs(b.getName())` and `BookingValidator.escapeForJs(b.getDescription())` instead of the current inline escape chains
- Data for rendering comes from `BookingService.loadBookingsForResource()` returning `List<MBooking>`
- The rendered HTML `StringBuilder` is set on the ZK `Html` component in the booking container, same as current behaviour

### 4.6 MResourceAssignment + MBooking (Model Layer)

**Changes:**
- Add a comment to `isOverlap()` / `beforeSave()` in both classes:

```java
// KNOWN LIMITATION: This check-then-act pattern is not atomic at the database level.
// Two concurrent saves can both pass isOverlap() and both insert, causing a double-booking.
// Fixing this requires a DB-level unique constraint or advisory lock (out of scope).
```

---

## 5. Frontend Design

### 5.1 BookingApp.Timeline (booking.js)

Owns Timeline-view-specific symbols only. Week/day-view symbols currently duplicated in `booking.js` are **removed** — `BookingApp.WeekView` is the authoritative owner of those.

```javascript
var BookingApp = BookingApp || {};
BookingApp.Timeline = (function() {
    // private state (previously global)
    var isload, items, groups, options, timeline, container, cStart, hiddenDates;

    // ... all existing timeline functions ...

    // Public API — Timeline-view symbols only
    return {
        initChart:   initChart,   // called from ZUL after server renders timeline view
        drawChart:   drawChart    // called from ZUL to refresh timeline data
    };
})();
```

ZUL call sites for timeline: `BookingApp.Timeline.initChart(...)`, `BookingApp.Timeline.drawChart(...)`.

### 5.2 BookingApp.WeekView (booking_weekview.js)

Owns all Week/Day-view symbols, including those absorbed from the ZUL inline script block.

```javascript
var BookingApp = BookingApp || {};
BookingApp.WeekView = (function() {
    // private helpers (previously in ZUL inline block or booking_weekview.js IIFE)
    function toTimestamp(strDate) { ... }
    function convertFormToJSON(form) { ... }
    function updateMeetingRoomSelector() { ... }
    function showBeforeDate() { ... }
    function validateBookingForm() { ... }
    function initDragEvents() { ... }
    function getTimeFromY(y, dayKey) { ... }
    function snapTo30(d) { ... }
    function formatTime(date) { ... }
    function formatDuration(diffMs) { ... }
    function getResourceColor(id) { ... }
    function updateTooltip(start, end, e) { ... }
    function triggerUpdate(s_booking_id, group, start, end) { ... }
    function doScroll() { ... }

    // Public API — all symbols callable from ZUL or from BookingApp.Timeline
    return {
        clickNew:                  clickNew,
        openEditDialog:            openEditDialog,
        openCustomAddDialog:       openCustomAddDialog,
        openCustomEditDialog:      openCustomEditDialog,
        openCustomAddDialogRange:  openCustomAddDialogRange,
        onWeekDayClick:            onWeekDayClick,
        onWeekEventClick:          onWeekEventClick,
        onWeekEventDelete:         onWeekEventDelete,
        weekViewScrollTo8Am:       weekViewScrollTo8Am,
        updateTimeIndicator:       updateTimeIndicator
    };
})();
```

### 5.3 Inline Script Removal from ZUL

The ~400-line inline `<script><![CDATA[...]]></script>` block in `meetingroom.zul` moves entirely into `BookingApp.WeekView` in `booking_weekview.js`.

**ZUL call site changes** — old `window.*` references become `BookingApp.WeekView.*`:

| Old (inline script / window.*) | New (after refactor) |
|---|---|
| `window.clickNew` | `BookingApp.WeekView.clickNew` |
| `window.openEditDialog` | `BookingApp.WeekView.openEditDialog` |
| `window.openCustomAddDialog` | `BookingApp.WeekView.openCustomAddDialog` |
| `window.openCustomEditDialog` | `BookingApp.WeekView.openCustomEditDialog` |
| `window.openCustomAddDialogRange` | `BookingApp.WeekView.openCustomAddDialogRange` |
| `window.onWeekDayClick` | `BookingApp.WeekView.onWeekDayClick` |
| `window.onWeekEventClick` | `BookingApp.WeekView.onWeekEventClick` |
| `window.onWeekEventDelete` | `BookingApp.WeekView.onWeekEventDelete` |
| `window.weekViewScrollTo8Am` | `BookingApp.WeekView.weekViewScrollTo8Am` |
| `window.updateTimeIndicator` | `BookingApp.WeekView.updateTimeIndicator` |

The ZUL file retains only `<script src="...">` import tags. No other ZUL structural changes are needed.

---

## 6. Build Fix

**`pom.xml`:** Change `<source>11</source>` and `<target>11</target>` to `17`.

**`META-INF/MANIFEST.MF`:** No change needed — it already correctly declares `Bundle-RequiredExecutionEnvironment: JavaSE-17`.

---

## 7. New Files

| File | Type | Purpose |
|---|---|---|
| `src/tw/ninniku/booking/form/BookingDTO.java` | New | Typed DTO replacing raw JSON |
| `src/tw/ninniku/booking/form/BookingValidationException.java` | New | Checked exception for validation failures |
| `src/tw/ninniku/booking/form/BookingValidator.java` | New | Validation + JS escaping utility |
| `src/tw/ninniku/booking/form/BookingService.java` | New | Business logic layer |

---

## 8. Modified Files

| File | Change |
|---|---|
| `src/tw/ninniku/booking/form/BookingTimeline.java` | Thin controller: remove business logic, add DTO/validator/service calls; rendering uses `BookingValidator.escapeForJs()` |
| `src/tw/ninniku/booking/model/MResourceAssignment.java` | Add race condition comment |
| `src/tw/ninniku/booking/model/MBooking.java` | Add race condition comment |
| `WEB-INF/web/js/booking.js` | Wrap in `BookingApp.Timeline` IIFE; remove week-view symbol definitions |
| `WEB-INF/web/js/booking_weekview.js` | Wrap in `BookingApp.WeekView` IIFE; absorb inline ZUL script |
| `WEB-INF/web/meetingroom.zul` | Remove inline script block; update call sites to `BookingApp.WeekView.*` / `BookingApp.Timeline.*` |
| `pom.xml` | Fix Java version 11 → 17 |

---

## 9. Out of Scope

- Database-level fix for overlap check race condition (documented as known limitation)
- Unit tests and test infrastructure (no `src/test/` directory or test stubs created)
- Permission model changes
- Audit trail / booking history
- UI changes
