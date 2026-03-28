# MVVM Refactoring Design: Full ZK MVVM + Resource Layout Realignment

**Date:** 2026-03-28
**Replaces:** N/A (new spec — extends previous structural refactor)
**Skill reference:** `docs/plugin-zk-mvvm-skill.md`

---

## 1. Goals

Refactor `tw.ninniku.booking` to fully comply with the ZK MVVM pattern as defined in the project's `plugin-zk-mvvm-skill.md`:

1. **Resource layout** — move all web resources from `WEB-INF/web/` to `web/` at the bundle root; align `Bundle-ClassPath` and `build.properties`
2. **MVVM pattern** — introduce `BookingVM.java` (POJO ViewModel); thin `BookingTimeline.java` into `BookingForm.java` (~100 lines)
3. **Full dialog MVVM** — replace native HTML `<n:form id="booking-form">` with ZK components bound to `BookingDraft` via `@bind`
4. **ZUL toolbar binding** — replace Java event listeners with `@command` bindings on all toolbar buttons and the resource type listbox

---

## 2. Approach

**Approach C — Full MVVM** was selected:
- Resource layout fully realigned to skill spec
- ViewModel owns all ZK-visible state including booking dialog draft fields
- Booking dialog converted from jQuery-UI native HTML form to ZK `<window mode="modal">` with `@bind`
- JS rendering stays event-driven via `Clients.evalJavaScript()` from VM `@Command` methods
- `BookingService`, `BookingDTO`, `BookingValidator`, `BookingValidationException` unchanged

---

## 3. Resource Layout Realignment

### 3.1 File Moves

```
WEB-INF/web/meetingroom.zul     → web/zul/meetingroom.zul
WEB-INF/web/js/*                → web/js/*
WEB-INF/web/styles/*            → web/styles/*
WEB-INF/web/images/*            → web/images/*
```

`WEB-INF/` directory is deleted entirely after the move.

### 3.2 MANIFEST.MF

```
Bundle-ClassPath: src/, .
```

Before: `., src/, WEB-INF/` — the `WEB-INF/` entry is removed; `.` (bundle root) gives ZK access to `web/` via `~./`.

### 3.3 build.properties

```properties
bin.includes = META-INF/,\
               .,\
               OSGI-INF/tw.ninniku.booking.model.factory.xml,\
               OSGI-INF/tw.ninniku.booking.form.factory.xml,\
               OSGI-INF/tw.ninniku.booking.process.factory.xml,\
               web/,\
               src/
source.. = src/
src.includes = src/,\
               web/
output.. = bin/
```

### 3.4 ZUL Script/Style Paths

All `<script>` and `<style>` tags in `meetingroom.zul` updated from absolute `/js/...` and `/styles/...` to ZK classpath paths:

```xml
<script src="~./js/vis-timeline-graph2d.min.js"/>
<script src="~./js/jquery-ui.js"/>
<script src="~./js/jquery.toast.js"/>
<script src="~./js/booking.js"/>
<script src="~./js/booking_weekview.js"/>
<style src="~./styles/vis-timeline-graph2d.min.css"/>
<style src="~./styles/jquery-ui.css"/>
<style src="~./styles/jquery.toast.css"/>
<style src="~./styles/booking.css"/>
```

### 3.5 Controller ZUL Path

```java
Executions.createComponents("~./zul/meetingroom.zul", this, args);
```

---

## 4. Architecture

```
BookingFormFactory  ──creates──▶  BookingForm (ADForm, ~100 lines)
                                       │
                                  initForm()
                                       │
                          createComponents("~./zul/meetingroom.zul")
                                       │
                          ┌────────────┼─────────────┐
                          ▼            ▼              ▼
                    BookingVM       ZUL Template   (no WSearchEditor)
                    (POJO VM)       (MVVM bind)
                          │
                    BookingService / BookingValidator / BookingDTO
```

---

## 5. ViewModel (`BookingVM.java`)

**Location:** `src/tw/ninniku/booking/viewmodel/BookingVM.java`
**Type:** Pure POJO — no ZK superclass

### 5.1 State Properties

```java
// Resource selector
private List<MResourceType> resourceTypes;       // @load → listbox model
private MResourceType selectedResourceType;      // @bind → listbox selectedItem

// Date range (display)
private String currentDateFrom;                  // @load → toolbar label
private String currentDateTo;                    // @load → toolbar label

// View mode
private String viewMode = "timeline";            // "timeline" | "week" | "day"

// Status
private String errorMessage;                     // @load → error label
private boolean loading;                         // @load → spinner visible (optional)

// Dialog
private boolean dialogVisible;                   // @load → window visible
private boolean isEditMode;                      // false = Add, true = Edit
private String dialogError;                      // @load → inline dialog error label
private BookingDraft draft = new BookingDraft();
```

### 5.2 BookingDraft (inner class)

A mutable container for the booking dialog fields. All fields need both getter and setter (required by `@bind`).

```java
public static class BookingDraft {
    private int bookingId;          // 0 = new record
    private int sResourceId;
    private String resourceName;    // display only (@load)
    private String name;
    private String description;
    private Date startDate;
    private Date startTime;
    private Date endDate;
    private Date endTime;
    private boolean weekly;
    private Date weeklyEndDate;     // null if !weekly
    // getters + setters for all fields
}
```

### 5.3 Commands

| Method | Annotation | Triggered by | Action |
|---|---|---|---|
| `init()` | `@Init` | ZK Binder at bind time | Load `resourceTypes` from DB; set initial date range |
| `refresh()` | `@Command` `@NotifyChange({"errorMessage","loading"})` | Refresh button | Call `BookingService.loadBookings`; `Clients.evalJavaScript()` to redraw |
| `changeViewMode(String mode)` | `@Command` `@NotifyChange("viewMode")` | Week/Day/Timeline buttons | Set `viewMode`; `Clients.evalJavaScript()` to switch JS view |
| `prevPeriod()` | `@Command` `@NotifyChange({"currentDateFrom","currentDateTo"})` | `<` button | Step date range back; call refresh |
| `nextPeriod()` | `@Command` `@NotifyChange({"currentDateFrom","currentDateTo"})` | `>` button | Step date range forward; call refresh |
| `today()` | `@Command` `@NotifyChange({"currentDateFrom","currentDateTo"})` | Today button | Reset to current week/day/period; call refresh |
| `openAddDialog()` | `@Command` `@NotifyChange({"dialogVisible","isEditMode","draft","dialogError"})` | Add Booking button | Clear draft; set `isEditMode=false`; set `dialogVisible=true` |
| `saveBooking()` | `@Command` `@NotifyChange({"dialogVisible","dialogError","errorMessage"})` | Dialog Save button | `BookingValidator.validate(draft)` → `BookingService.saveBooking(dto, ...)` → close dialog → refresh |
| `deleteBooking()` | `@Command` `@NotifyChange({"dialogVisible","errorMessage"})` | Dialog Delete button | `BookingService.deleteBooking(bookingId, ...)` → close dialog → refresh |
| `closeDialog()` | `@Command` `@NotifyChange("dialogVisible")` | Dialog Cancel / onClose | Set `dialogVisible=false` |

### 5.4 Non-command method: `prepareEdit(String jsonStr)`

Called by `BookingForm.onEvent()` when JS sends an edit event. Not a `@Command` (called from Java, not ZUL). Uses `BindUtils.postNotifyChange` to update UI:

```java
public void prepareEdit(String jsonStr) throws BookingValidationException {
    BookingDTO dto = BookingDTO.fromJson(jsonStr);
    draft.bookingId    = dto.bookingId;
    draft.sResourceId  = dto.sResourceId;
    // ... populate all draft fields from dto ...
    isEditMode    = true;
    dialogError   = null;
    dialogVisible = true;
    BindUtils.postNotifyChange(null, null, this, "draft");
    BindUtils.postNotifyChange(null, null, this, "dialogVisible");
    BindUtils.postNotifyChange(null, null, this, "isEditMode");
    BindUtils.postNotifyChange(null, null, this, "dialogError");
}
```

---

## 6. Controller (`BookingForm.java`)

**Renamed from:** `BookingTimeline.java`
**Target size:** ~100 lines

### 6.1 Responsibilities

- ClassLoader switch + ZUL load + `wireComponents`
- Bridge ZK custom events from JS (`onBookingEdit`, `onBookingDelete`) to VM

### 6.2 initForm()

```java
@Override
protected void initForm() {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    try {
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

        Map<String, Object> args = new HashMap<>();
        args.put("vm", new BookingVM());
        args.put("version", resolvePluginVersion());
        Executions.createComponents("~./zul/meetingroom.zul", this, args);
        Selectors.wireComponents(this, this, false);

    } catch (Exception e) {
        log.severe("Failed to init BookingForm: " + e.getMessage());
    } finally {
        Thread.currentThread().setContextClassLoader(cl);
    }
}
```

### 6.3 onEvent() — JS bridge

```java
@Override
public void onEvent(Event e) {
    String name = e.getName();
    BookingVM vm = getViewModel();
    if (vm == null) return;

    if ("onBookingEdit".equals(name)) {
        try {
            vm.prepareEdit((String) e.getData());
        } catch (BookingValidationException ex) {
            vm.setErrorMessage(ex.getMessage());
            BindUtils.postNotifyChange(null, null, vm, "errorMessage");
        }
    } else if ("onBookingDelete".equals(name)) {
        // handled via @command in ZUL delete button
    }
}
```

### 6.4 @Wire

```java
@Wire("#bookingVMContainer")
private Component bookingVMContainer;
```

### 6.5 getViewModel() helper

```java
private BookingVM getViewModel() {
    if (bookingVMContainer == null) return null;
    Binder binder = (Binder) bookingVMContainer.getAttribute("binder");
    if (binder == null) return null;
    Object vm = binder.getViewModel();
    return (vm instanceof BookingVM) ? (BookingVM) vm : null;
}
```

---

## 7. ZUL Template (`web/zul/meetingroom.zul`)

### 7.1 Root container

```xml
<window id="bookingVMContainer" border="none" width="100%" height="100%"
    style="background-color:green" contentStyle="overflow:auto;"
    apply="org.zkoss.bind.BindComposer"
    viewModel="@id('vm') @init(arg.vm)">
```

### 7.2 Toolbar

All toolbar buttons use `@command`. Resource type listbox uses `@load` + `@bind`:

```xml
<toolbar>
  <label value="Booking"/>
  <separator bar="true"/>
  <label value="Version ${arg.version}"/>
  <separator bar="true"/>
  <button mold="os" label="Refresh" onClick="@command('refresh')"/>
  <separator bar="true"/>
  <label value="Resource:"/>
  <listbox mold="select"
           model="@load(vm.resourceTypes)"
           selectedItem="@bind(vm.selectedResourceType)">
    <template name="model" var="rt">
      <listitem label="@load(rt.name)" value="@load(rt)"/>
    </template>
  </listbox>
  <separator bar="true"/>
  <button mold="os" label="Add Booking" onClick="@command('openAddDialog')"
          style="background-color:#1976D2;color:black;font-weight:bold;
                 border-radius:4px;box-shadow:0 2px 5px rgba(0,0,0,0.2);"/>
  <separator bar="true"/>
  <button mold="os" label="Week"     onClick="@command('changeViewMode', mode='week')"/>
  <button mold="os" label="Day"      onClick="@command('changeViewMode', mode='day')"/>
  <button mold="os" label="Timeline" onClick="@command('changeViewMode', mode='timeline')"/>
  <separator bar="true"/>
  <button mold="os" label="&lt;"  onClick="@command('prevPeriod')"/>
  <button mold="os" label="Today" onClick="@command('today')"/>
  <button mold="os" label="&gt;"  onClick="@command('nextPeriod')"/>
  <separator bar="true"/>
  <checkbox id="chkWorkHours" label="Only Work Hours" checked="true"/>
</toolbar>
```

### 7.3 Error label

```xml
<label value="@load(vm.errorMessage)" sclass="booking-error-label"
       visible="@load(not empty vm.errorMessage)"/>
```

### 7.4 Booking dialog (replaces `<n:div id="update-form">`)

```xml
<window title="@load(vm.isEditMode ? '編輯預約' : '新增預約')"
        border="normal" width="480px" mode="modal"
        visible="@load(vm.dialogVisible)"
        closable="true" onClose="@command('closeDialog')">
  <grid>
    <rows>
      <row><label value="Resource"/><label value="@load(vm.draft.resourceName)"/></row>
      <row><label value="Name *"/>
           <textbox value="@bind(vm.draft.name)" width="300px"/></row>
      <row><label value="Description"/>
           <textbox value="@bind(vm.draft.description)" width="300px" rows="2"/></row>
      <row><label value="Start"/>
           <hlayout>
             <datebox value="@bind(vm.draft.startDate)" format="yyyy-MM-dd"/>
             <timebox value="@bind(vm.draft.startTime)" format="HH:mm"/>
           </hlayout></row>
      <row><label value="End"/>
           <hlayout>
             <datebox value="@bind(vm.draft.endDate)" format="yyyy-MM-dd"/>
             <timebox value="@bind(vm.draft.endTime)" format="HH:mm"/>
           </hlayout></row>
      <row><label value="Weekly"/>
           <checkbox checked="@bind(vm.draft.weekly)"/></row>
      <row visible="@load(vm.draft.weekly)">
           <label value="Repeat Until"/>
           <datebox value="@bind(vm.draft.weeklyEndDate)" format="yyyy-MM-dd"/></row>
      <row><label value=""/>
           <label value="@load(vm.dialogError)" sclass="error-text"
                  visible="@load(not empty vm.dialogError)"/></row>
    </rows>
  </grid>
  <div sclass="booking-dialog-footer">
    <button label="Save"   onClick="@command('saveBooking')"
            style="background-color:#1976D2;color:white;"/>
    <button label="Delete" onClick="@command('deleteBooking')"
            visible="@load(vm.isEditMode)"/>
    <button label="Cancel" onClick="@command('closeDialog')"/>
  </div>
</window>
```

### 7.5 Inline script removal

All inline `<script>` or `<n:script>` blocks are removed. JS logic stays in `booking.js` / `booking_weekview.js`. JS → ZK communication uses ZK event mechanism (unchanged).

---

## 8. OSGI Registration Updates

| File | Change |
|---|---|
| `src/tw/ninniku/booking/factories/BookingFormFactory.java` | `BookingTimeline.class.getName()` → `BookingForm.class.getName()` |
| `OSGI-INF/tw.ninniku.booking.form.factory.xml` | No change needed (references factory class, not form class) |
| AD Form record in iDempiere DB | Classname field: `tw.ninniku.booking.form.BookingTimeline` → `tw.ninniku.booking.form.BookingForm` (manual update in iDempiere UI) |

---

## 9. File Summary

### New
| File | Purpose |
|---|---|
| `src/tw/ninniku/booking/viewmodel/BookingVM.java` | POJO ViewModel with BookingDraft inner class |
| `web/zul/meetingroom.zul` | Moved + MVVM-rewritten ZUL |
| `web/js/*` | Moved from `WEB-INF/web/js/` |
| `web/styles/*` | Moved from `WEB-INF/web/styles/` |
| `web/images/*` | Moved from `WEB-INF/web/images/` |

### Modified
| File | Change |
|---|---|
| `src/tw/ninniku/booking/form/BookingTimeline.java` | Renamed to `BookingForm.java`; thinned to ~100 lines |
| `src/tw/ninniku/booking/factories/BookingFormFactory.java` | Class reference update |
| `META-INF/MANIFEST.MF` | `Bundle-ClassPath: src/, .` |
| `build.properties` | `web/` replaces `WEB-INF/` entries |

### Deleted
| File | Reason |
|---|---|
| `WEB-INF/` (entire directory) | Resources moved to `web/` |

### Unchanged
| File | Reason |
|---|---|
| `BookingDTO.java` | Used as-is by VM |
| `BookingService.java` | Called by VM `@Command` methods |
| `BookingValidator.java` | Called by VM |
| `BookingValidationException.java` | Unchanged |
| `MBooking.java`, `MResourceAssignment.java` | Model layer unchanged |
| `booking.js`, `booking_weekview.js` | JS logic unchanged; only `<script src>` path updated in ZUL |

---

## 10. Out of Scope

- Attendee management in booking dialog
- Unit tests / test infrastructure
- Permission model changes
- Audit trail / booking history
- UI visual redesign
