# Refactoring: Eliminate Hidden Bugs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate all identified hidden bugs through a full structural refactor: new typed DTO/validator/service classes, thin controller, namespaced JS modules, and a build config fix.

**Architecture:** New backend classes (`BookingDTO`, `BookingValidationException`, `BookingValidator`, `BookingService`) extract all business logic from `BookingTimeline.java`, leaving it as a thin ZK event-wiring layer. Frontend globals in `booking.js` and `booking_weekview.js` move into `BookingApp.Timeline` and `BookingApp.WeekView` IIFE namespaces; the ~400-line ZUL inline script moves to `booking_weekview.js`.

**Tech Stack:** Java 17, iDempiere OSGi, ZK Framework 9.6.4, vis.js (Timeline), jQuery, Maven

**Spec:** `docs/superpowers/specs/2026-03-25-refactoring-design.md`

---

## File Map

### New Files
| File | Responsibility |
|---|---|
| `src/tw/ninniku/booking/form/BookingValidationException.java` | Checked exception carrying user-facing messages |
| `src/tw/ninniku/booking/form/BookingDTO.java` | Typed DTO; parses + validates raw JSON from ZK events |
| `src/tw/ninniku/booking/form/BookingValidator.java` | Business rule validation + JS string escaping |
| `src/tw/ninniku/booking/form/BookingService.java` | All booking CRUD logic; owns transaction lifecycle |

### Modified Files
| File | Change |
|---|---|
| `src/tw/ninniku/booking/form/BookingTimeline.java` | Thin controller: replaces inline CRUD with service calls; updates JS eval strings for namespace |
| `src/tw/ninniku/booking/model/MResourceAssignment.java` | Add race condition comment |
| `src/tw/ninniku/booking/model/MBooking.java` | Add race condition comment |
| `WEB-INF/web/js/booking.js` | Wrap in `BookingApp.Timeline` IIFE; expose setters for groups/items; remove week-view duplicates |
| `WEB-INF/web/js/booking_weekview.js` | Wrap in `BookingApp.WeekView` IIFE; absorb all inline ZUL script functions |
| `WEB-INF/web/meetingroom.zul` | Remove inline script block; update 10 JS call sites to `BookingApp.WeekView.*` |
| `pom.xml` | Fix Java source/target 11 → 17 |

---

## Task 1: BookingValidationException

**Files:**
- Create: `src/tw/ninniku/booking/form/BookingValidationException.java`

- [ ] **Step 1: Create the exception class**

```java
package tw.ninniku.booking.form;

public class BookingValidationException extends Exception {
    private static final long serialVersionUID = 1L;

    public BookingValidationException(String userMessage) {
        super(userMessage);
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd /Users/ray/sources/tw.ninniku.booking
mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/tw/ninniku/booking/form/BookingValidationException.java
git commit -m "Add BookingValidationException for user-facing validation errors"
```

---

## Task 2: BookingDTO

**Files:**
- Create: `src/tw/ninniku/booking/form/BookingDTO.java`

**Context:** The ZK `itemData` hidden textbox carries a JSON string sent by the JS form. All numeric values are serialized as Strings (e.g., `"s_booking_id": "42"`). The DTO parses this once and exposes typed fields.

- [ ] **Step 1: Create BookingDTO**

```java
package tw.ninniku.booking.form;

import java.sql.Timestamp;
import org.json.JSONException;
import org.json.JSONObject;

public class BookingDTO {
    /** 0 = new record; > 0 = existing S_ResourceAssignment_ID */
    public final int bookingId;
    /** vis.js group ID — used as S_Resource_ID for drag-drop updates */
    public final int groupResourceId;
    /** iDempiere S_Resource_ID from form */
    public final int sResourceId;
    public final String name;
    public final String description;
    public final Timestamp startTime;
    public final Timestamp endTime;
    public final Timestamp assignFrom;
    public final Timestamp assignTo;
    public final boolean isWeekly;
    /** null when isWeekly == false */
    public final Timestamp weeklyEndDate;

    private BookingDTO(int bookingId, int groupResourceId, int sResourceId,
            String name, String description,
            Timestamp startTime, Timestamp endTime,
            Timestamp assignFrom, Timestamp assignTo,
            boolean isWeekly, Timestamp weeklyEndDate) {
        this.bookingId = bookingId;
        this.groupResourceId = groupResourceId;
        this.sResourceId = sResourceId;
        this.name = name;
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;
        this.assignFrom = assignFrom;
        this.assignTo = assignTo;
        this.isWeekly = isWeekly;
        this.weeklyEndDate = weeklyEndDate;
    }

    /**
     * Parses raw JSON string from ZK itemData textbox.
     * All JSONException are wrapped as BookingValidationException("Invalid form data").
     * Missing or non-numeric required fields throw BookingValidationException with a descriptive message.
     */
    public static BookingDTO fromJson(String raw) throws BookingValidationException {
        JSONObject json;
        try {
            json = new JSONObject(raw);
        } catch (JSONException e) {
            throw new BookingValidationException("Invalid form data: " + e.getMessage());
        }

        try {
            int bookingId = parseLenientInt(json, "s_booking_id", 0);
            int groupResourceId = parseLenientInt(json, "group", 0);
            int sResourceId = parseLenientInt(json, "s_resource_id", 0);
            String name = json.optString("booking-name", "").trim();
            String description = json.optString("description", "").trim();
            Timestamp startTime = parseTimestamp(json, "startTimestamp");
            Timestamp endTime = parseTimestamp(json, "endTimestamp");
            Timestamp assignFrom = parseTimestamp(json, "assign-date-from-timestamp");
            Timestamp assignTo = parseTimestamp(json, "assign-date-to-timestamp");
            boolean isWeekly = "Y".equals(json.optString("is-weekly", "N"));
            Timestamp weeklyEndDate = null;
            if (isWeekly) {
                weeklyEndDate = parseTimestamp(json, "repeat-date-to-timestamp");
            }
            return new BookingDTO(bookingId, groupResourceId, sResourceId,
                    name, description, startTime, endTime, assignFrom, assignTo,
                    isWeekly, weeklyEndDate);
        } catch (JSONException e) {
            throw new BookingValidationException("Invalid form data: " + e.getMessage());
        }
    }

    /** Parses a String-encoded integer field, returns defaultValue if absent/blank. */
    private static int parseLenientInt(JSONObject json, String key, int defaultValue)
            throws BookingValidationException {
        String val = json.optString(key, "").trim();
        if (val.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            throw new BookingValidationException("Invalid value for '" + key + "': " + val);
        }
    }

    /** Parses a String-encoded epoch-millisecond timestamp. Throws if absent or malformed. */
    private static Timestamp parseTimestamp(JSONObject json, String key)
            throws BookingValidationException {
        String val = json.optString(key, "").trim();
        if (val.isEmpty()) {
            throw new BookingValidationException("Missing required field: " + key);
        }
        try {
            return new Timestamp(Long.parseLong(val));
        } catch (NumberFormatException e) {
            throw new BookingValidationException("Invalid timestamp for '" + key + "': " + val);
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd /Users/ray/sources/tw.ninniku.booking
mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/tw/ninniku/booking/form/BookingDTO.java
git commit -m "Add BookingDTO: typed DTO replacing raw JSONObject parsing"
```

---

## Task 3: BookingValidator

**Files:**
- Create: `src/tw/ninniku/booking/form/BookingValidator.java`

- [ ] **Step 1: Create BookingValidator**

```java
package tw.ninniku.booking.form;

public class BookingValidator {

    /**
     * Validates business rules on a parsed DTO.
     * Throws BookingValidationException with a user-facing message on failure.
     */
    public static void validate(BookingDTO dto) throws BookingValidationException {
        if (dto.name == null || dto.name.isBlank()) {
            throw new BookingValidationException("Subject (Name) is required.");
        }
        if (!dto.startTime.before(dto.endTime)) {
            throw new BookingValidationException("Start time must be before end time.");
        }
        if (dto.isWeekly) {
            if (dto.weeklyEndDate == null) {
                throw new BookingValidationException("Weekly repeat end date is required.");
            }
            if (!dto.weeklyEndDate.after(dto.endTime)) {
                throw new BookingValidationException("Weekly repeat end date must be after the booking end time.");
            }
        }
    }

    /**
     * Escapes a string for safe embedding inside a JavaScript single-quoted string literal.
     * Escape order: backslash first (to avoid double-escaping), then ', ", \n.
     * Carriage returns (\r) are stripped rather than escaped — they are invisible
     * in JS strings and serve no purpose; stripping avoids leaving bare \r in output.
     * Returns empty string for null input.
     */
    public static String escapeForJs(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");  // strip CR; not escaped, intentionally removed
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd /Users/ray/sources/tw.ninniku.booking
mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/tw/ninniku/booking/form/BookingValidator.java
git commit -m "Add BookingValidator: business rules and escapeForJs utility"
```

---

## Task 4: BookingService

**Files:**
- Create: `src/tw/ninniku/booking/form/BookingService.java`

**Context:** This class extracts all CRUD logic from `BookingTimeline.java`. It works with `MResourceAssignment` directly (the controller currently does not use `MBooking` for save/delete operations). Transaction lifecycle is owned here.

**Intentional spec deviations (documented):**

1. **Method signatures use `boolean isAdmin, int currentUserId` instead of `MUser user`.** The spec declares `saveBooking(BookingDTO, MUser)` etc., but the permission check requires `isWritable()` (a role-based DB query in the controller) which is not a property of `MUser`. Passing `isAdmin` + `currentUserId` directly is more correct and avoids coupling the service to ZK session state. The controller calls `isWritable()` and `Env.getContextAsInt(ctx, "#AD_User_ID")` and passes the results.

2. **Load methods are named `fetchBookings`/`fetchGroups` and return `List<MResourceAssignment>`/`List<Group>`, not `loadBookingsForResource`/`loadGroups` returning `List<MBooking>`.** The spec incorrectly references `MBooking` — the controller has never used `MBooking` for timeline operations; it uses `MResourceAssignment`. `fetchBookings`/`fetchGroups` better reflects their read-only nature.

3. **`updateBookingTime` uses auto-commit (no `Trx` wrapper).** This method only updates a single existing record's time fields — no weekly recurrence, no cascade, no multi-record write. Auto-commit is appropriate and consistent with the current code's `updateBooking(int, int, Timestamp, Timestamp)` which also calls `booking.save()` without a transaction.

4. **`saveBooking` also throws `BookingValidationException`** (for permission-denied on existing bookings), in addition to `AdempiereException`. The spec's exception table only lists `AdempiereException`. The controller in Task 5 catches both.

5. **`BookingApp.Timeline` exports `setGroups`, `setItems`, `getGroups`, `clickNew`, `openEditDialog`** beyond the spec's `initChart` and `drawChart`. `setGroups`/`setItems`/`getGroups` are required by the Java `Clients.evalJavaScript` calls updated in Task 10. `clickNew`/`openEditDialog` are re-exported for `BookingApp.WeekView` delegation.

- [ ] **Step 1: Create BookingService**

```java
package tw.ninniku.booking.form;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Properties;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Trx;

import tw.ninniku.booking.model.MResourceAssignment;
import tw.ninniku.timeline.Group;

public class BookingService {

    private final Properties ctx;

    public BookingService(Properties ctx) {
        this.ctx = ctx;
    }

    /**
     * Creates or updates a booking. For weekly bookings, creates one record per week
     * until dto.weeklyEndDate (all-or-nothing: any failure rolls back all iterations).
     *
     * @param dto       validated, parsed DTO
     * @param isAdmin   result of BookingTimeline.isWritable() — true if role has write access
     * @param currentUserId  Env.getContextAsInt(ctx, "#AD_User_ID")
     * @return S_ResourceAssignment_ID of the saved (or first-created) record
     * @throws BookingValidationException if the user lacks permission to edit an existing booking
     * @throws AdempiereException on DB failure (transaction rolled back before throwing)
     */
    public int saveBooking(BookingDTO dto, boolean isAdmin, int currentUserId)
            throws BookingValidationException, AdempiereException {

        // Permission check for existing bookings
        if (dto.bookingId > 0) {
            MResourceAssignment existing = new MResourceAssignment(ctx, dto.bookingId, null);
            if (!isAdmin && existing.getCreatedBy() != currentUserId) {
                throw new BookingValidationException(
                        "Permission denied: only the creator or admin can update this booking.");
            }
        }

        Trx trx = Trx.get(Trx.createTrxName(), true);
        int savedId = 0;
        try {
            trx.start();

            MResourceAssignment booking = new MResourceAssignment(ctx, dto.bookingId, trx.getTrxName());
            booking.setName(dto.name);
            booking.setDescription(dto.description);
            booking.setS_Resource_ID(dto.sResourceId);
            booking.setAssignDateFrom(dto.assignFrom);
            booking.setAssignDateTo(dto.assignTo);
            if (booking.getAD_Org_ID() == 0) {
                booking.setAD_Org_ID(Env.getAD_Org_ID(ctx));
            }

            if (!booking.save(trx.getTrxName())) {
                String msg = "時間重疊:" + booking.getAssignDateFrom() + " - " + booking.getAssignDateTo();
                throw new AdempiereException(msg);
            }
            savedId = booking.getS_ResourceAssignment_ID();

            // Weekly recurrence — only for new bookings
            if (dto.bookingId == 0 && dto.isWeekly && dto.weeklyEndDate != null) {
                Calendar calFrom = Calendar.getInstance();
                Calendar calTo = Calendar.getInstance();
                Calendar calEnd = Calendar.getInstance();
                calFrom.setTime(dto.assignFrom);
                calFrom.add(Calendar.DAY_OF_MONTH, 7);
                calTo.setTime(dto.assignTo);
                calTo.add(Calendar.DAY_OF_MONTH, 7);
                calEnd.setTime(dto.weeklyEndDate);

                while (calFrom.before(calEnd)) {
                    MResourceAssignment weekly = new MResourceAssignment(ctx, 0, trx.getTrxName());
                    weekly.setName(dto.name);
                    weekly.setDescription(dto.description);
                    weekly.setS_Resource_ID(dto.sResourceId);
                    weekly.setAssignDateFrom(new Timestamp(calFrom.getTimeInMillis()));
                    weekly.setAssignDateTo(new Timestamp(calTo.getTimeInMillis()));
                    if (weekly.getAD_Org_ID() == 0) {
                        weekly.setAD_Org_ID(Env.getAD_Org_ID(ctx));
                    }
                    if (!weekly.save(trx.getTrxName())) {
                        String msg = "時間重疊:" + weekly.getAssignDateFrom() + " - " + weekly.getAssignDateTo();
                        throw new AdempiereException(msg);
                    }
                    calFrom.add(Calendar.DAY_OF_MONTH, 7);
                    calTo.add(Calendar.DAY_OF_MONTH, 7);
                }
            }

            trx.commit();
            return savedId;
        } catch (AdempiereException e) {
            trx.rollback();
            throw e;
        } catch (Exception e) {
            trx.rollback();
            throw new AdempiereException("Error saving booking: " + e.getMessage(), e);
        } finally {
            trx.close();
        }
    }

    /**
     * Updates only the time and resource of an existing booking (used by drag-drop / resize).
     *
     * @throws BookingValidationException if user lacks permission
     * @throws AdempiereException on DB failure
     */
    public void updateBookingTime(int bookingId, int resourceId, Timestamp start, Timestamp end,
            boolean isAdmin, int currentUserId)
            throws BookingValidationException, AdempiereException {
        if (bookingId <= 0) return;

        MResourceAssignment booking = new MResourceAssignment(ctx, bookingId, null);
        if (!isAdmin && booking.getCreatedBy() != currentUserId) {
            throw new BookingValidationException(
                    "Permission denied: only the creator or admin can update this booking.");
        }
        booking.setAssignDateFrom(start);
        booking.setAssignDateTo(end);
        booking.setS_Resource_ID(resourceId);
        if (!booking.save()) {
            throw new AdempiereException("Time overlap, update failed.");
        }
    }

    /**
     * Deletes a booking (and its MResourceAssignment record) if the user has permission.
     *
     * @throws BookingValidationException if user lacks permission
     * @throws AdempiereException on DB failure
     */
    public void deleteBooking(int bookingId, boolean isAdmin, int currentUserId)
            throws BookingValidationException, AdempiereException {
        if (bookingId <= 0) return;
        MResourceAssignment booking = new MResourceAssignment(ctx, bookingId, null);
        if (!isAdmin && booking.getCreatedBy() != currentUserId) {
            throw new BookingValidationException(
                    "Permission denied: only the creator or admin can delete this booking.");
        }
        booking.delete(true);
    }

    /**
     * Loads all active bookings where AssignDateFrom is within [start, end]
     * for the given resource type.
     * Returns empty list if none found.
     *
     * @throws AdempiereException on DB failure
     */
    public List<MResourceAssignment> fetchBookings(int resourceTypeId, Timestamp start, Timestamp end) {
        String whereSql = " AssignDateFrom >= ? AND AssignDateFrom <= ? "
                + "and exists (select 1 from S_Resource where S_Resource_ID = "
                + "S_ResourceAssignment.S_Resource_ID and S_ResourceType_ID = ?) ";
        return new org.compiere.model.Query(ctx, MResourceAssignment.Table_Name, whereSql, null)
                .setParameters(new Object[]{start, end, resourceTypeId})
                .setOrderBy("S_Resource_ID")
                .setOnlyActiveRecords(true)
                .list();
    }

    /**
     * Loads timeline groups (resources) for the given resource type.
     * Returns empty list if none found.
     * Throws AdempiereException (unchecked) on DB failure.
     */
    public List<Group> fetchGroups(int resourceTypeId) {
        List<Group> list = new ArrayList<>();
        String sql = "select * from s_resource where s_resourcetype_id = ?";
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = DB.prepareStatement(sql, null);
            pstmt.setInt(1, resourceTypeId);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                list.add(new Group(rs.getInt("s_resource_id"), rs.getString("name")));
            }
        } catch (SQLException ex) {
            throw new AdempiereException("Unable to load resources", ex);
        } finally {
            DB.close(rs, pstmt);
        }
        return list;
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd /Users/ray/sources/tw.ninniku.booking
mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/tw/ninniku/booking/form/BookingService.java
git commit -m "Add BookingService: extract CRUD business logic from controller"
```

---

## Task 5: Thin BookingTimeline Controller

**Files:**
- Modify: `src/tw/ninniku/booking/form/BookingTimeline.java`

**Context:** Replace the three heavy methods (`updateBooking(JSONObject)`, `updateBooking(int,int,Timestamp,Timestamp)`, `deleteBooking(JSONObject)`) with service calls. Update `getResourceJSON()` and `fetchBookings()` to use BookingService. Fix the XSS in `renderEventCards()` using `BookingValidator.escapeForJs()`. Update JS eval strings from bare `groups=` / `items=` to namespace-aware calls.

- [ ] **Step 1: Add BookingService field and instantiate in initForm()**

In `BookingTimeline.java`, add the field after existing fields (around line 83):
```java
private BookingService bookingService;
```

In `initForm()` (around line 387), add after `currentViewDate = ...`:
```java
bookingService = new BookingService(Env.getCtx());
```

- [ ] **Step 2: Replace the dateLast event handler (line 136–148)**

Find:
```java
} else if (event.getTarget().getId().equals("dateLast")) {

    JSONObject json = new JSONObject(itemData.getValue());
    Timestamp ds = new Timestamp(Long.valueOf((String) json.get("startTimestamp")));
    Timestamp de = new Timestamp(Long.valueOf((String) json.get("endTimestamp")));

    int S_Booking_ID = Integer.valueOf((String) json.get("s_booking_id"));
    int s_recource_id = Integer.valueOf((String) json.get("group"));
    if (!updateBooking(S_Booking_ID, s_recource_id, ds, de)) {
        Clients.showNotification("Time overlap, update failed.");
    }
    refreshView();
```

Replace with:
```java
} else if (event.getTarget().getId().equals("dateLast")) {
    try {
        JSONObject json = new JSONObject(itemData.getValue());
        int bookingId = Integer.parseInt(json.optString("s_booking_id", "0"));
        int resourceId = Integer.parseInt(json.optString("group", "0"));
        Timestamp ds = new Timestamp(Long.parseLong(json.optString("startTimestamp", "0")));
        Timestamp de = new Timestamp(Long.parseLong(json.optString("endTimestamp", "0")));
        bookingService.updateBookingTime(bookingId, resourceId, ds, de,
                isWritable(), Env.getContextAsInt(Env.getCtx(), "#AD_User_ID"));
    } catch (BookingValidationException e) {
        Clients.showNotification(e.getMessage());
    } catch (AdempiereException e) {
        Clients.showNotification(e.getMessage());
    } catch (Exception e) {
        Clients.showNotification("Update failed: " + e.getMessage());
    }
    refreshView();
```

- [ ] **Step 3: Replace the bookingUpdated event handler (line 149–154)**

Find:
```java
} else if (event.getTarget().getId().equals("bookingUpdated")) {
    JSONObject json = new JSONObject(itemData.getValue());
    if (!updateBooking(json)) {
        Clients.showNotification(errorMessage);
    }
    refreshView();
```

Replace with:
```java
} else if (event.getTarget().getId().equals("bookingUpdated")) {
    try {
        BookingDTO dto = BookingDTO.fromJson(itemData.getValue());
        BookingValidator.validate(dto);
        bookingService.saveBooking(dto, isWritable(), Env.getContextAsInt(Env.getCtx(), "#AD_User_ID"));
    } catch (BookingValidationException e) {
        Clients.showNotification(e.getMessage());
    } catch (AdempiereException e) {
        Clients.showNotification(e.getMessage());
    }
    refreshView();
```

- [ ] **Step 4: Replace the bookingDeleted event handler (line 155–162)**

Find:
```java
} else if (event.getTarget().getId().equals("bookingDeleted")) {
    JSONObject json = new JSONObject(itemData.getValue());

    if (!deleteBooking(json)) {
        Clients.showNotification(errorMessage);
    }
    refreshView();
```

Replace with:
```java
} else if (event.getTarget().getId().equals("bookingDeleted")) {
    try {
        JSONObject json = new JSONObject(itemData.getValue());
        int bookingId = Integer.parseInt(json.optString("id", "0"));
        bookingService.deleteBooking(bookingId, isWritable(),
                Env.getContextAsInt(Env.getCtx(), "#AD_User_ID"));
    } catch (BookingValidationException e) {
        Clients.showNotification(e.getMessage());
    } catch (AdempiereException e) {
        Clients.showNotification(e.getMessage());
    }
    refreshView();
```

- [ ] **Step 5: Replace getResourceJSON() to use BookingService**

Find `getResourceJSON()` (lines 566–594) and replace with:
```java
private String getResourceJSON() {
    Listitem item = resourceType.getSelectedItem();
    if (item == null) return new com.google.gson.Gson().toJson(new ArrayList<Group>());
    int resourceTypeId = Integer.parseInt((String) item.getId());
    groups = (ArrayList<Group>) bookingService.fetchGroups(resourceTypeId);
    return new com.google.gson.Gson().toJson(groups);
}
```

- [ ] **Step 6: Replace fetchBookings() to use BookingService**

Find `fetchBookings()` (lines 547–555) and replace with:
```java
private List<MResourceAssignment> fetchBookings(Timestamp start, Timestamp end) {
    Listitem item = resourceType.getSelectedItem();
    if (item == null) return new ArrayList<>();
    int resourceTypeId = Integer.parseInt((String) item.getId());
    return bookingService.fetchBookings(resourceTypeId, start, end);
}
```

- [ ] **Step 7: Fix XSS in renderEventCards() using BookingValidator.escapeForJs()**

Find (lines 838–843):
```java
String nameJS = b.getName().replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"");
String descJS = "";
if (b.getDescription() != null) {
    descJS = b.getDescription().replace("\r", "").replace("\n", " ").replace("\\", "\\\\")
            .replace("'", "\\'").replace("\"", "\\\"");
}
```

Replace with:
```java
String nameJS = BookingValidator.escapeForJs(b.getName());
String descJS = BookingValidator.escapeForJs(b.getDescription());
```

- [ ] **Step 8: Remove the now-dead methods**

Delete these three methods entirely from BookingTimeline.java:
- `private boolean deleteBooking(JSONObject json)` (lines 228–245)
- `private boolean updateBooking(JSONObject json)` (lines 251–365)
- `private boolean updateBooking(int, int, Timestamp, Timestamp)` (lines 367–383)

Also remove the `errorMessage` field (line 83) and the `isInteger` static helper (lines 247–249) if they are no longer referenced.

- [ ] **Step 9: Remove unused imports**

Remove from the import block any imports that are now unused after the deletions:
- `import org.json.JSONObject;` — still used in dateLast/delete handlers; keep it
- Verify no other dead imports remain (check `import java.util.Properties;` is still needed for `isWritable()`)

- [ ] **Step 10: Verify compilation**

```bash
cd /Users/ray/sources/tw.ninniku.booking
mvn compile -q
```
Expected: BUILD SUCCESS. If compilation errors appear, fix them before continuing.

- [ ] **Step 11: Commit**

```bash
git add src/tw/ninniku/booking/form/BookingTimeline.java
git commit -m "Thin BookingTimeline: delegate CRUD to BookingService, fix XSS in renderEventCards"
```

---

## Task 6: Race Condition Comments in Model Classes

**Files:**
- Modify: `src/tw/ninniku/booking/model/MResourceAssignment.java`
- Modify: `src/tw/ninniku/booking/model/MBooking.java`

- [ ] **Step 1: Add comment to MResourceAssignment.isOverlap()**

Open `MResourceAssignment.java`. Find the `isOverlap()` method and add the comment immediately before the `return` statement:

```java
// KNOWN LIMITATION: This check-then-act pattern is not atomic at the database level.
// Two concurrent saves can both pass isOverlap() and both insert, causing a double-booking.
// Fixing this requires a DB-level unique constraint or advisory lock (out of scope).
return DB.getSQLValue(...) > 0;
```

- [ ] **Step 2: Add comment to MBooking.isOverlap()**

Same comment in `MBooking.java` at the same location.

- [ ] **Step 3: Verify compilation**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/tw/ninniku/booking/model/MResourceAssignment.java \
        src/tw/ninniku/booking/model/MBooking.java
git commit -m "Document non-atomic overlap check race condition in model classes"
```

---

## Task 7: Fix pom.xml Java Version

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Change Java source/target from 11 to 17**

In `pom.xml`, find:
```xml
<source>11</source>
<target>11</target>
```

Replace with:
```xml
<source>17</source>
<target>17</target>
```

- [ ] **Step 2: Verify compilation**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "Fix pom.xml: align Java source/target to 17 matching MANIFEST.MF"
```

---

## Task 8: Refactor booking_weekview.js → BookingApp.WeekView

**Files:**
- Modify: `WEB-INF/web/js/booking_weekview.js`

**Context:** This file currently defines global `window.*` functions as a fallback. It will be rewritten to use an IIFE namespace. In a later task, all inline ZUL script functions will be absorbed here. The key cross-module dependency is `window.groups` (the vis.js groups array) — after namespacing, this becomes `BookingApp.Timeline.getGroups()` (added in Task 9).

**Important:** Do Task 8 before Task 9 so that when Task 9 removes week-view globals from booking.js, WeekView already defines them correctly.

- [ ] **Step 1: Rewrite booking_weekview.js with IIFE namespace**

Replace the entire file content with:

```javascript
// booking_weekview.js
// BookingApp.WeekView — owns all week/day-view interaction symbols.
// Authoritative owner of openCustomAddDialog, openEditDialog, onWeekEventClick, etc.
// The ZUL inline script block functions are absorbed into this module in a later step.

var BookingApp = BookingApp || {};
BookingApp.WeekView = (function () {

    // ---------------------------------------------------------------------------
    // Private helpers (previously local in ZUL inline script or booking.js IIFE)
    // ---------------------------------------------------------------------------

    function getGroups() {
        // After Task 9, BookingApp.Timeline exposes getGroups(). Until then, fall back to window.groups.
        if (window.BookingApp && window.BookingApp.Timeline && typeof window.BookingApp.Timeline.getGroups === 'function') {
            return window.BookingApp.Timeline.getGroups();
        }
        return window.groups || [];
    }

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    function openCustomAddDialog(dateStr, minutesOffset, resourceId) {
        try {
            var groups = getGroups();
            if (!resourceId) {
                resourceId = groups.length > 0 ? groups[0].id : 0;
            }
            var date = new Date(dateStr);
            date.setMinutes(date.getMinutes() + minutesOffset);
            var endDate = new Date(date);
            endDate.setHours(endDate.getHours() + 1);
            var item = { start: date, end: endDate, group: resourceId, content: '' };
            if (typeof BookingApp.WeekView.clickNew === 'function') {
                BookingApp.WeekView.clickNew(item, function () { });
            } else {
                console.error("BookingApp.WeekView.clickNew not defined");
            }
        } catch (e) {
            console.error("Error in openCustomAddDialog:", e);
        }
    }

    function openCustomEditDialog(id, name, desc, resourceId, startMs, endMs) {
        var item = {
            id: id, s_booking_id: id, name: name, content: name,
            description: desc, group: resourceId,
            start: new Date(startMs), end: new Date(endMs)
        };
        if (typeof BookingApp.WeekView.openEditDialog === 'function') {
            BookingApp.WeekView.openEditDialog(item, function () { });
        }
    }

    function openCustomAddDialogRange(startMs, endMs, resourceId) {
        var groups = getGroups();
        if (!resourceId) {
            resourceId = groups.length > 0 ? groups[0].id : 0;
        }
        var item = { start: new Date(startMs), end: new Date(endMs), group: resourceId, content: '' };
        if (typeof BookingApp.WeekView.clickNew === 'function') {
            BookingApp.WeekView.clickNew(item, function () { });
        } else {
            console.error("BookingApp.WeekView.clickNew not defined");
        }
    }

    var _wasDragging = false;
    function onWeekDayClick(event, elem, dayKey, resourceId) {
        if (_wasDragging) { _wasDragging = false; return; }
        var rect = elem.getBoundingClientRect();
        var min = (event.clientY - rect.top) / 40 * 60;
        openCustomAddDialog(dayKey, Math.floor(min / 30) * 30, resourceId);
    }

    function onWeekEventClick(event, id, name, desc, resId, startMs, endMs) {
        event.stopPropagation();
        openCustomEditDialog(id, name, desc, resId, startMs, endMs);
    }

    function onWeekEventDelete(event, id) {
        event.stopPropagation();
        if (typeof zAu !== 'undefined') {
            // Trigger ZK delete event via bookingDeleted hidden textbox
            // (same mechanism as booking.js openEditDialog delete button)
            var payload = JSON.stringify({ id: String(id) });
            var tb = document.querySelector('[id$="bookingDeleted"]');
            if (tb) {
                tb.value = payload;
                zAu.send(new zk.Event(zk.Widget.$(tb), 'onChange', { value: payload }, { toServer: true }));
            }
        }
    }

    function weekViewScrollTo8Am() {
        var scrollBody = document.querySelector('.scroll-body');
        if (scrollBody) scrollBody.scrollTop = 8 * 40;
    }

    function updateTimeIndicator() {
        var lines = document.querySelectorAll('.current-time-line');
        if (!lines.length) return;
        var now = new Date();
        var rootEl = document.querySelector('.week-view-root');
        var startHour = rootEl ? parseInt(rootEl.getAttribute('data-start-hour') || '0') : 0;
        var top = ((now.getHours() - startHour) * 60 + now.getMinutes()) * (40 / 60);
        lines.forEach(function (line) { line.style.top = top + 'px'; });
    }

    // clickNew and openEditDialog are defined in booking.js (BookingApp.Timeline)
    // and re-exported here so ZUL only needs one namespace.
    function clickNew(item, callback) {
        if (window.BookingApp && window.BookingApp.Timeline) {
            BookingApp.Timeline.clickNew(item, callback);
        }
    }
    function openEditDialog(item, callback) {
        if (window.BookingApp && window.BookingApp.Timeline) {
            BookingApp.Timeline.openEditDialog(item, callback);
        }
    }

    return {
        openCustomAddDialog:      openCustomAddDialog,
        openCustomEditDialog:     openCustomEditDialog,
        openCustomAddDialogRange: openCustomAddDialogRange,
        onWeekDayClick:           onWeekDayClick,
        onWeekEventClick:         onWeekEventClick,
        onWeekEventDelete:        onWeekEventDelete,
        weekViewScrollTo8Am:      weekViewScrollTo8Am,
        updateTimeIndicator:      updateTimeIndicator,
        clickNew:                 clickNew,
        openEditDialog:           openEditDialog
    };

})();
```

**Note on Task 8 / Task 9 ordering and self-references:**
`BookingApp.WeekView` delegates `clickNew` and `openEditDialog` to `BookingApp.Timeline`. At the time Task 8 is committed, `BookingApp.Timeline` does not yet exist (it is created in Task 9). This is safe because the delegation code runs at *call time*, not at IIFE definition time — by the time a user clicks anything, Task 9 will be complete and `BookingApp.Timeline` will exist. The `getGroups()` call similarly falls back to `window.groups` until Task 9 is done. This is a deliberate temporary state, not a bug.

- [ ] **Step 2: Commit**

```bash
git add WEB-INF/web/js/booking_weekview.js
git commit -m "Refactor booking_weekview.js to BookingApp.WeekView IIFE namespace"
```

---

## Task 9: Refactor booking.js → BookingApp.Timeline

**Files:**
- Modify: `WEB-INF/web/js/booking.js`

**Context:** Wrap all existing code in an IIFE. Remove week-view symbol definitions that are now owned by `BookingApp.WeekView`. Expose `getGroups()` and `setGroups()` / `setItems()` as public API so the Java controller can update state via `BookingApp.Timeline.setGroups(...)` instead of bare `groups = ...`.

- [ ] **Step 1: Wrap entire booking.js in BookingApp.Timeline IIFE**

At the very top of the file, add:
```javascript
var BookingApp = BookingApp || {};
BookingApp.Timeline = (function () {
```

At the very bottom of the file, before the closing `})();`, add the return statement:
```javascript
    return {
        initChart:    initChart,
        drawChart:    drawChart,
        setGroups:    function(g) { groups = g; },
        setItems:     function(data) { items = new vis.DataSet(data); if (timeline) timeline.setItems(items); },
        getGroups:    function() { return groups; },
        clickNew:     clickNew,
        openEditDialog: openEditDialog
    };

})();
```

- [ ] **Step 2: Remove week-view global assignments from booking.js**

Find and delete the entire "Week View Logic" block (approximately lines 344–427). It starts with:
```javascript
/* Week View Logic - Merged from booking_weekview.js for reliability */
console.log('DEBUG: Start of Merged Week View Logic');

window.openCustomAddDialog = function (...
```
...and ends after `window.onWeekEventDelete = function (...) { ... };` (closing `};` at approximately line 427).

Also delete the `_weekViewWasDragging` variable declaration just before `window.onWeekDayClick`.

Inside the Drag and Drop IIFE (starts around line 429), update the one remaining `window.openCustomAddDialogRange` call at approximately line 576:
```javascript
// Old:
if (window.openCustomAddDialogRange) {
    window.openCustomAddDialogRange(start.getTime(), end.getTime(), dragStartData.resId);
}
// New:
if (BookingApp.WeekView && BookingApp.WeekView.openCustomAddDialogRange) {
    BookingApp.WeekView.openCustomAddDialogRange(start.getTime(), end.getTime(), dragStartData.resId);
}
```

- [ ] **Step 3: Commit**

```bash
git add WEB-INF/web/js/booking.js
git commit -m "Refactor booking.js to BookingApp.Timeline IIFE namespace"
```

---

## Task 10: Update ZUL Call Sites + Absorb Inline Script

**Files:**
- Modify: `WEB-INF/web/meetingroom.zul`
- Modify: `WEB-INF/web/js/booking_weekview.js` (add absorbed functions)
- Modify: `src/tw/ninniku/booking/form/BookingTimeline.java` (update JS eval strings)

This is the most involved task. Read the current `meetingroom.zul` carefully before making changes.

- [ ] **Step 1: Update Java JS eval strings to use BookingApp.Timeline namespace**

In `BookingTimeline.java`, find all `Clients.evalJavaScript(...)` calls and update:

| Old JS string | New JS string |
|---|---|
| `"groups= " + groupJson + ";"` | `"BookingApp.Timeline.setGroups(" + groupJson + ");"` |
| `"items = new vis.DataSet(" + itemJson + ");"` | `"BookingApp.Timeline.setItems(" + itemJson + ");"` |
| `"setTimeout(function(){ initChart(); }, 200);"` | `"setTimeout(function(){ BookingApp.Timeline.initChart(); }, 200);"` |
| `"setTimeout(function(){" + "initChart();" + " }, 2000)"` | `"setTimeout(function(){ BookingApp.Timeline.initChart(); }, 2000)"` |
| The `btnAddBooking` handler that calls `window.openCustomAddDialogRange` | Update to call `BookingApp.WeekView.openCustomAddDialogRange(...)` |

- [ ] **Step 2: Copy the private helper functions from ZUL to booking_weekview.js**

Read the inline `<script><![CDATA[...]]></script>` block in `meetingroom.zul`. Find all private helper functions (NOT window.* exports):
- `toTimestamp(strDate)`
- `convertFormToJSON(form)`
- `updateMeetingRoomSelector()`
- `showBeforeDate()`
- `validateBookingForm()`
- `initDragEvents()` (and all its internal drag/mousemove/mouseup logic)
- `getTimeFromY(y, dayKey)`
- `snapTo30(d)`
- `formatTime(date)`
- `formatDuration(diffMs)`
- `getResourceColor(id)`
- `updateTooltip(start, end, e)`
- `triggerUpdate(s_booking_id, group, start, end)`
- `doScroll()`

Add them as private functions inside `BookingApp.WeekView`'s IIFE (before the `return` block). They stay private — do not export them.

Also move the `$(document).ready(...)` or initialization call at the bottom of the ZUL script into the IIFE's auto-initialization (call `initDragEvents()` and `doScroll()` at the end of the IIFE body, before the `return`).

- [ ] **Step 3: Verify the absorbed functions before deleting**

Before deleting the ZUL inline block, reload in the browser and confirm the week view and day view still work with the new `BookingApp.WeekView` functions. Check the browser console for errors. Only proceed to Step 4 once confirmed working.

- [ ] **Step 4: Remove the inline script block from meetingroom.zul**

Delete the entire `<script><![CDATA[...]]></script>` block from `meetingroom.zul`.

- [ ] **Step 5: Update ZUL call sites**

Search for any remaining `onclick`, `ondrop`, `ondragover`, or `window.*` references in `meetingroom.zul` and update them:

| Old | New |
|---|---|
| `window.onWeekEventClick(...)` | `BookingApp.WeekView.onWeekEventClick(...)` |
| `window.onWeekEventDelete(...)` | `BookingApp.WeekView.onWeekEventDelete(...)` |
| `window.weekViewScrollTo8Am()` | `BookingApp.WeekView.weekViewScrollTo8Am()` |
| Any other `window.*` call from the list in the spec | `BookingApp.WeekView.*` |

Note: The Java-generated HTML in `renderEventCards()` (the `onclick` attribute on `.event-card` divs) also references `onWeekEventClick` and `onWeekEventDelete`. These are generated in `BookingTimeline.java` — update those string literals in `renderEventCards()` to use `BookingApp.WeekView.*` as well.

Specifically, in `renderEventCards()` (around line 833–835):
```java
// Old:
"<span class='delete-icon' onclick='window.onWeekEventDelete(event, %d)'>&times;</span>"
// New:
"<span class='delete-icon' onclick='BookingApp.WeekView.onWeekEventDelete(event, %d)'>&times;</span>"
```

And in the `onclick` on the event-card div (around line 851):
```java
// Old:
"onclick=\"onWeekEventClick(event, '%s', ...)\""
// New:
"onclick=\"BookingApp.WeekView.onWeekEventClick(event, '%s', ...)\""
```

- [ ] **Step 6: Verify script load order in meetingroom.zul**

`BookingApp.WeekView` delegates `clickNew` and `openEditDialog` to `BookingApp.Timeline`, so `booking.js` must be loaded before `booking_weekview.js`. In `meetingroom.zul`, confirm the `<script src="...">` tags appear in this order:

```xml
<script src="~./js/booking.js" />
<script src="~./js/booking_weekview.js" />
```

If the order is reversed, swap the two lines.

- [ ] **Step 7: Verify compilation**

```bash
cd /Users/ray/sources/tw.ninniku.booking
mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 8: Manual smoke test**

Deploy to iDempiere and verify:
1. Timeline view loads and shows bookings
2. Week view loads, events are visible and clickable
3. Day view loads, events are visible and clickable
4. Create a new booking via the dialog — verify it saves
5. Drag an event to a new time — verify it saves
6. Delete a booking — verify it deletes
7. Try creating a booking with an empty name — verify error message appears
8. Open browser devtools console — verify no JS errors

- [ ] **Step 9: Commit**

```bash
git add WEB-INF/web/meetingroom.zul \
        WEB-INF/web/js/booking_weekview.js \
        src/tw/ninniku/booking/form/BookingTimeline.java
git commit -m "Absorb ZUL inline script into BookingApp.WeekView; update all JS call sites to namespaced API"
```

---

## Summary of Bug Fixes

| Bug | Fixed in |
|---|---|
| XSS in renderEventCards string concatenation | Task 5 (Step 7) |
| Missing try-catch for JSON parsing | Task 5 (Steps 2–4) |
| Unsafe type casting on json.get() | Task 2 (BookingDTO.parseLenientInt/parseTimestamp) |
| Global JS variables collide across users | Tasks 8–10 |
| ~400 lines of untestable inline ZUL script | Task 10 |
| Race condition — documented (not fixed) | Task 6 |
| pom.xml Java 11 vs MANIFEST.MF Java 17 | Task 7 |
