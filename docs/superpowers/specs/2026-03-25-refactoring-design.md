# Refactoring Design: Full Structural Refactor to Eliminate Hidden Bugs

**Date:** 2026-03-25
**Version:** 1.0
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

```
booking.js           →  BookingApp.Timeline  (IIFE namespace)
booking_weekview.js  →  BookingApp.WeekView  (IIFE namespace)
meetingroom.zul      →  no inline script block (moved to BookingApp.WeekView)
```

---

## 4. Component Design

### 4.1 BookingDTO

A typed, immutable data transfer object that replaces all raw `JSONObject` parsing at call sites.

**File:** `src/tw/ninniku/booking/form/BookingDTO.java`

```java
public class BookingDTO {
    public final int resourceId;
    public final int bookingId;       // 0 for new
    public final String name;
    public final String description;
    public final Timestamp startTime;
    public final Timestamp endTime;
    public final boolean isWeekly;
    public final Timestamp weeklyEndDate; // null if not weekly

    public static BookingDTO fromJson(String raw) throws BookingValidationException { ... }
}
```

**Responsibilities:**
- Parse JSON string once in `fromJson()`
- Validate all required fields are present and non-null
- Validate types before casting (use `json.optString()`, `json.optLong()` etc.)
- Throw `BookingValidationException` (checked) with a descriptive message on any failure
- No business logic — pure data container

### 4.2 BookingValidationException

**File:** `src/tw/ninniku/booking/form/BookingValidationException.java`

A checked exception thrown by `BookingDTO.fromJson()` and `BookingValidator`. Carries a user-facing message string.

### 4.3 BookingValidator

All business validation rules in one place.

**File:** `src/tw/ninniku/booking/form/BookingValidator.java`

**Responsibilities:**
- `validate(BookingDTO dto)` — throws `BookingValidationException` if:
  - `name` is empty or blank
  - `startTime >= endTime`
  - `weeklyEndDate` is before `endTime` when `isWeekly == true`
- `escapeForJs(String value)` — static utility for safe JS string escaping, used by `renderEventCards()`:
  - Escapes `\` first (to avoid double-escaping)
  - Then escapes `'`, `"`, newlines, carriage returns
  - Returns empty string for null input

### 4.4 BookingService

All business logic extracted from `BookingTimeline.java`.

**File:** `src/tw/ninniku/booking/form/BookingService.java`

**Responsibilities:**
- `saveBooking(BookingDTO dto, MUser user)` — creates or updates `MBooking` + linked `MResourceAssignment`; handles weekly recurrence loop; owns the transaction lifecycle (`Trx.get`, `commit`, `rollback`, `close`)
- `deleteBooking(int bookingId, MUser user)` — permission check (creator or admin), cascade delete attendees, delete `MResourceAssignment`, delete `MBooking`; owns transaction
- `loadBookingsForResource(int resourceTypeId, Timestamp from, Timestamp to)` — returns `List<MBooking>`
- `loadGroups(int resourceTypeId)` — returns `List<Group>`

**Error handling:** All methods throw `AdempiereException` on failure after rolling back the transaction.

### 4.5 BookingTimeline (Refactored Controller)

Reduced from ~929 lines to ~400–500 lines.

**Responsibilities (after refactor):**
- ZK event wiring only
- Parse incoming event data → `BookingDTO.fromJson()` (wrapped in try-catch for `BookingValidationException` and `JSONException`)
- Call `BookingService` methods
- Handle result: display error message or refresh view
- No direct `Trx`, `DB.prepareStatement`, or `JSONObject` parsing outside of `BookingDTO.fromJson()`

### 4.6 MResourceAssignment + MBooking (Model Layer)

**Changes:**
- Add a comment to `isOverlap()` / `beforeSave()` in both classes documenting the non-atomic race condition and the decision to leave it as-is

---

## 5. Frontend Design

### 5.1 JavaScript Namespace

All global variables in `booking.js` move into a module pattern:

```javascript
var BookingApp = BookingApp || {};
BookingApp.Timeline = (function() {
    var items, groups, options, timeline, container, cStart;

    function init(groupsData, itemsData, opts) { ... }
    function refresh(newItems) { ... }
    // all existing functions, now private or exported

    return {
        init: init,
        refresh: refresh,
        // ... other public API
    };
})();
```

Same pattern for `BookingApp.WeekView` in `booking_weekview.js`.

### 5.2 Inline Script Removal from ZUL

The ~400-line inline `<script>` block in `meetingroom.zul` (drag-drop and resize handlers) moves into `BookingApp.WeekView` in `booking_weekview.js`. The ZUL file retains only the `<script src="...">` import tags.

**Benefit:** Inline scripts cannot be linted, unit-tested, or easily diffed. Moving them to `.js` files makes the code reviewable and maintainable.

---

## 6. Build Fix

**`pom.xml`:** Change `<source>11</source>` and `<target>11</target>` to `17` to match the `JavaSE-17` execution environment declared in `META-INF/MANIFEST.MF`.

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
| `src/tw/ninniku/booking/form/BookingTimeline.java` | Thin controller: remove business logic, add DTO/service calls |
| `src/tw/ninniku/booking/model/MResourceAssignment.java` | Add race condition comment |
| `src/tw/ninniku/booking/model/MBooking.java` | Add race condition comment |
| `WEB-INF/web/js/booking.js` | Wrap in `BookingApp.Timeline` namespace |
| `WEB-INF/web/js/booking_weekview.js` | Wrap in `BookingApp.WeekView` namespace; absorb inline ZUL script |
| `WEB-INF/web/meetingroom.zul` | Remove inline script block |
| `pom.xml` | Fix Java version 11 → 17 |

---

## 9. Out of Scope

- Database-level fix for overlap check race condition (documented as known limitation)
- Unit tests (no test infrastructure exists; adding it is a separate effort)
- Permission model changes
- Audit trail / booking history
- UI changes
