# MVVM Refactoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor `tw.ninniku.booking` to full ZK MVVM pattern: move resources to `web/`, introduce `BookingVM.java`, thin `BookingForm.java` controller, replace native HTML booking dialog with ZK modal bound to `BookingDraft`.

**Architecture:** `BookingFormFactory` creates `BookingForm` (thin ADForm, ~120 lines) which loads `web/zul/meetingroom.zul` via ClassLoader switch. The ZUL binds to `BookingVM` (POJO) via `viewModel="@id('vm') @init(arg.vm)"`. All toolbar buttons use `@command`; the booking dialog is a ZK `<window mode="modal">` bound to `BookingDraft` inner class. JS rendering (timeline/week/day) stays via `Clients.evalJavaScript()` from VM methods.

**Tech Stack:** iDempiere 12, ZK 9.6, ZK MVVM (zkbind), Java 17, OSGI, org.json, Gson, vis-timeline (CDN)

**Spec:** `docs/superpowers/specs/2026-03-28-mvvm-refactoring-design.md`

---

## File Map

| File | Action | Responsibility |
|---|---|---|
| `web/zul/meetingroom.zul` | Create (moved from `WEB-INF/web/`) | ZUL template with MVVM binding, CDN scripts, ZK dialog |
| `web/js/booking.js` | Create (moved + updated) | Timeline view JS; send ZK events instead of textbox triggers |
| `web/js/booking_weekview.js` | Create (moved + updated) | Week/day view JS; send ZK events instead of HTML dialog |
| `web/js/*` (others) | Create (moved, unchanged) | vis-timeline, jquery-ui, jquery.toast bundles (offline fallback) |
| `web/styles/*` | Create (moved, unchanged) | CSS bundles |
| `web/images/*` | Create (moved, unchanged) | Image assets |
| `META-INF/MANIFEST.MF` | Modify | `Bundle-ClassPath: src/, .`; add MVVM Import-Package entries |
| `build.properties` | Modify | `web/` replaces `WEB-INF/` in includes |
| `src/tw/ninniku/booking/viewmodel/BookingVM.java` | Create | POJO ViewModel — all state, @Command methods, rendering helpers |
| `src/tw/ninniku/booking/form/BookingForm.java` | Create | Thin ADForm controller — ClassLoader, ZUL, @Wire, JS event bridge |
| `src/tw/ninniku/booking/factories/BookingFormFactory.java` | Modify | Reference `BookingForm` instead of `BookingTimeline` |
| `WEB-INF/` (entire dir) | Delete | Replaced by `web/` |
| `src/.../form/BookingTimeline.java` | Delete | Replaced by `BookingForm.java` |

---

## Task 1: Move Web Resources

**Files:**
- Create: `web/zul/`, `web/js/`, `web/styles/`, `web/images/` (directories)
- Create: all files under `web/` (moved from `WEB-INF/web/`)

- [ ] **Step 1: Create directory structure and move files**

```bash
cd /Users/ray/sources/tw.ninniku.booking
mkdir -p web/zul web/js web/styles web/images
cp WEB-INF/web/js/* web/js/
cp WEB-INF/web/styles/* web/styles/
cp -r WEB-INF/web/styles/images/ web/styles/images/
cp WEB-INF/web/images/* web/images/
# meetingroom.zul will be rewritten in Task 8 — skip copying it now
git add web/
```

- [ ] **Step 2: Commit**

```bash
git add web/
git commit -m "Move web resources from WEB-INF/web/ to web/ (resource layout realignment)"
```

---

## Task 2: Update MANIFEST.MF and build.properties

**Files:**
- Modify: `META-INF/MANIFEST.MF`
- Modify: `build.properties`

- [ ] **Step 1: Update MANIFEST.MF**

Replace the entire `Bundle-ClassPath` line and add missing `Import-Package` entries.

Open `META-INF/MANIFEST.MF`. Make these changes:

**Change `Bundle-ClassPath`** (was `., src/, WEB-INF/`):
```
Bundle-ClassPath: src/,
 .
```

**Add to `Import-Package`** (after existing entries, before the blank line at end):
```
 org.zkoss.bind,
 org.zkoss.bind.annotation,
 org.zkoss.zk.ui.select,
 org.zkoss.zk.ui.select.annotation,
```

The resulting `Import-Package` block should include:
```
Import-Package: com.google.gson,
 org.json;version="[20230227.0.0,20230228.0.0]",
 org.osgi.framework,
 org.adempiere.webui.panel,
 org.zkoss;version="[9.6.0,10.0.0]",
 org.zkoss.bind,
 org.zkoss.bind.annotation,
 org.zkoss.image;version="[9.6.0,10.0.0]",
 org.zkoss.zhtml;version="[9.6.0,10.0.0]",
 org.zkoss.zk.ui;version="[9.6.0,10.0.0]",
 org.zkoss.zk.ui.event;version="[9.6.0,10.0.0]",
 org.zkoss.zk.ui.http;version="[9.6.0,10.0.0]",
 org.zkoss.zk.ui.select,
 org.zkoss.zk.ui.select.annotation,
 org.zkoss.zk.ui.util;version="[9.6.0,10.0.0]",
 org.zkoss.zul;version="[9.6.0,10.0.0]"
```

- [ ] **Step 2: Update build.properties**

Replace the entire file with:

```properties
output.. = bin/
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
```

- [ ] **Step 3: Commit**

```bash
git add META-INF/MANIFEST.MF build.properties
git commit -m "Update MANIFEST.MF Bundle-ClassPath and Import-Package; update build.properties for web/ layout"
```

---

## Task 3: Create BookingVM.java

**Files:**
- Create: `src/tw/ninniku/booking/viewmodel/BookingVM.java`

This ViewModel owns all ZK-visible state. It inherits rendering methods from `BookingTimeline` (copy and adapt). The `isAdmin` and `currentUserId` are passed in via constructor.

- [ ] **Step 1: Create the directory**

```bash
mkdir -p /Users/ray/sources/tw.ninniku.booking/src/tw/ninniku/booking/viewmodel
```

- [ ] **Step 2: Create BookingVM.java**

Create `src/tw/ninniku/booking/viewmodel/BookingVM.java`:

```java
package tw.ninniku.booking.viewmodel;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MResourceType;
import org.compiere.model.MUser;
import org.compiere.model.Query;
import org.compiere.util.Env;
import org.json.JSONObject;
import org.zkoss.bind.BindUtils;
import org.zkoss.bind.annotation.BindingParam;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.zk.ui.util.Clients;

import com.google.gson.Gson;

import tw.ninniku.booking.form.BookingDTO;
import tw.ninniku.booking.form.BookingService;
import tw.ninniku.booking.form.BookingValidationException;
import tw.ninniku.booking.form.BookingValidator;
import tw.ninniku.booking.model.MResourceAssignment;
import tw.ninniku.timeline.Group;
import tw.ninniku.timeline.Item;

public class BookingVM {

    // ── constructor state ───────────────────────────────────────────────────
    private final Properties ctx;
    private final boolean isAdmin;
    private final int currentUserId;
    private final BookingService bookingService;

    // ── resource selector ───────────────────────────────────────────────────
    private List<MResourceType> resourceTypes = new ArrayList<>();
    private MResourceType selectedResourceType;

    // ── view state ──────────────────────────────────────────────────────────
    private String viewMode = "week";   // "timeline" | "week" | "day"
    private Timestamp currentViewDate;
    private List<Group> groups = new ArrayList<>();

    // ── rendered HTML (bound to <html> component in ZUL) ────────────────────
    private String bookingHtml = "";

    // ── status ──────────────────────────────────────────────────────────────
    private String errorMessage;

    // ── dialog ──────────────────────────────────────────────────────────────
    private boolean dialogVisible;
    private boolean editMode;
    private String dialogError;
    private BookingDraft draft = new BookingDraft();

    // ═══════════════════════════════════════════════════════════════════════
    // BookingDraft inner class
    // ═══════════════════════════════════════════════════════════════════════

    public static class BookingDraft {
        private int bookingId;
        private int sResourceId;
        private String resourceName = "";
        private String name = "";
        private String description = "";
        private java.util.Date startDate;
        private java.util.Date startTime;
        private java.util.Date endDate;
        private java.util.Date endTime;
        private boolean weekly;
        private java.util.Date weeklyEndDate;

        public int getBookingId()                        { return bookingId; }
        public void setBookingId(int v)                  { this.bookingId = v; }
        public int getSResourceId()                      { return sResourceId; }
        public void setSResourceId(int v)                { this.sResourceId = v; }
        public String getResourceName()                  { return resourceName; }
        public void setResourceName(String v)            { this.resourceName = v != null ? v : ""; }
        public String getName()                          { return name; }
        public void setName(String v)                    { this.name = v != null ? v : ""; }
        public String getDescription()                   { return description; }
        public void setDescription(String v)             { this.description = v != null ? v : ""; }
        public java.util.Date getStartDate()             { return startDate; }
        public void setStartDate(java.util.Date v)       { this.startDate = v; }
        public java.util.Date getStartTime()             { return startTime; }
        public void setStartTime(java.util.Date v)       { this.startTime = v; }
        public java.util.Date getEndDate()               { return endDate; }
        public void setEndDate(java.util.Date v)         { this.endDate = v; }
        public java.util.Date getEndTime()               { return endTime; }
        public void setEndTime(java.util.Date v)         { this.endTime = v; }
        public boolean isWeekly()                        { return weekly; }
        public void setWeekly(boolean v)                 { this.weekly = v; }
        public java.util.Date getWeeklyEndDate()         { return weeklyEndDate; }
        public void setWeeklyEndDate(java.util.Date v)   { this.weeklyEndDate = v; }

        /** Combine startDate + startTime into a single Timestamp. */
        public Timestamp getAssignFrom() {
            return combineDateAndTime(startDate, startTime);
        }
        /** Combine endDate + endTime into a single Timestamp. */
        public Timestamp getAssignTo() {
            return combineDateAndTime(endDate, endTime);
        }
        /** null if !weekly or weeklyEndDate is null */
        public Timestamp getWeeklyEndTimestamp() {
            if (!weekly || weeklyEndDate == null) return null;
            return new Timestamp(weeklyEndDate.getTime());
        }

        private static Timestamp combineDateAndTime(java.util.Date date, java.util.Date time) {
            if (date == null) return new Timestamp(System.currentTimeMillis());
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            if (time != null) {
                Calendar tc = Calendar.getInstance();
                tc.setTime(time);
                cal.set(Calendar.HOUR_OF_DAY, tc.get(Calendar.HOUR_OF_DAY));
                cal.set(Calendar.MINUTE, tc.get(Calendar.MINUTE));
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
            }
            return new Timestamp(cal.getTimeInMillis());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════════

    public BookingVM(Properties ctx, boolean isAdmin, int currentUserId) {
        this.ctx = ctx;
        this.isAdmin = isAdmin;
        this.currentUserId = currentUserId;
        this.bookingService = new BookingService(ctx);
        this.currentViewDate = new Timestamp(System.currentTimeMillis());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════════

    @Init
    public void init() {
        String whereSql = " AD_Client_ID = ? ";
        resourceTypes = new Query(ctx, MResourceType.Table_Name, whereSql, null)
                .setParameters(Integer.valueOf(ctx.getProperty("#AD_Client_ID")))
                .setOrderBy(MResourceType.COLUMNNAME_Name)
                .setOnlyActiveRecords(true)
                .list();

        // Select default resource type (marked with "[default]" in description)
        for (MResourceType rt : resourceTypes) {
            if (rt.getDescription() != null && rt.getDescription().contains("[default]")) {
                selectedResourceType = rt;
                break;
            }
        }
        if (selectedResourceType == null && !resourceTypes.isEmpty()) {
            selectedResourceType = resourceTypes.get(0);
        }

        // Initial load
        doRefreshView();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Commands — toolbar
    // ═══════════════════════════════════════════════════════════════════════

    @Command
    @NotifyChange({"bookingHtml", "errorMessage"})
    public void refresh() {
        doRefreshView();
    }

    @Command
    @NotifyChange({"bookingHtml", "errorMessage"})
    public void changeViewMode(@BindingParam("mode") String mode) {
        this.viewMode = mode;
        doRefreshView();
    }

    @Command
    @NotifyChange({"bookingHtml", "errorMessage"})
    public void prevPeriod() {
        navigateView(-1);
    }

    @Command
    @NotifyChange({"bookingHtml", "errorMessage"})
    public void nextPeriod() {
        navigateView(1);
    }

    @Command
    @NotifyChange({"bookingHtml", "errorMessage"})
    public void today() {
        currentViewDate = new Timestamp(System.currentTimeMillis());
        doRefreshView();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Commands — dialog
    // ═══════════════════════════════════════════════════════════════════════

    @Command
    @NotifyChange({"dialogVisible", "editMode", "draft", "dialogError"})
    public void openAddDialog() {
        draft = new BookingDraft();
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        int min = cal.get(Calendar.MINUTE);
        if (min > 30) {
            cal.set(Calendar.MINUTE, 0);
            cal.add(Calendar.HOUR_OF_DAY, 1);
        } else if (min > 0) {
            cal.set(Calendar.MINUTE, 30);
        }
        draft.setStartDate(new java.util.Date(cal.getTimeInMillis()));
        draft.setStartTime(new java.util.Date(cal.getTimeInMillis()));
        cal.add(Calendar.HOUR_OF_DAY, 1);
        draft.setEndDate(new java.util.Date(cal.getTimeInMillis()));
        draft.setEndTime(new java.util.Date(cal.getTimeInMillis()));
        if (selectedResourceType != null) {
            draft.setSResourceId(getFirstResourceIdForType(selectedResourceType.getS_ResourceType_ID()));
        }
        editMode = false;
        dialogError = null;
        dialogVisible = true;
    }

    @Command
    @NotifyChange({"dialogVisible", "dialogError", "bookingHtml", "errorMessage"})
    public void saveBooking() {
        try {
            BookingValidator.validateDraft(draft);
            BookingDTO dto = draftToDto(draft);
            bookingService.saveBooking(dto, isAdmin, currentUserId);
            dialogVisible = false;
            dialogError = null;
            doRefreshView();
        } catch (BookingValidationException e) {
            dialogError = e.getMessage();
        } catch (AdempiereException e) {
            dialogError = e.getMessage();
        }
    }

    @Command
    @NotifyChange({"dialogVisible", "bookingHtml", "errorMessage"})
    public void deleteBooking() {
        try {
            bookingService.deleteBooking(draft.getBookingId(), isAdmin, currentUserId);
            dialogVisible = false;
            doRefreshView();
        } catch (BookingValidationException e) {
            errorMessage = e.getMessage();
            BindUtils.postNotifyChange(null, null, this, "errorMessage");
        } catch (AdempiereException e) {
            errorMessage = e.getMessage();
            BindUtils.postNotifyChange(null, null, this, "errorMessage");
        }
    }

    @Command
    @NotifyChange("dialogVisible")
    public void closeDialog() {
        dialogVisible = false;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Non-command methods (called from BookingForm.onEvent)
    // ═══════════════════════════════════════════════════════════════════════

    /** Called when JS sends onBookingEdit ZK event (click on existing booking). */
    public void prepareEdit(String jsonStr) throws BookingValidationException {
        JSONObject json;
        try {
            json = new JSONObject(jsonStr);
        } catch (Exception e) {
            throw new BookingValidationException("Invalid booking data: " + e.getMessage());
        }
        draft = new BookingDraft();
        long id = parseLong(json, "s_booking_id", 0);
        draft.setBookingId((int) id);
        int resId = (int) parseLong(json, "s_resource_id", 0);
        draft.setSResourceId(resId);
        draft.setResourceName(resolveResourceName(resId));
        draft.setName(json.optString("booking-name", ""));
        draft.setDescription(json.optString("description", ""));
        long startMs = parseLong(json, "assign-date-from-timestamp", 0);
        long endMs = parseLong(json, "assign-date-to-timestamp", 0);
        java.util.Date start = startMs > 0 ? new java.util.Date(startMs) : new java.util.Date();
        java.util.Date end   = endMs   > 0 ? new java.util.Date(endMs)   : new java.util.Date();
        draft.setStartDate(start);
        draft.setStartTime(start);
        draft.setEndDate(end);
        draft.setEndTime(end);
        draft.setWeekly(false);
        editMode = true;
        dialogError = null;
        dialogVisible = true;
        BindUtils.postNotifyChange(null, null, this, "draft");
        BindUtils.postNotifyChange(null, null, this, "editMode");
        BindUtils.postNotifyChange(null, null, this, "dialogError");
        BindUtils.postNotifyChange(null, null, this, "dialogVisible");
    }

    /** Called when JS sends onBookingAdd ZK event (click on empty time slot). */
    public void prepareAdd(String jsonStr) throws BookingValidationException {
        JSONObject json;
        try {
            json = new JSONObject(jsonStr);
        } catch (Exception e) {
            throw new BookingValidationException("Invalid data: " + e.getMessage());
        }
        draft = new BookingDraft();
        int resId = (int) parseLong(json, "s_resource_id", 0);
        draft.setSResourceId(resId);
        draft.setResourceName(resolveResourceName(resId));
        long startMs = parseLong(json, "assign-date-from-timestamp", 0);
        long endMs   = parseLong(json, "assign-date-to-timestamp", 0);
        java.util.Date start = startMs > 0 ? new java.util.Date(startMs) : new java.util.Date();
        java.util.Date end   = endMs   > 0 ? new java.util.Date(endMs)   : new java.util.Date();
        draft.setStartDate(start);
        draft.setStartTime(start);
        draft.setEndDate(end);
        draft.setEndTime(end);
        editMode = false;
        dialogError = null;
        dialogVisible = true;
        BindUtils.postNotifyChange(null, null, this, "draft");
        BindUtils.postNotifyChange(null, null, this, "editMode");
        BindUtils.postNotifyChange(null, null, this, "dialogError");
        BindUtils.postNotifyChange(null, null, this, "dialogVisible");
    }

    /** Called when JS sends onBookingDelete ZK event (delete icon click). */
    public void deleteDirectlyById(int bookingId) {
        try {
            bookingService.deleteBooking(bookingId, isAdmin, currentUserId);
            doRefreshView();
            BindUtils.postNotifyChange(null, null, this, "bookingHtml");
        } catch (BookingValidationException | AdempiereException e) {
            errorMessage = e.getMessage();
            BindUtils.postNotifyChange(null, null, this, "errorMessage");
        }
    }

    /** Called from BookingForm.onEvent for drag-drop/resize (dateLast). Refreshes after updateBookingTime. */
    public void doRefreshView() {
        try {
            errorMessage = null;
            int resourceTypeId = getSelectedResourceTypeId();
            groups = bookingService.fetchGroups(resourceTypeId);

            if ("timeline".equals(viewMode)) {
                String groupJson = new Gson().toJson(groups);
                String itemJson = buildTimelineItemJson(resourceTypeId);
                bookingHtml = "<div id='booking-chart' style='height:100%;width:100%;'></div>";
                Clients.evalJavaScript(whenReady(
                    "BookingApp.Timeline.setGroups(" + groupJson + ");" +
                    "BookingApp.Timeline.setItems(" + itemJson + ");" +
                    "BookingApp.Timeline.initChart();"
                ));
            } else if ("week".equals(viewMode)) {
                bookingHtml = renderWeekView(resourceTypeId);
                Clients.evalJavaScript(
                    "(function(){if(window.BookingApp&&BookingApp.WeekView){" +
                    "BookingApp.WeekView.weekViewScrollTo8Am();" +
                    "BookingApp.WeekView.updateTimeIndicator();}" +
                    "})();"
                );
            } else if ("day".equals(viewMode)) {
                bookingHtml = renderDayView(resourceTypeId);
                Clients.evalJavaScript(
                    "(function(){if(window.BookingApp&&BookingApp.WeekView){" +
                    "BookingApp.WeekView.weekViewScrollTo8Am();" +
                    "BookingApp.WeekView.updateTimeIndicator();}" +
                    "})();"
                );
            }
        } catch (AdempiereException e) {
            errorMessage = "Failed to load data: " + e.getMessage();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Private helpers
    // ═══════════════════════════════════════════════════════════════════════

    private void navigateView(int direction) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(currentViewDate);
        if ("week".equals(viewMode)) {
            cal.add(Calendar.WEEK_OF_YEAR, direction);
        } else if ("day".equals(viewMode)) {
            cal.add(Calendar.DAY_OF_YEAR, direction);
        } else {
            cal.add(Calendar.DAY_OF_YEAR, direction * 7);
        }
        currentViewDate = new Timestamp(cal.getTimeInMillis());
        doRefreshView();
    }

    private int getSelectedResourceTypeId() {
        if (selectedResourceType != null) return selectedResourceType.getS_ResourceType_ID();
        if (!resourceTypes.isEmpty()) return resourceTypes.get(0).getS_ResourceType_ID();
        return 0;
    }

    private int getFirstResourceIdForType(int resourceTypeId) {
        if (!groups.isEmpty()) return Integer.parseInt(String.valueOf(groups.get(0).getId()));
        List<Group> g = bookingService.fetchGroups(resourceTypeId);
        if (!g.isEmpty()) return Integer.parseInt(String.valueOf(g.get(0).getId()));
        return 0;
    }

    private String resolveResourceName(int sResourceId) {
        if (groups != null) {
            for (Group g : groups) {
                try {
                    if (Integer.parseInt(String.valueOf(g.getId())) == sResourceId)
                        return g.getContent();
                } catch (NumberFormatException ignored) {}
            }
        }
        return "";
    }

    private String buildTimelineItemJson(int resourceTypeId) {
        String whereSql = " AssignDateFrom >= (now() - INTERVAL '2 days ') "
                + "and exists (select 1 from S_Resource where S_Resource_ID = "
                + "S_ResourceAssignment.S_Resource_ID and S_ResourceType_ID = ?) ";
        List<MResourceAssignment> bookings =
                new Query(ctx, MResourceAssignment.Table_Name, whereSql, null)
                        .setParameters(resourceTypeId)
                        .setOrderBy("S_Resource_ID")
                        .setOnlyActiveRecords(true)
                        .list();
        List<Item> list = new ArrayList<>();
        for (MResourceAssignment b : bookings) {
            Item item = new Item(b.getS_ResourceAssignment_ID());
            item.setStart((Timestamp) b.getAssignDateFrom());
            item.setEnd((Timestamp) b.getAssignDateTo());
            boolean editable = isAdmin || currentUserId == b.getCreatedBy();
            item.setEditable(editable);
            item.setName(b.getName());
            item.setDescription(b.getDescription());
            item.setGroup(b.getS_Resource_ID());
            MUser user = new MUser(ctx, b.getCreatedBy(), null);
            String content = "(" + user.getName() + ")<br/>" + b.getName();
            if (b.getDescription() != null) content += "<br/> " + b.getDescription();
            item.setContent(content);
            item.setTitle(content);
            list.add(item);
        }
        return new Gson().toJson(list);
    }

    private String renderWeekView(int resourceTypeId) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(currentViewDate);
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        Timestamp start = new Timestamp(cal.getTimeInMillis());
        cal.add(Calendar.DAY_OF_YEAR, 5);
        Timestamp end = new Timestamp(cal.getTimeInMillis());

        List<MResourceAssignment> bookings = bookingService.fetchBookings(resourceTypeId, start, end);
        Map<Integer, String> resourceNameMap = buildResourceNameMap();
        boolean workHoursOnly = true; // default; JS reads chkWorkHours DOM for display
        int startHour = 8, endHour = 18;
        int hourCount = endHour - startHour + 1;
        int heightPx = hourCount * 40;

        StringBuilder html = new StringBuilder();
        html.append("<div class='week-view-root' data-start-hour='").append(startHour).append("'>");
        html.append("<div class='week-header'><div class='header-time-spacer'></div>");

        SimpleDateFormat sdfDay = new SimpleDateFormat("EEE MM/dd");
        SimpleDateFormat sdfKey = new SimpleDateFormat("yyyy-MM-dd");
        cal.setTime(start);
        List<String> dayKeys = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            html.append("<div class='header-day'>").append(sdfDay.format(cal.getTime())).append("</div>");
            dayKeys.add(sdfKey.format(cal.getTime()));
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        html.append("</div>"); // week-header

        html.append("<div class='scroll-body'>");
        html.append("<div class='week-layout' style='min-height:").append(heightPx).append("px;'>");
        html.append("<div class='time-col'>");
        for (int i = startHour; i <= endHour; i++) {
            html.append("<div class='time-slot'>").append(String.format("%02d:00", i)).append("</div>");
        }
        html.append("</div>"); // time-col

        html.append("<div class='days-grid'>");
        String defaultResId = (!groups.isEmpty() ? String.valueOf(groups.get(0).getId()) : "");
        for (String dayKey : dayKeys) {
            html.append("<div class='day-col' data-date='").append(dayKey)
                .append("' data-resource-id='").append(defaultResId)
                .append("' style='height:").append(heightPx).append("px;'>");
            List<MResourceAssignment> dayEvents = new ArrayList<>();
            for (MResourceAssignment b : bookings) {
                if (sdfKey.format(b.getAssignDateFrom()).equals(dayKey)) dayEvents.add(b);
            }
            renderEventCards(html, sortAndPackEvents(dayEvents), startHour, resourceNameMap);
            html.append("</div>"); // day-col
        }
        appendViewFooter(html);
        return html.toString();
    }

    private String renderDayView(int resourceTypeId) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(currentViewDate);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Timestamp start = new Timestamp(cal.getTimeInMillis());
        cal.add(Calendar.DAY_OF_YEAR, 1);
        Timestamp end = new Timestamp(cal.getTimeInMillis());

        List<MResourceAssignment> bookings = bookingService.fetchBookings(resourceTypeId, start, end);
        Map<Integer, String> resourceNameMap = buildResourceNameMap();
        int startHour = 8, endHour = 18;
        int hourCount = endHour - startHour + 1;
        int heightPx = hourCount * 40;

        StringBuilder html = new StringBuilder();
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy/MM/dd (EEE)");
        SimpleDateFormat sdfKey  = new SimpleDateFormat("yyyy-MM-dd");
        String dayKey = sdfKey.format(start);

        html.append("<div class='week-view-root' data-start-hour='").append(startHour).append("'>");
        html.append("<div style='text-align:center;font-size:15px;font-weight:bold;padding:6px 0;")
            .append("background:#f5f5f5;border-bottom:1px solid #ddd;flex-shrink:0;'>")
            .append(sdfDate.format(start)).append("</div>");
        html.append("<div class='week-header'><div class='header-time-spacer'></div>");
        for (Group g : groups) {
            String color = getResourceColor(Integer.valueOf(String.valueOf(g.getId())));
            html.append("<div class='header-day' style='color:").append(color).append(";'>")
                .append(g.getContent()).append("</div>");
        }
        html.append("</div>"); // week-header

        html.append("<div class='scroll-body'>");
        html.append("<div class='week-layout' style='min-height:").append(heightPx).append("px;'>");
        html.append("<div class='time-col'>");
        for (int i = startHour; i <= endHour; i++) {
            html.append("<div class='time-slot'>").append(String.format("%02d:00", i)).append("</div>");
        }
        html.append("</div>"); // time-col

        html.append("<div class='days-grid'>");
        for (Group g : groups) {
            int resourceId = Integer.valueOf(String.valueOf(g.getId()));
            html.append("<div class='day-col' data-date='").append(dayKey)
                .append("' data-resource-id='").append(resourceId)
                .append("' style='height:").append(heightPx).append("px;'>");
            List<MResourceAssignment> resEvents = new ArrayList<>();
            for (MResourceAssignment b : bookings) {
                if (b.getS_Resource_ID() == resourceId) resEvents.add(b);
            }
            renderEventCards(html, sortAndPackEvents(resEvents), startHour, resourceNameMap);
            html.append("</div>"); // day-col
        }
        appendViewFooter(html);
        return html.toString();
    }

    private void renderEventCards(StringBuilder html, List<List<MResourceAssignment>> columns,
            int startHour, Map<Integer, String> resourceNameMap) {
        int numCols = columns.size();
        double colWidthPercent = 95.0 / (numCols > 0 ? numCols : 1);
        for (int colIndex = 0; colIndex < numCols; colIndex++) {
            for (MResourceAssignment b : columns.get(colIndex)) {
                long startMs = b.getAssignDateFrom().getTime();
                long endMs = b.getAssignDateTo().getTime();
                Calendar dayStart = Calendar.getInstance();
                dayStart.setTime(b.getAssignDateFrom());
                dayStart.set(Calendar.HOUR_OF_DAY, startHour);
                dayStart.set(Calendar.MINUTE, 0);
                dayStart.set(Calendar.SECOND, 0);
                double pxPerMin = 40.0 / 60.0;
                double top = ((startMs - dayStart.getTimeInMillis()) / 60000.0) * pxPerMin;
                double height = Math.max(((endMs - startMs) / 60000.0) * pxPerMin, 15);
                double left = 2.0 + colIndex * colWidthPercent;
                double width = colWidthPercent - 2.0;
                MUser user = new MUser(ctx, b.getCreatedBy(), null);
                String resName = resourceNameMap.getOrDefault(b.getS_Resource_ID(), "");
                if (!resName.isEmpty()) resName = "[" + BookingValidator.escapeForHtml(resName) + "] ";
                String title = resName + BookingValidator.escapeForHtml(b.getName())
                        + " (" + BookingValidator.escapeForHtml(user.getName()) + ")";
                String color = getResourceColor(b.getS_Resource_ID());
                String displayContent = title;
                if (b.getDescription() != null && !b.getDescription().isEmpty()) {
                    displayContent += "<br/><span style='font-size:10px;opacity:0.9;'>"
                            + BookingValidator.escapeForHtml(b.getDescription()) + "</span>";
                }
                boolean isOwnerOrAdmin = isAdmin || b.getCreatedBy() == currentUserId;
                String deleteIconHtml = isOwnerOrAdmin ? String.format(
                        "<span class='delete-icon' onclick='BookingApp.WeekView.onWeekEventDelete(event,%d)'>&times;</span>",
                        b.getS_ResourceAssignment_ID()) : "";
                String nameJS = BookingValidator.escapeForJs(b.getName());
                String descJS = BookingValidator.escapeForJs(b.getDescription());
                String editableClass = isOwnerOrAdmin ? "editable" : "";
                String resizeHandle = isOwnerOrAdmin ? "<div class='resize-handle'></div>" : "";
                html.append(String.format(
                        "<div class='event-card %s' style='top:%.1fpx;height:%.1fpx;"
                        + "background-color:%s;width:%.1f%%;left:%.1f%%;' "
                        + "data-id='%d' data-resource-id='%d' data-start-ms='%d' data-end-ms='%d' "
                        + "onclick=\"BookingApp.WeekView.onWeekEventClick(event,'%s','%s','%s','%s',%s,%s);\">"
                        + "%s%s%s</div>",
                        editableClass, top, height, color, width, left,
                        b.getS_ResourceAssignment_ID(), b.getS_Resource_ID(), startMs, endMs,
                        b.getS_ResourceAssignment_ID(), nameJS, descJS,
                        b.getS_Resource_ID(), startMs, endMs,
                        displayContent, deleteIconHtml, resizeHandle));
            }
        }
    }

    private void appendViewFooter(StringBuilder html) {
        html.append("<div class='current-time-line'></div>");
        html.append("</div>"); // days-grid
        html.append("</div>"); // week-layout
        html.append("</div>"); // scroll-body
        html.append("</div>"); // week-view-root
    }

    private List<List<MResourceAssignment>> sortAndPackEvents(List<MResourceAssignment> events) {
        Collections.sort(events, new Comparator<MResourceAssignment>() {
            public int compare(MResourceAssignment a, MResourceAssignment b) {
                int c = a.getAssignDateFrom().compareTo(b.getAssignDateFrom());
                return c != 0 ? c : b.getAssignDateTo().compareTo(a.getAssignDateTo());
            }
        });
        List<List<MResourceAssignment>> columns = new ArrayList<>();
        for (MResourceAssignment evt : events) {
            boolean placed = false;
            for (List<MResourceAssignment> col : columns) {
                if (evt.getAssignDateFrom().getTime() >= col.get(col.size() - 1).getAssignDateTo().getTime()) {
                    col.add(evt);
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                List<MResourceAssignment> newCol = new ArrayList<>();
                newCol.add(evt);
                columns.add(newCol);
            }
        }
        return columns;
    }

    private Map<Integer, String> buildResourceNameMap() {
        Map<Integer, String> map = new HashMap<>();
        for (Group g : groups) {
            try { map.put(Integer.valueOf(String.valueOf(g.getId())), g.getContent()); }
            catch (NumberFormatException ignored) {}
        }
        return map;
    }

    private String getResourceColor(int resourceId) {
        String[] colors = {
            "#C62828","#AD1457","#6A1B9A","#4527A0","#283593",
            "#1565C0","#0277BD","#00838F","#00695C","#2E7D32",
            "#558B2F","#9E9D24","#F9A825","#FF8F00","#EF6C00",
            "#D84315","#4E342E","#424242","#37474F"
        };
        return colors[Math.abs(resourceId) % colors.length];
    }

    private static String whenReady(String jsExpression) {
        return "(function poll(){if(window.BookingApp&&window.BookingApp.Timeline){"
                + jsExpression + "}else{setTimeout(poll,50);}})();";
    }

    private static long parseLong(JSONObject json, String key, long defaultValue) {
        String val = json.optString(key, "").trim();
        if (val.isEmpty()) return defaultValue;
        try { return Long.parseLong(val); } catch (NumberFormatException e) { return defaultValue; }
    }

    /** Converts BookingDraft to BookingDTO for service calls. */
    private static BookingDTO draftToDto(BookingDraft d) throws BookingValidationException {
        Timestamp assignFrom = d.getAssignFrom();
        Timestamp assignTo   = d.getAssignTo();
        Timestamp weeklyEnd  = d.getWeeklyEndTimestamp();
        String raw = "{"
            + "\"s_booking_id\":\"" + d.getBookingId() + "\","
            + "\"s_resource_id\":\"" + d.getSResourceId() + "\","
            + "\"group\":\"" + d.getSResourceId() + "\","
            + "\"booking-name\":\"" + BookingValidator.escapeForJs(d.getName()) + "\","
            + "\"description\":\"" + BookingValidator.escapeForJs(d.getDescription()) + "\","
            + "\"startTimestamp\":\"" + assignFrom.getTime() + "\","
            + "\"endTimestamp\":\"" + assignTo.getTime() + "\","
            + "\"assign-date-from-timestamp\":\"" + assignFrom.getTime() + "\","
            + "\"assign-date-to-timestamp\":\"" + assignTo.getTime() + "\","
            + "\"is-weekly\":\"" + (d.isWeekly() ? "Y" : "N") + "\","
            + "\"repeat-date-to-timestamp\":\"" + (weeklyEnd != null ? weeklyEnd.getTime() : "") + "\""
            + "}";
        return BookingDTO.fromJson(raw);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Getters for ZUL binding
    // ═══════════════════════════════════════════════════════════════════════

    public List<MResourceType> getResourceTypes()          { return resourceTypes; }
    public MResourceType getSelectedResourceType()          { return selectedResourceType; }
    public void setSelectedResourceType(MResourceType v)    { this.selectedResourceType = v; }
    public String getViewMode()                             { return viewMode; }
    public String getBookingHtml()                          { return bookingHtml != null ? bookingHtml : ""; }
    public String getErrorMessage()                         { return errorMessage; }
    public void setErrorMessage(String v)                   { this.errorMessage = v; }
    public boolean isDialogVisible()                        { return dialogVisible; }
    public boolean isEditMode()                             { return editMode; }
    public String getDialogError()                          { return dialogError; }
    public BookingDraft getDraft()                          { return draft; }
}
```

- [ ] **Step 3: Add `validateDraft` to BookingValidator**

Open `src/tw/ninniku/booking/form/BookingValidator.java` and add this method (BookingVM calls it):

```java
/**
 * Validates a BookingDraft for business rules.
 * Throws BookingValidationException with a user-facing message if invalid.
 */
public static void validateDraft(tw.ninniku.booking.viewmodel.BookingVM.BookingDraft draft)
        throws BookingValidationException {
    if (draft.getName() == null || draft.getName().trim().isEmpty()) {
        throw new BookingValidationException("Name is required.");
    }
    java.sql.Timestamp from = draft.getAssignFrom();
    java.sql.Timestamp to   = draft.getAssignTo();
    if (from != null && to != null && !from.before(to)) {
        throw new BookingValidationException("Start time must be before end time.");
    }
    if (draft.isWeekly() && draft.getWeeklyEndDate() == null) {
        throw new BookingValidationException("Weekly end date is required for weekly bookings.");
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add src/tw/ninniku/booking/viewmodel/BookingVM.java \
        src/tw/ninniku/booking/form/BookingValidator.java
git commit -m "Add BookingVM with BookingDraft; add validateDraft to BookingValidator"
```

---

## Task 4: Create BookingForm.java (Thin Controller)

**Files:**
- Create: `src/tw/ninniku/booking/form/BookingForm.java`

- [ ] **Step 1: Create BookingForm.java**

Create `src/tw/ninniku/booking/form/BookingForm.java`:

```java
package tw.ninniku.booking.form;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.webui.event.ValueChangeEvent;
import org.adempiere.webui.event.ValueChangeListener;
import org.adempiere.webui.panel.ADForm;
import org.adempiere.webui.panel.IFormController;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.json.JSONObject;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.zkoss.bind.BindUtils;
import org.zkoss.bind.Binder;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.select.Selectors;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zul.Textbox;

import tw.ninniku.booking.viewmodel.BookingVM;

public class BookingForm extends ADForm
        implements IFormController, EventListener<Event>, ValueChangeListener {

    private static final long serialVersionUID = 1L;
    private static final Logger log = Logger.getLogger(BookingForm.class.getName());

    @Wire("#bookingVMContainer")
    private Component bookingVMContainer;

    @Wire("#dateLast")
    private Textbox dateLast;

    @Wire("#itemData")
    private Textbox itemData;

    @Override
    protected void initForm() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            boolean admin = isWritable();
            int userId = Env.getContextAsInt(Env.getCtx(), "#AD_User_ID");

            Map<String, Object> args = new HashMap<>();
            args.put("vm", new BookingVM(Env.getCtx(), admin, userId));
            args.put("version", resolvePluginVersion());
            Executions.createComponents("~./zul/meetingroom.zul", this, args);
            Selectors.wireComponents(this, this, false);

            if (dateLast != null) {
                dateLast.addEventListener(Events.ON_CHANGE, this);
            }

        } catch (Exception e) {
            log.severe("Failed to init BookingForm: " + e.getMessage());
        } finally {
            Thread.currentThread().setContextClassLoader(cl);
        }
    }

    @Override
    public void onEvent(Event e) throws Exception {
        String name = e.getName();

        // Drag-drop / resize from timeline view (JS fires dateLast onChange)
        if (e.getTarget() == dateLast) {
            BookingVM vm = getViewModel();
            if (vm == null || itemData == null) return;
            try {
                JSONObject json = new JSONObject(itemData.getValue());
                // "id" is the vis.js item ID = S_ResourceAssignment_ID
                int bookingId  = (int) parseLong(json, "id", 0);
                int resourceId = (int) parseLong(json, "group", 0);
                long startMs   = parseLong(json, "startTimestamp", 0);
                long endMs     = parseLong(json, "endTimestamp", 0);
                if (bookingId > 0 && startMs > 0 && endMs > 0) {
                    boolean admin = isWritable();
                    int userId = Env.getContextAsInt(Env.getCtx(), "#AD_User_ID");
                    BookingService svc = new BookingService(Env.getCtx());
                    svc.updateBookingTime(bookingId, resourceId,
                            new java.sql.Timestamp(startMs),
                            new java.sql.Timestamp(endMs),
                            admin, userId);
                    vm.doRefreshView();
                    BindUtils.postNotifyChange(null, null, vm, "bookingHtml");
                }
            } catch (BookingValidationException ex) {
                BookingVM vm2 = getViewModel();
                if (vm2 != null) {
                    vm2.setErrorMessage(ex.getMessage());
                    BindUtils.postNotifyChange(null, null, vm2, "errorMessage");
                }
            } catch (AdempiereException ex) {
                BookingVM vm2 = getViewModel();
                if (vm2 != null) {
                    vm2.setErrorMessage(ex.getMessage());
                    BindUtils.postNotifyChange(null, null, vm2, "errorMessage");
                }
            }
            return;
        }

        // JS → ZK events for booking edit / add / delete
        if ("onBookingEdit".equals(name)) {
            BookingVM vm = getViewModel();
            if (vm == null) return;
            try {
                vm.prepareEdit((String) e.getData());
            } catch (BookingValidationException ex) {
                vm.setErrorMessage(ex.getMessage());
                BindUtils.postNotifyChange(null, null, vm, "errorMessage");
            }
            return;
        }

        if ("onBookingAdd".equals(name)) {
            BookingVM vm = getViewModel();
            if (vm == null) return;
            try {
                vm.prepareAdd((String) e.getData());
            } catch (BookingValidationException ex) {
                vm.setErrorMessage(ex.getMessage());
                BindUtils.postNotifyChange(null, null, vm, "errorMessage");
            }
            return;
        }

        if ("onBookingDelete".equals(name)) {
            BookingVM vm = getViewModel();
            if (vm == null) return;
            try {
                int bookingId = Integer.parseInt(String.valueOf(e.getData()).trim());
                vm.deleteDirectlyById(bookingId);
            } catch (NumberFormatException ex) {
                log.warning("Invalid bookingId in onBookingDelete: " + e.getData());
            }
            return;
        }

        super.onEvent(e);
    }

    @Override
    public void valueChange(ValueChangeEvent evt) { }

    @Override
    public ADForm getForm() { return this; }

    // ── helpers ─────────────────────────────────────────────────────────────

    private BookingVM getViewModel() {
        if (bookingVMContainer == null) return null;
        Binder binder = (Binder) bookingVMContainer.getAttribute("binder");
        if (binder == null) return null;
        Object vm = binder.getViewModel();
        return (vm instanceof BookingVM) ? (BookingVM) vm : null;
    }

    private boolean isWritable() {
        String sql = "select isreadwrite from AD_Form_Access "
                + "where ad_role_id = ? and ad_form_id = ?";
        String result = DB.getSQLValueString(null, sql,
                Integer.valueOf(Env.getCtx().getProperty("#AD_Role_ID")),
                getAdFormId());
        return "Y".equals(result);
    }

    private static String resolvePluginVersion() {
        Bundle host = FrameworkUtil.getBundle(BookingForm.class);
        if (host != null && host.getBundleContext() != null) {
            for (Bundle b : host.getBundleContext().getBundles()) {
                if ("tw.ninniku.booking".equals(b.getSymbolicName())) {
                    return b.getVersion().toString();
                }
            }
        }
        return "?.?.?";
    }

    private static long parseLong(JSONObject json, String key, long defaultValue) {
        String val = json.optString(key, "").trim();
        if (val.isEmpty()) return defaultValue;
        try { return Long.parseLong(val); } catch (NumberFormatException e) { return defaultValue; }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/tw/ninniku/booking/form/BookingForm.java
git commit -m "Add thin BookingForm controller: ClassLoader, ZUL, @Wire, drag-drop and JS event bridge"
```

---

## Task 5: Update BookingFormFactory.java

**Files:**
- Modify: `src/tw/ninniku/booking/factories/BookingFormFactory.java`

- [ ] **Step 1: Update the factory to reference BookingForm**

Replace the entire file content:

```java
package tw.ninniku.booking.factories;

import org.adempiere.webui.factory.IFormFactory;
import org.adempiere.webui.panel.ADForm;
import tw.ninniku.booking.form.BookingForm;

public class BookingFormFactory implements IFormFactory {

    @Override
    public ADForm newFormInstance(String formName) {
        if (formName != null && formName.contains("BookingForm")) {
            return new BookingForm().getForm();
        }
        // Backwards compatibility: old AD_Form records may still have "BookingTimeline"
        if (formName != null && formName.contains("BookingTimeline")) {
            return new BookingForm().getForm();
        }
        return null;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/tw/ninniku/booking/factories/BookingFormFactory.java
git commit -m "Update BookingFormFactory: reference BookingForm; keep BookingTimeline alias for existing AD_Form records"
```

---

## Task 6: Update booking.js — Replace HTML dialog with ZK events

**Files:**
- Modify: `web/js/booking.js` (the file we moved in Task 1)

The changes: replace every `bookingUpdated`/`bookingDeleted` textbox trigger with `sendZkBookingEvent()` calls; replace `openEditDialog` HTML dialog with ZK event; replace `clickNew` HTML dialog with ZK event.

- [ ] **Step 1: Add ZK event helper at the top of booking.js**

Open `web/js/booking.js`. After `var BookingApp = BookingApp || {};` and the IIFE open, add before the `var isload` line:

```javascript
    // ── ZK event bridge ──────────────────────────────────────────────────────
    function sendZkBookingEvent(eventName, data) {
        var vmContainer = zk.Widget.$('$bookingVMContainer');
        if (vmContainer) {
            zAu.send(new zk.Event(vmContainer, eventName, data));
        } else {
            console.warn('bookingVMContainer not found for event:', eventName);
        }
    }
```

- [ ] **Step 2: Replace onMove — fix bookingId key (was broken: read "id" not "s_booking_id")**

The `onMove` handler already writes `item` to `$itemData` and fires `$dateLast` — this is kept as-is for drag-drop. No change needed here; the controller now correctly reads `"id"` field.

- [ ] **Step 3: Replace onRemove — use ZK event instead of bookingDeleted textbox**

Find (around line 76-84):
```javascript
    onRemove: function (item, callback) {
        //callback(null); // cancel deletion
        zk.$("$itemData").setValue(JSON.stringify(item));
        zk.$("$itemData").fireOnChange();

        zk.$("$bookingDeleted").setValue(Date.now().toString());
        zk.$("$bookingDeleted").fireOnChange();
        callback(item);
    }
```

Replace with:
```javascript
    onRemove: function (item, callback) {
        if (confirm('Are you sure you want to delete this booking?')) {
            var bookingId = item.id || item.s_booking_id || 0;
            sendZkBookingEvent('onBookingDelete', String(bookingId));
            callback(item);
        } else {
            callback(null);
        }
    }
```

- [ ] **Step 4: Replace openEditDialog — use ZK event instead of HTML dialog**

Find `function openEditDialog(item, callback)` (around line 169). Replace the entire function:

```javascript
function openEditDialog(item, callback) {
    var startMs = item.start instanceof Date ? item.start.getTime() : Number(item.start);
    var endMs   = item.end   instanceof Date ? item.end.getTime()   : Number(item.end);
    var json = JSON.stringify({
        's_booking_id':              String(item.id || item.s_booking_id || 0),
        's_resource_id':             String(item.group || 0),
        'booking-name':              item.name || item.content || '',
        'description':               item.description || '',
        'startTimestamp':            String(startMs),
        'endTimestamp':              String(endMs),
        'assign-date-from-timestamp': String(startMs),
        'assign-date-to-timestamp':   String(endMs)
    });
    sendZkBookingEvent('onBookingEdit', json);
    if (callback) callback(item); // keep timeline item in place
}
```

- [ ] **Step 5: Replace clickNew — use ZK event instead of HTML dialog**

Find `function clickNew(item, callback)` (around line 262). Replace the entire function:

```javascript
function clickNew(item, callback) {
    var startMs = item.start instanceof Date ? item.start.getTime() : Number(item.start);
    var endMs   = item.end   instanceof Date ? item.end.getTime()   : Number(item.end);
    var json = JSON.stringify({
        's_booking_id':              '0',
        's_resource_id':             String(item.group || 0),
        'booking-name':              '',
        'description':               '',
        'startTimestamp':            String(startMs),
        'endTimestamp':              String(endMs),
        'assign-date-from-timestamp': String(startMs),
        'assign-date-to-timestamp':   String(endMs)
    });
    sendZkBookingEvent('onBookingAdd', json);
    if (callback) callback(null); // don't add ghost item to timeline — server will refresh
}
```

- [ ] **Step 6: Commit**

```bash
git add web/js/booking.js
git commit -m "booking.js: replace HTML dialog and bookingDeleted/bookingUpdated with ZK event sends"
```

---

## Task 7: Update booking_weekview.js — Replace HTML dialog with ZK events

**Files:**
- Modify: `web/js/booking_weekview.js`

- [ ] **Step 1: Add ZK event helper inside the IIFE**

Open `web/js/booking_weekview.js`. After the IIFE opening `(function () {`, add:

```javascript
    // ── ZK event bridge ──────────────────────────────────────────────────────
    function sendZkBookingEvent(eventName, data) {
        var vmContainer = zk.Widget.$('$bookingVMContainer');
        if (vmContainer) {
            zAu.send(new zk.Event(vmContainer, eventName, data));
        } else {
            console.warn('bookingVMContainer not found for event:', eventName);
        }
    }
```

- [ ] **Step 2: Replace onWeekEventDelete — use ZK event**

Find `function onWeekEventDelete(event, id)` (around line 463). Replace:

```javascript
    function onWeekEventDelete(event, id) {
        event.stopPropagation();
        if (confirm('Are you sure you want to delete this booking?')) {
            sendZkBookingEvent('onBookingDelete', String(id));
        }
    }
```

- [ ] **Step 3: Replace openEditDialog (weekview version) — use ZK event**

Find `function openEditDialog(item, callback)` inside the IIFE (around line 573). Replace the entire function:

```javascript
    function openEditDialog(item, callback) {
        var startMs = item.start instanceof Date ? item.start.getTime() : Number(item.start);
        var endMs   = item.end   instanceof Date ? item.end.getTime()   : Number(item.end);
        var json = JSON.stringify({
            's_booking_id':              String(item.id || item.s_booking_id || 0),
            's_resource_id':             String(item.group || 0),
            'booking-name':              item.name || item.content || '',
            'description':               item.description || '',
            'startTimestamp':            String(startMs),
            'endTimestamp':              String(endMs),
            'assign-date-from-timestamp': String(startMs),
            'assign-date-to-timestamp':   String(endMs)
        });
        sendZkBookingEvent('onBookingEdit', json);
        if (callback) callback(item);
    }
```

- [ ] **Step 4: Replace clickNew (weekview version) — use ZK event**

Find `function clickNew(item, callback)` inside the IIFE (around line 522). Replace:

```javascript
    function clickNew(item, callback) {
        var startMs = item.start instanceof Date ? item.start.getTime() : Number(item.start);
        var endMs   = item.end   instanceof Date ? item.end.getTime()   : Number(item.end);
        var resId   = item.group || 0;
        var json = JSON.stringify({
            's_booking_id':              '0',
            's_resource_id':             String(resId),
            'booking-name':              '',
            'description':               '',
            'startTimestamp':            String(startMs),
            'endTimestamp':              String(endMs),
            'assign-date-from-timestamp': String(startMs),
            'assign-date-to-timestamp':   String(endMs)
        });
        sendZkBookingEvent('onBookingAdd', json);
        if (callback) callback(null);
    }
```

- [ ] **Step 5: Commit**

```bash
git add web/js/booking_weekview.js
git commit -m "booking_weekview.js: replace HTML dialog and bookingDeleted/bookingUpdated with ZK event sends"
```

---

## Task 8: Rewrite meetingroom.zul

**Files:**
- Create: `web/zul/meetingroom.zul`

- [ ] **Step 1: Create web/zul/meetingroom.zul**

Create `web/zul/meetingroom.zul` with this complete content:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<zk>
<window id="bookingVMContainer" border="none" width="100%" height="100%"
        style="background-color:green" contentStyle="overflow:auto;"
        viewModel="@id('vm') @init(arg.vm)">

  <!-- Third-party: CDN -->
  <script src="https://cdn.jsdelivr.net/npm/vis-timeline@7/dist/vis-timeline-graph2d.min.js"/>
  <script src="https://cdn.jsdelivr.net/npm/jquery-ui@1.13/dist/jquery-ui.min.js"/>
  <script src="https://cdn.jsdelivr.net/npm/jquery-toast-plugin@1.3.2/dist/jquery.toast.min.js"/>

  <!-- Custom JS: bundle classpath -->
  <script src="~./js/booking.js"/>
  <script src="~./js/booking_weekview.js"/>

  <!-- Third-party styles: CDN -->
  <style src="https://cdn.jsdelivr.net/npm/vis-timeline@7/dist/vis-timeline-graph2d.min.css"/>
  <style src="https://cdn.jsdelivr.net/npm/jquery-ui@1.13/dist/themes/base/jquery-ui.min.css"/>
  <style src="https://cdn.jsdelivr.net/npm/jquery-toast-plugin@1.3.2/dist/jquery.toast.min.css"/>

  <!-- Custom styles: bundle classpath -->
  <style src="~./styles/booking.css"/>

  <!-- Inline week/day view styles (unchanged from original) -->
  <style>
.week-view-root { font-family: 'Roboto', sans-serif; height: 100%; display: flex; flex-direction: column; overflow: hidden; }
.week-header { display: flex; height: 40px; border-bottom: 2px solid #ddd; background: #f5f5f5; flex-shrink: 0; }
.header-time-spacer { width: 60px; min-width: 60px; border-right: 1px solid #eee; background: #f9f9f9; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 10px; line-height: 40px; text-align: center; }
.header-day { flex: 1; text-align: center; line-height: 40px; font-weight: bold; font-size: 13px; color: #444; border-right: 1px solid #eee; }
.scroll-body { flex: 1; overflow-y: auto; position: relative; min-height: 0; display: flex; flex-direction: column; }
.week-layout { display: flex; flex-direction: row; background: white; border: 1px solid #ddd; min-height: 440px; flex: 1; }
.time-col { width: 60px; flex-shrink: 0; background: #f8f9fa; border-right: 1px solid #ddd; }
.time-slot { height: 40px; border-bottom: 1px solid #e0e0e0; font-size: 11px; color: #666; display: flex; align-items: start; justify-content: center; padding-top: 2px; box-sizing: border-box; }
.days-grid { flex: 1; display: flex; position: relative; }
.day-col { flex: 1; border-right: 1px solid #eee; position: relative; height: 100%; background: repeating-linear-gradient(to bottom, transparent 0, transparent 39px, #f5f5f5 40px); cursor: pointer; }
.event-card { position: absolute; font-size: 11px; color: white; border-radius: 3px; padding: 2px 4px; overflow: hidden; box-shadow: 0 1px 2px rgba(0,0,0,0.2); z-index: 10; cursor: pointer; }
.event-card.editable { cursor: grab; }
.event-card.editable:active { cursor: grabbing; }
.drag-ghost { position: absolute; background: rgba(33,150,243,0.6); border: 2px solid #1565C0; box-shadow: 0 4px 8px rgba(0,0,0,0.3); z-index: 100; pointer-events: none; }
.current-time-line { position: absolute; left: 0; width: 100%; border-top: 2px solid red; z-index: 100; pointer-events: none; display: none; }
.drag-tooltip { position: fixed; background: rgba(0,0,0,0.8); color: white; padding: 4px 8px; border-radius: 4px; font-size: 12px; pointer-events: none; z-index: 2147483647; white-space: nowrap; box-shadow: 0 2px 10px rgba(0,0,0,0.5); }
.delete-icon { position: absolute; top: 2px; right: 2px; width: 16px; height: 16px; line-height: 16px; text-align: center; color: rgba(255,255,255,0.7); font-weight: bold; cursor: pointer; border-radius: 50%; z-index: 20; }
.delete-icon:hover { color: #fff; background-color: rgba(255,0,0,0.7); }
.resize-handle { position: absolute; bottom: 0; left: 0; width: 100%; height: 8px; cursor: ns-resize; z-index: 30; }
.event-card.dragging { opacity: 0.5; box-shadow: 0 4px 8px rgba(0,0,0,0.3); z-index: 100 !important; pointer-events: none; }
.input-error { border: 2px solid #D32F2F !important; background-color: #FFEBEE !important; }
.error-text { color: #D32F2F; font-size: 11px; display: block; margin-top: 2px; font-weight: bold; }
.booking-error-label { color: #D32F2F; font-size: 12px; padding: 4px 8px; }
.booking-dialog-footer { text-align: right; padding: 8px; }
.booking-dialog-footer button { margin-left: 6px; }
  </style>

  <!-- Toolbar -->
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
             selectedItem="@bind(vm.selectedResourceType)"
             onChange="@command('refresh')">
      <template name="model" var="rt">
        <listitem label="@load(rt.name)" value="@load(rt)"/>
      </template>
    </listbox>
    <separator bar="true"/>
    <button mold="os" label="Add Booking"
            onClick="@command('openAddDialog')"
            style="background-color:#1976D2;color:black;font-weight:bold;border-radius:4px;box-shadow:0 2px 5px rgba(0,0,0,0.2);"/>
    <separator bar="true"/>
    <button mold="os" label="Week"     onClick="@command('changeViewMode', mode='week')"/>
    <button mold="os" label="Day"      onClick="@command('changeViewMode', mode='day')"/>
    <button mold="os" label="Timeline" onClick="@command('changeViewMode', mode='timeline')"/>
    <separator bar="true"/>
    <button mold="os" label="&lt;"  onClick="@command('prevPeriod')"/>
    <button mold="os" label="Today" onClick="@command('today')"/>
    <button mold="os" label="&gt;"  onClick="@command('nextPeriod')"/>
    <separator bar="true"/>
    <!-- chkWorkHours NOT bound to VM — JS reads it via DOM; id must be preserved -->
    <checkbox id="chkWorkHours" label="Only Work Hours" checked="true"/>
  </toolbar>

  <!-- Error label -->
  <label value="@load(vm.errorMessage)" sclass="booking-error-label"
         visible="@load(not empty vm.errorMessage)"/>

  <!-- Booking view container: HTML component bound to vm.bookingHtml -->
  <div id="bookingContainer" hflex="1" vflex="1">
    <html content="@load(vm.bookingHtml)" hflex="1" vflex="1"/>
  </div>

  <!-- Hidden textboxes for drag-drop/resize (timeline only) -->
  <div style="display:none;">
    <textbox id="dateLast" visible="false"/>
    <textbox id="itemData" visible="false"/>
  </div>

  <!-- Booking dialog: ZK modal replacing native HTML form -->
  <window title="@load(vm.editMode ? '編輯預約' : '新增預約')"
          border="normal" width="500px" mode="modal"
          visible="@load(vm.dialogVisible)"
          closable="true" onClose="@command('closeDialog')">
    <grid>
      <rows>
        <row>
          <label value="Resource"/>
          <label value="@load(vm.draft.resourceName)"/>
        </row>
        <row>
          <label value="Name *"/>
          <textbox value="@bind(vm.draft.name)" width="300px"/>
        </row>
        <row>
          <label value="Memo"/>
          <textbox value="@bind(vm.draft.description)" width="300px" rows="3"/>
        </row>
        <row>
          <label value="Start"/>
          <hlayout>
            <datebox value="@bind(vm.draft.startDate)" format="yyyy-MM-dd" width="130px"/>
            <timebox value="@bind(vm.draft.startTime)" format="HH:mm" width="80px"/>
          </hlayout>
        </row>
        <row>
          <label value="End"/>
          <hlayout>
            <datebox value="@bind(vm.draft.endDate)" format="yyyy-MM-dd" width="130px"/>
            <timebox value="@bind(vm.draft.endTime)" format="HH:mm" width="80px"/>
          </hlayout>
        </row>
        <row>
          <label value="Weekly"/>
          <checkbox checked="@bind(vm.draft.weekly)"/>
        </row>
        <row visible="@load(vm.draft.weekly)">
          <label value="Repeat Until"/>
          <datebox value="@bind(vm.draft.weeklyEndDate)" format="yyyy-MM-dd" width="130px"/>
        </row>
        <row>
          <label value=""/>
          <label value="@load(vm.dialogError)" sclass="error-text"
                 visible="@load(not empty vm.dialogError)"/>
        </row>
      </rows>
    </grid>
    <div sclass="booking-dialog-footer">
      <button label="Save"   onClick="@command('saveBooking')"
              style="background-color:#1976D2;color:white;padding:4px 12px;"/>
      <button label="Delete" onClick="@command('deleteBooking')"
              visible="@load(vm.editMode)"/>
      <button label="Cancel" onClick="@command('closeDialog')"/>
    </div>
  </window>

</window>
</zk>
```

- [ ] **Step 2: Commit**

```bash
git add web/zul/meetingroom.zul
git commit -m "Add meetingroom.zul: MVVM binding, CDN scripts, ZK modal dialog, html content binding"
```

---

## Task 9: Delete Old Files

**Files:**
- Delete: `src/tw/ninniku/booking/form/BookingTimeline.java`
- Delete: `WEB-INF/` directory

- [ ] **Step 1: Delete BookingTimeline.java and WEB-INF/**

```bash
cd /Users/ray/sources/tw.ninniku.booking
git rm src/tw/ninniku/booking/form/BookingTimeline.java
git rm -r WEB-INF/
```

- [ ] **Step 2: Commit**

```bash
git commit -m "Delete BookingTimeline.java (replaced by BookingForm) and WEB-INF/ (replaced by web/)"
```

---

## Self-Review

**Spec coverage check:**

| Spec requirement | Covered by |
|---|---|
| Resource layout: `WEB-INF/web/` → `web/` | Task 1 |
| `Bundle-ClassPath: src/, .` | Task 2 |
| Add MVVM Import-Package entries | Task 2 |
| `build.properties` uses `web/` | Task 2 |
| `BookingVM.java` POJO ViewModel | Task 3 |
| `BookingDraft` inner class with all getters/setters | Task 3 |
| `@Init`, all `@Command` methods | Task 3 |
| `prepareEdit`, `prepareAdd`, `deleteDirectlyById` (non-@Command) | Task 3 |
| `doRefreshView()` with timeline/week/day rendering | Task 3 |
| `validateDraft` added to BookingValidator | Task 3 |
| `BookingForm.java` thin controller (~120 lines) | Task 4 |
| `initForm()` ClassLoader + createComponents + wireComponents | Task 4 |
| `onEvent()` bridges dateLast (drag-drop), onBookingEdit, onBookingAdd, onBookingDelete | Task 4 |
| `@Wire` bookingVMContainer, dateLast, itemData | Task 4 |
| `getViewModel()` via Binder | Task 4 |
| `isWritable()` using `getAdFormId()` stays in controller | Task 4 |
| Drag-drop bug fix: read `"id"` not `"s_booking_id"` from JSON | Task 4 |
| `BookingFormFactory` references `BookingForm` | Task 5 |
| `booking.js`: ZK event sends replace HTML dialog | Task 6 |
| `booking_weekview.js`: ZK event sends replace HTML dialog | Task 7 |
| ZUL: `viewModel="@id('vm') @init(arg.vm)"` (no redundant `apply=`) | Task 8 |
| ZUL: CDN for third-party, `~./js/` for custom JS | Task 8 |
| ZUL toolbar `@command` bindings | Task 8 |
| ZUL resource type listbox `@load`/`@bind` | Task 8 |
| ZUL `<html content="@load(vm.bookingHtml)"/>` | Task 8 |
| ZUL ZK modal dialog bound to `BookingDraft` | Task 8 |
| `chkWorkHours` keeps DOM id for JS | Task 8 |
| Delete `WEB-INF/`, `BookingTimeline.java` | Task 9 |

**Known gap:** `BookingValidator.escapeForHtml()` is called by `BookingVM.renderEventCards()` but may not exist (only `escapeForJs()` is confirmed). Verify `BookingValidator` has `escapeForHtml(String)` before running Task 3 — if missing, add it:

```java
public static String escapeForHtml(String value) {
    if (value == null) return "";
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
}
```

**AD Form record:** After deployment, manually update the Classname in the iDempiere AD Form record from `tw.ninniku.booking.form.BookingTimeline` to `tw.ninniku.booking.form.BookingForm` (or leave as-is — the factory handles both names via the `contains("BookingTimeline")` fallback in Task 5).
