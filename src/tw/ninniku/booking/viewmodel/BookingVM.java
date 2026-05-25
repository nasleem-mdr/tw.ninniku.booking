package tw.ninniku.booking.viewmodel;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MResourceType;
import org.compiere.model.MUser;
import org.compiere.model.Query;
import org.compiere.util.Env;
import org.compiere.util.Msg;
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
    
    // ── Getter (add to BookingVM) ──────────────────────────────────────

    public String getDocActionResult() {
        return docActionResult;
    }

    // ── status ──────────────────────────────────────────────────────────────
    private String errorMessage;

    // ── dialog ──────────────────────────────────────────────────────────────
    private boolean dialogVisible;
    private boolean editMode;
    private String dialogError;
    private BookingDraft draft = new BookingDraft();
    private int selectedResourceIndex = 0;
    private String docActionResult;
    private boolean admin;
    private int userId;
    private Object ctx;          // replace with Properties in real VM
    private int selectedBookingId;  // set from draft.bookingId in real VM

    // ── i18n labels (loaded once from AD_Message at construction time) ────────
    private final Map<String, String> labels = new LinkedHashMap<>();

    // ═══════════════════════════════════════════════════════════════════════
    // BookingDraft inner class
    // ═══════════════════════════════════════════════════════════════════════

    public static class BookingDraft {
        private int bookingId;
        private int sResourceId;
        private String resourceName = "";
        private Group selectedGroup;
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
        public Group getSelectedGroup()                  { return selectedGroup; }
        public void setSelectedGroup(Group g) {
            this.selectedGroup = g;
            if (g != null) this.sResourceId = Integer.parseInt(String.valueOf(g.getId()));
        }
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
            if (date == null) return null;
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

    private void loadLabels() {
        labels.put("booking",      Msg.getMsg(ctx, "BK_Booking"));
        labels.put("refresh",      Msg.getMsg(ctx, "BK_Refresh"));
        labels.put("resource",     Msg.getMsg(ctx, "BK_Resource"));
        labels.put("week",         Msg.getMsg(ctx, "BK_Week"));
        labels.put("day",          Msg.getMsg(ctx, "BK_Day"));
        labels.put("timeline",     Msg.getMsg(ctx, "BK_Timeline"));
        labels.put("today",        Msg.getMsg(ctx, "BK_Today"));
        labels.put("newBooking",   Msg.getMsg(ctx, "BK_NewBooking"));
        labels.put("editBooking",  Msg.getMsg(ctx, "BK_EditBooking"));
        labels.put("name",         Msg.getMsg(ctx, "BK_Name"));
        labels.put("memo",         Msg.getMsg(ctx, "BK_Memo"));
        labels.put("start",        Msg.getMsg(ctx, "BK_Start"));
        labels.put("end",          Msg.getMsg(ctx, "BK_End"));
        labels.put("weekly",       Msg.getMsg(ctx, "BK_Weekly"));
        labels.put("repeatUntil",  Msg.getMsg(ctx, "BK_RepeatUntil"));
        labels.put("save",         Msg.getMsg(ctx, "BK_Save"));
        labels.put("delete",       Msg.getMsg(ctx, "BK_Delete"));
        labels.put("cancel",       Msg.getMsg(ctx, "BK_Cancel"));
    }

    public BookingVM(Properties ctx, boolean isAdmin, int currentUserId) {
        this.ctx = ctx;
        this.isAdmin = isAdmin;
        this.currentUserId = currentUserId;
        this.bookingService = new BookingService(ctx);
        this.currentViewDate = new Timestamp(System.currentTimeMillis());
        loadLabels();
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
    @NotifyChange({"dialogVisible", "editMode", "draft", "dialogError", "selectedResourceIndex"})
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
        draft.setSelectedGroup(findGroupById(draft.getSResourceId()));
        selectedResourceIndex = findResourceIndex(draft.getSResourceId());
        editMode = false;
        dialogError = null;
        dialogVisible = true;
    }

    @Command
    @NotifyChange({"dialogVisible", "dialogError", "bookingHtml", "errorMessage"})
    public void saveBooking() {
        try {
            if (selectedResourceIndex >= 0 && selectedResourceIndex < groups.size()) {
                draft.setSelectedGroup(groups.get(selectedResourceIndex));
            }
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
        draft.setSelectedGroup(findGroupById(resId));
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
        selectedResourceIndex = findResourceIndex(resId);
        editMode = true;
        dialogError = null;
        dialogVisible = true;
        BindUtils.postNotifyChange(null, null, this, "editMode");
        BindUtils.postNotifyChange(null, null, this, "dialogError");
        BindUtils.postNotifyChange(null, null, this, "dialogVisible");
        BindUtils.postNotifyChange(null, null, this, "draft");
        BindUtils.postNotifyChange(null, null, this, "selectedResourceIndex");
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
        draft.setSelectedGroup(findGroupById(resId));
        long startMs = parseLong(json, "assign-date-from-timestamp", 0);
        long endMs   = parseLong(json, "assign-date-to-timestamp", 0);
        java.util.Date start = startMs > 0 ? new java.util.Date(startMs) : new java.util.Date();
        java.util.Date end   = endMs   > 0 ? new java.util.Date(endMs)   : new java.util.Date();
        draft.setStartDate(start);
        draft.setStartTime(start);
        draft.setEndDate(end);
        draft.setEndTime(end);
        selectedResourceIndex = findResourceIndex(resId);
        editMode = false;
        dialogError = null;
        dialogVisible = true;
        BindUtils.postNotifyChange(null, null, this, "editMode");
        BindUtils.postNotifyChange(null, null, this, "dialogError");
        BindUtils.postNotifyChange(null, null, this, "dialogVisible");
        BindUtils.postNotifyChange(null, null, this, "draft");
        BindUtils.postNotifyChange(null, null, this, "selectedResourceIndex");
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

    private int findResourceIndex(int sResourceId) {
        for (int i = 0; i < groups.size(); i++) {
            try {
                if (Integer.parseInt(String.valueOf(groups.get(i).getId())) == sResourceId) return i;
            } catch (NumberFormatException ignore) {}
        }
        return 0;
    }

    private Group findGroupById(int sResourceId) {
        for (Group g : groups) {
            try {
                if (Integer.parseInt(String.valueOf(g.getId())) == sResourceId) return g;
            } catch (NumberFormatException ignore) { }
        }
        return groups.isEmpty() ? null : groups.get(0);
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

        html.append("<div class='days-grid' data-view='week'>");
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

        html.append("<div class='days-grid' data-view='day'>");
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
                String editableClass = isOwnerOrAdmin ? "editable" : "";
                String ownClass = (b.getCreatedBy() == currentUserId) ? "own-booking" : "";
                String resizeHandle = isOwnerOrAdmin ? "<div class='resize-handle'></div>" : "";
                String nameAttr = BookingValidator.escapeForHtml(b.getName() != null ? b.getName() : "");
                String descAttr = BookingValidator.escapeForHtml(b.getDescription() != null ? b.getDescription() : "");
                html.append(String.format(
                        "<div class='event-card %s %s' style='top:%.1fpx;height:%.1fpx;"
                        + "background-color:%s;width:%.1f%%;left:%.1f%%;' "
                        + "data-id='%d' data-resource-id='%d' data-start-ms='%d' data-end-ms='%d' "
                        + "data-booking-name=\"%s\" data-booking-desc=\"%s\">"
                        + "%s%s%s</div>",
                        editableClass, ownClass, top, height, color, width, left,
                        b.getS_ResourceAssignment_ID(), b.getS_Resource_ID(), startMs, endMs,
                        nameAttr, descAttr,
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
        if (assignFrom == null || assignTo == null) {
            throw new BookingValidationException("Start and end date/time are required.");
        }
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
    /**
     * "Submit for Approval" — transitions DR → IP.
     * Bind to a toolbar button visible when DocStatus == DR.
     */
    @Command("submitBooking")
    @NotifyChange({"docActionResult", "errorMessage", "bookingHtml"})
    public void submitBooking() {
        int bookingId = getSelectedBookingId();
        if (bookingId <= 0) { setError("No booking selected."); return; }
        try {
            BookingService svc = new BookingService(getCtx());
            MResourceAssignment ra = svc.processDocAction(
                    bookingId,
                    MResourceAssignment.DOCACTION_Prepare,
                    isAdmin());
            docActionResult = "Booking #" + bookingId + " submitted for approval.";
            clearError();
            doRefreshView();
        } catch (BookingValidationException ex) {
            setError(ex.getMessage());
        } catch (Exception ex) {
            setError("Submit failed: " + ex.getMessage());
        }
    }

    /**
     * "Approve" — admin only, transitions IP → AP.
     * Bind to a button visible when DocStatus == IP AND user is admin.
     */
    @Command("approveBooking")
    @NotifyChange({"docActionResult", "errorMessage", "bookingHtml"})
    public void approveBooking() {
        int bookingId = getSelectedBookingId();
        if (bookingId <= 0) { setError("No booking selected."); return; }
        try {
            BookingService svc = new BookingService(getCtx());
            MResourceAssignment ra = svc.processDocAction(
                    bookingId,
                    MResourceAssignment.DOCACTION_Approve,
                    isAdmin());
            docActionResult = "Booking #" + bookingId + " approved.";
            clearError();
            doRefreshView();
        } catch (BookingValidationException ex) {
            setError(ex.getMessage());
        } catch (Exception ex) {
            setError("Approve failed: " + ex.getMessage());
        }
    }

    /**
     * "Reject / Send back" — admin only, transitions IP → DR.
     */
    @Command("rejectBooking")
    @NotifyChange({"docActionResult", "errorMessage", "bookingHtml"})
    public void rejectBooking() {
        int bookingId = getSelectedBookingId();
        if (bookingId <= 0) { setError("No booking selected."); return; }
        try {
            // rejectIt() moves IP → DR; no dedicated service method needed
            MResourceAssignment ra = new MResourceAssignment(getCtx(), bookingId, null);
            ra.rejectIt();
            ra.saveEx();
            docActionResult = "Booking #" + bookingId + " sent back to requester.";
            clearError();
            doRefreshView();
        } catch (Exception ex) {
            setError("Reject failed: " + ex.getMessage());
        }
    }

    /**
     * "Void" — removes/cancels a booking (soft-delete via DocStatus=VO).
     * Replaces the raw-delete used previously.
     */
    @Command("voidBooking")
    @NotifyChange({"docActionResult", "errorMessage", "bookingHtml"})
    public void voidBooking() {
        int bookingId = getSelectedBookingId();
        if (bookingId <= 0) { setError("No booking selected."); return; }
        try {
            BookingService svc = new BookingService(getCtx());
            svc.deleteBooking(bookingId, isAdmin(), getUserId());
            docActionResult = "Booking #" + bookingId + " voided.";
            clearError();
            doRefreshView();
            closeDialog();
        } catch (BookingValidationException ex) {
            setError(ex.getMessage());
        } catch (Exception ex) {
            setError("Void failed: " + ex.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Getters for ZUL binding
    // ═══════════════════════════════════════════════════════════════════════

    public List<MResourceType> getResourceTypes()          { return resourceTypes; }
    public MResourceType getSelectedResourceType()          { return selectedResourceType; }
    public void setSelectedResourceType(MResourceType v)    { this.selectedResourceType = v; }
    public List<Group> getResources()                       { return groups; }
    public String getViewMode()                             { return viewMode; }
    public String getBookingHtml()                          { return bookingHtml != null ? bookingHtml : ""; }
    public String getErrorMessage()                         { return errorMessage; }
    public void setErrorMessage(String v)                   { this.errorMessage = v; }
    public boolean isDialogVisible()                        { return dialogVisible; }
    public boolean isEditMode()                             { return editMode; }
    public String getDialogError()                          { return dialogError; }
    public BookingDraft getDraft()                          { return draft; }
    public int getSelectedResourceIndex()                   { return selectedResourceIndex; }
    public void setSelectedResourceIndex(int v)             { this.selectedResourceIndex = v; }
    public Map<String, String> getLabels()                  { return labels; }
    
    private int getSelectedBookingId() { return selectedBookingId; }
    private java.util.Properties getCtx() { return (java.util.Properties) ctx; }
    private boolean isAdmin() { return admin; }
    private int getUserId() { return userId; }
    private void setError(String msg) { /* vm.setErrorMessage(msg) */ }
    private void clearError() { /* vm.setErrorMessage(null) */ }
    public void doRefreshView() { /* existing refresh logic */ }
    public void closeDialog() { /* existing close dialog logic */ }

}
