package tw.ninniku.booking.form;

import java.sql.Timestamp;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.webui.event.ValueChangeEvent;
import org.adempiere.webui.event.ValueChangeListener;
import org.adempiere.webui.panel.ADForm;
import org.adempiere.webui.panel.IFormController;

import org.compiere.model.MResourceType;
import org.compiere.model.MUser;
import org.compiere.model.Query;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.json.JSONObject;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;

import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.Button;
import org.zkoss.zul.Div;
import org.zkoss.zul.Html;
import org.zkoss.zul.Listbox;
import org.zkoss.zul.Listcell;
import org.zkoss.zul.Listitem;
import org.zkoss.zul.Textbox;

import com.google.gson.Gson;

import tw.ninniku.booking.model.MResourceAssignment;
import tw.ninniku.timeline.Group;
import tw.ninniku.timeline.Item;

public class BookingTimeline extends ADForm implements IFormController, EventListener<Event>, ValueChangeListener {

	private static final long serialVersionUID = 1L;
	private ArrayList<Group> groups;
	String version = "3.32";
	Textbox dateStart;
	Textbox dateEnd;
	Textbox dateLast;
	Textbox group;
	Textbox bookingID;
	static long timeLast;
	long timeLastLocal;
	private Textbox bookingUpdated;
	private Textbox bookingDeleted;
	private Textbox itemData;
	private Listbox resourceType;

	// View Switching
	private Div bookingContainer;
	private Button btnViewTimeline, btnViewWeek, btnViewDay;
	private Button btnPrev, btnNext, btnToday, btnRefresh, btnAddBooking;
	private org.zkoss.zul.Checkbox chkWorkHours;

	private static final String VIEW_TIMELINE = "TIMELINE";
	private static final String VIEW_WEEK = "WEEK";
	private static final String VIEW_DAY = "DAY";
	private String currentViewMode = VIEW_WEEK;

	private Timestamp currentViewDate; // Represents the start of the view or 'focus' date

	private BookingService bookingService;

	@Override
	public void valueChange(ValueChangeEvent evt) {
	}

	@Override
	public ADForm getForm() {
		return this;
	}

	@Override
	public void onEvent(Event event) throws Exception {
		if (event.getTarget().getId().equals("btnRefresh")) {
			refreshView();
		} else if (event.getTarget() == btnAddBooking) {

			// Round to nearest 00 or 30
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
			long startMs = cal.getTimeInMillis();
			long endMs = startMs + 3600000; // +1 hour

			String cmd = "var msg = 'Debug: '; " + "msg += 'isload=' + (typeof isload) + ', '; "
					+ "msg += 'BookingApp.Timeline=' + (typeof BookingApp !== 'undefined' ? typeof BookingApp.Timeline : 'n/a') + ', '; "
					+ "msg += 'BookingApp.WeekView=' + (typeof BookingApp !== 'undefined' ? typeof BookingApp.WeekView : 'n/a'); "
					+ "console.log(msg); "
					+ "if(BookingApp && BookingApp.WeekView && BookingApp.WeekView.openCustomAddDialogRange){ BookingApp.WeekView.openCustomAddDialogRange("
					+ startMs + "," + endMs
					+ ", 0); } else { console.error('BookingApp.WeekView.openCustomAddDialogRange missing'); alert(msg); }";
			Clients.evalJavaScript(cmd);
		} else if (event.getTarget() == btnViewTimeline) {
			updateViewMode(VIEW_TIMELINE);
		} else if (event.getTarget() == btnViewWeek) {
			updateViewMode(VIEW_WEEK);
		} else if (event.getTarget() == btnViewDay) {
			updateViewMode(VIEW_DAY);
		} else if (event.getTarget() == btnPrev) {
			navigateView(-1);
		} else if (event.getTarget() == btnNext) {
			navigateView(1);
		} else if (event.getTarget() == btnToday) {
			currentViewDate = new Timestamp(System.currentTimeMillis());
			refreshView();
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

		} else if (event.getTarget().getId().equals("resourceType")) {
			renewGroup();
			refreshView();
		} else if (event.getTarget() == chkWorkHours) {
			refreshView();
		}

		super.onEvent(event);
	}

	private void updateViewMode(String mode) {
		this.currentViewMode = mode;
		btnViewTimeline.setDisabled(mode.equals(VIEW_TIMELINE));
		btnViewWeek.setDisabled(mode.equals(VIEW_WEEK));
		btnViewDay.setDisabled(mode.equals(VIEW_DAY));
		refreshView();
	}

	private void navigateView(int direction) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(currentViewDate);

		if (VIEW_WEEK.equals(currentViewMode)) {
			cal.add(Calendar.WEEK_OF_YEAR, direction);
		} else if (VIEW_DAY.equals(currentViewMode)) {
			cal.add(Calendar.DAY_OF_YEAR, direction);
		} else {
			// Timeline default nav (maybe 1 week?)
			cal.add(Calendar.DAY_OF_YEAR, direction * 7);
		}
		currentViewDate = new Timestamp(cal.getTimeInMillis());
		refreshView();
	}

	private void refreshView() {
		bookingContainer.getChildren().clear();

		if (VIEW_TIMELINE.equals(currentViewMode)) {
			// Restore Timeline container
			// <n:div id="booking-chart" height="100%"></n:div>
			// org.zkoss.zhtml.Div div = new org.zkoss.zhtml.Div(); // zhtml.Div corresponds
			// to n:div logic generally or just Div
			// Actually existing code used <n:div xmlns:n="native"> which maps to
			// org.zkoss.zhtml.Div or similar if configured,
			// but here let's just use ZK Div but with the ID expected by existing JS

			// Warning: ZK ID collision if we reuse "booking-chart".
			// But we cleared children.
			// Ideally we want to output <div id="booking-chart">...</div>
			Html html = new Html("<div id='booking-chart' style='height:100%'></div>");
			html.setHflex("1");
			html.setVflex("1");
			bookingContainer.appendChild(html);

			renewItem(100);
			// Re-initialize chart because the DOM element was recreated
			String cmd = "setTimeout(function(){ BookingApp.Timeline.initChart(); }, 200);";
			Clients.evalJavaScript(cmd);
		} else if (VIEW_WEEK.equals(currentViewMode)) {
			renderWeekView();
		} else if (VIEW_DAY.equals(currentViewMode)) {
			renderDayView();
		}
	}

	@Override
	protected void initForm() {
		currentViewDate = new Timestamp(System.currentTimeMillis());
		bookingService = new BookingService(Env.getCtx());

		String zulPath = "~./meetingroom.zul";
		Properties p = Env.getCtx();

		Map<String, String> labels = new HashMap<>();
		if (p.getProperty("#Locale").equalsIgnoreCase("zh_TW")) {
			labels.put("lbSubject", "會議主題");
		} else {
			labels.put("lbSubject", "Meeting Subject:");
		}

		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		Component component = null;
		try {
			Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
			Map<String, Object> args = new HashMap<String, Object>();
			args.put("version", this.version);
			args.put("labels", labels);
			args.put("uuid", UUID.randomUUID().toString());
			component = Executions.createComponents(zulPath, this, args);
		} finally {
			Thread.currentThread().setContextClassLoader(cl);
		}
		// getContextPath()
		btnRefresh = (Button) component.getFellow("btnRefresh");
		btnAddBooking = (Button) component.getFellow("btnAddBooking");
		btnViewTimeline = (Button) component.getFellow("btnViewTimeline");
		btnViewTimeline.setDisabled(false);
		btnViewWeek = (Button) component.getFellow("btnViewWeek");
		btnViewWeek.setDisabled(true);
		btnViewDay = (Button) component.getFellow("btnViewDay");
		btnPrev = (Button) component.getFellow("btnPrev");
		btnNext = (Button) component.getFellow("btnNext");
		btnToday = (Button) component.getFellow("btnToday");

		chkWorkHours = (org.zkoss.zul.Checkbox) component.getFellow("chkWorkHours");
		chkWorkHours.addEventListener(Events.ON_CHECK, this);

		bookingContainer = (Div) component.getFellow("bookingContainer");

		dateStart = (Textbox) component.getFellow("dateStart");
		dateEnd = (Textbox) component.getFellow("dateEnd");
		dateLast = (Textbox) component.getFellow("dateLast");
		group = (Textbox) component.getFellow("group");
		bookingID = (Textbox) component.getFellow("bookingID");
		dateLast.addEventListener(Events.ON_CHANGE, this);
		itemData = (Textbox) component.getFellow("itemData");
		bookingUpdated = (Textbox) component.getFellow("bookingUpdated");
		bookingDeleted = (Textbox) component.getFellow("bookingDeleted");
		resourceType = (Listbox) component.getFellow("resourceType");
		// add item
		addResourceTypeItem();
		resourceType.addEventListener(Events.ON_SELECT, this);
		bookingUpdated.addEventListener(Events.ON_CHANGE, this);
		bookingDeleted.addEventListener(Events.ON_CHANGE, this);
		btnRefresh.addEventListener(Events.ON_CLICK, this);
		btnAddBooking.addEventListener(Events.ON_CLICK, this);

		btnViewTimeline.addEventListener(Events.ON_CLICK, this);
		btnViewWeek.addEventListener(Events.ON_CLICK, this);
		btnViewDay.addEventListener(Events.ON_CLICK, this);
		btnPrev.addEventListener(Events.ON_CLICK, this);
		btnNext.addEventListener(Events.ON_CLICK, this);
		btnToday.addEventListener(Events.ON_CLICK, this);

		renewGroup();

		// Default load
		if (VIEW_TIMELINE.equals(currentViewMode)) {
			renewItem(500);
			String cmd = "setTimeout(function(){ BookingApp.Timeline.initChart(); }, 2000)";
			Clients.evalJavaScript(cmd);
		} else {
			refreshView();
		}
	}

	private void addResourceTypeItem() {
		String whereSql = " AD_Client_ID= ? ";

		List<MResourceType> rosueceTypes = new Query(Env.getCtx(), MResourceType.Table_Name, whereSql, null)
				.setParameters(new Object[] { Integer.valueOf(Env.getCtx().getProperty("#AD_Client_ID")) })
				.setOrderBy(MResourceType.COLUMNNAME_Name).setOnlyActiveRecords(true).list();

		for (MResourceType mResourceType : rosueceTypes) {
			Listitem newItem = new Listitem();

			newItem.setId(String.valueOf(mResourceType.getS_ResourceType_ID()));
			newItem.setValue(String.valueOf(mResourceType.getS_ResourceType_ID()));
			newItem.appendChild(new Listcell(mResourceType.getName()));
			if (mResourceType.getDescription() != null && mResourceType.getDescription().contains("[default]"))
				newItem.setSelected(true);
			resourceType.appendChild(newItem);
		}

	}

	private void renewItem(int delay) {
		String itemJson = getBookingJSON();

		// If custom views, we don't send to vis.js but we might reuse getBookingJSON
		// logic?
		// getBookingJSON() queries 'now() - 2 days' which is hardcoded.
		// We should probably respect currentViewDate if possible, or leave Timeline as
		// is.

		String cmd = "BookingApp.Timeline.setItems(" + itemJson + ");";
		cmd = " setTimeout(function(){" + cmd + "}," + delay + ");";
		// cmd = " jq(document).ready(function () {" +cmd+ " });";
		Clients.evalJavaScript(cmd);

	}

	private String getBookingJSON() {

		boolean isWritable = isWritable();

		ArrayList<Item> list = new ArrayList<Item>();

		// Original logic queries >= val
		// String whereSql = " AssignDateFrom >= (now() - INTERVAL '2 days ') "
		// + "and exists (select 1 from S_Resource where S_Resource_ID =
		// S_ResourceAssignment.S_Resource_ID and S_ResourceType_ID = ?) ";

		// We should keep this for Timeline View as it was.

		String whereSql = " AssignDateFrom >= (now() - INTERVAL '2 days ') "
				+ "and exists (select 1 from S_Resource where S_Resource_ID = S_ResourceAssignment.S_Resource_ID and S_ResourceType_ID = ?) ";

		List<MResourceAssignment> bookings = new Query(Env.getCtx(), MResourceAssignment.Table_Name, whereSql, null)
				.setParameters(new Object[] { Integer.valueOf((String) resourceType.getSelectedItem().getId()) })
				.setOrderBy("S_Resource_ID").setOnlyActiveRecords(true).list();

		for (MResourceAssignment booking : bookings) {
			Item item = new Item(booking.getS_ResourceAssignment_ID());
			item.setStart((Timestamp) booking.getAssignDateFrom());
			item.setEnd((Timestamp) booking.getAssignDateTo());

			if (isWritable || Env.getContextAsInt(Env.getCtx(), "#AD_User_ID") == booking.getCreatedBy()) {
				item.setEditable(true);
			} else {
				item.setEditable(false);
			}

			item.setName(booking.getName());
			item.setDescription(booking.getDescription());
			item.setGroup(booking.getS_Resource_ID());
			item.setContent(booking.getName());
			MUser user = new MUser(Env.getCtx(), booking.getCreatedBy(), null);
			item.setContent("(" + user.getName() + ")<br/>" + item.getContent());
			if (booking.getDescription() != null)
				item.setContent(item.getContent() + "<br/> " + booking.getDescription());
			item.setTitle(item.getContent());
			list.add(item);
		}
		return new Gson().toJson(list);
	}

	// Helper for custom views query
	private List<MResourceAssignment> fetchBookings(Timestamp start, Timestamp end) {
		Listitem item = resourceType.getSelectedItem();
		if (item == null) return new ArrayList<>();
		int resourceTypeId = Integer.parseInt((String) item.getId());
		return bookingService.fetchBookings(resourceTypeId, start, end);
	}

	private void renewGroup() {

		String groupJson = getResourceJSON();

		String cmd = "BookingApp.Timeline.setGroups(" + groupJson + ");";
		Clients.evalJavaScript(cmd);

	}

	private String getResourceJSON() {
		Listitem item = resourceType.getSelectedItem();
		if (item == null) return new Gson().toJson(new ArrayList<Group>());
		int resourceTypeId = Integer.parseInt((String) item.getId());
		groups = (ArrayList<Group>) bookingService.fetchGroups(resourceTypeId);
		return new Gson().toJson(groups);
	}

	private boolean isWritable() {

		Properties p = Env.getCtx();
		String sql = "select isreadwrite from AD_Form_Access where ad_role_id = ? and ad_form_id = ?";
		String reslut = DB.getSQLValueString(null, sql,
				new Object[] { Integer.valueOf(p.getProperty("#AD_Role_ID")), getAdFormId() });
		return "Y".equals(reslut);
	}

	// --- Custom View Rendering ---

	private void renderWeekView() {
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

		Map<Integer, String> resourceNameMap = buildResourceNameMap();

		List<MResourceAssignment> bookings = fetchBookings(start, end);

		StringBuilder html = new StringBuilder();

		// Determine view range based on toggle
		boolean workHoursOnly = chkWorkHours != null && chkWorkHours.isChecked();
		int startHour = workHoursOnly ? 8 : 0;
		int endHour = workHoursOnly ? 18 : 23;
		int hourCount = endHour - startHour + 1;
		int pxPerHour = 40;
		int heightPx = hourCount * pxPerHour;

		html.append("<div class='week-view-root' data-start-hour='" + startHour + "'>");

		// Header
		html.append("<div class='week-header'>");
		html.append("<div class='header-time-spacer'></div>");

		SimpleDateFormat sdfDay = new SimpleDateFormat("EEE MM/dd");
		SimpleDateFormat sdfKey = new SimpleDateFormat("yyyy-MM-dd");
		cal.setTime(start);
		List<String> dayKeys = new ArrayList<String>();
		for (int i = 0; i < 5; i++) {
			html.append("<div class='header-day'>").append(sdfDay.format(cal.getTime())).append("</div>");
			dayKeys.add(sdfKey.format(cal.getTime()));
			cal.add(Calendar.DAY_OF_YEAR, 1);
		}

		html.append("</div>"); // End Header

		html.append("<div class='scroll-body'>");
		html.append("<div class='week-layout' style='min-height: " + heightPx + "px;'>");

		// Time Column
		html.append("<div class='time-col'>");
		for (int i = startHour; i <= endHour; i++) { // Limit range
			html.append("<div class='time-slot'>").append(String.format("%02d:00", i)).append("</div>");
		}
		html.append("</div>");

		// Days Grid
		html.append("<div class='days-grid'>");
		for (String dayKey : dayKeys) {
			String defaultResId = (groups != null && groups.size() > 0 ? groups.get(0).getId().toString() : "");
			html.append("<div class='day-col' data-date='" + dayKey + "' data-resource-id='" + defaultResId
					+ "' style='height: " + heightPx + "px;'>");

			// 1. Filter events for this day
			List<MResourceAssignment> dayEvents = new ArrayList<>();
			for (MResourceAssignment b : bookings) {
				String dayStr = sdfKey.format(b.getAssignDateFrom());
				if (dayStr.equals(dayKey)) {
					dayEvents.add(b);
				}
			}

			List<List<MResourceAssignment>> columns = sortAndPackEvents(dayEvents);

			renderEventCards(html, columns, startHour, resourceNameMap);

			html.append("</div>");
		}

		appendViewFooter(html);
	}

	private void renderDayView() {
		Calendar cal = Calendar.getInstance();
		cal.setTime(currentViewDate);

		// Set to midnight of the current day
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		Timestamp start = new Timestamp(cal.getTimeInMillis());

		cal.add(Calendar.DAY_OF_YEAR, 1);
		Timestamp end = new Timestamp(cal.getTimeInMillis());

		Map<Integer, String> resourceNameMap = buildResourceNameMap();

		List<MResourceAssignment> bookings = fetchBookings(start, end);

		StringBuilder html = new StringBuilder();

		// Determine view range based on toggle
		boolean workHoursOnly = chkWorkHours != null && chkWorkHours.isChecked();
		int startHour = workHoursOnly ? 8 : 0;
		int endHour = workHoursOnly ? 18 : 23;
		int hourCount = endHour - startHour + 1;
		int pxPerHour = 40;
		int heightPx = hourCount * pxPerHour;

		html.append("<div class='week-view-root' data-start-hour='" + startHour + "'>");

		// Date title row
		SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy/MM/dd (EEE)");
		cal.setTime(start);
		html.append("<div style='text-align:center; font-size:15px; font-weight:bold; padding:6px 0; background:#f5f5f5; border-bottom:1px solid #ddd; flex-shrink:0;'>")
				.append(sdfDate.format(cal.getTime()))
				.append("</div>");

		// Header — resource names
		html.append("<div class='week-header'>");
		html.append("<div class='header-time-spacer'></div>");

		// One header column per resource (room)
		SimpleDateFormat sdfKey = new SimpleDateFormat("yyyy-MM-dd");
		String dayKey = sdfKey.format(start);

		if (groups != null) {
			for (Group g : groups) {
				int resId = Integer.valueOf(g.getId());
				String color = getResourceColor(resId);
				html.append("<div class='header-day' style='color:").append(color).append(";'>")
						.append(g.getContent())
						.append("</div>");
			}
		}

		html.append("</div>"); // End Header

		html.append("<div class='scroll-body'>");
		html.append("<div class='week-layout' style='min-height: " + heightPx + "px;'>");

		// Time Column
		html.append("<div class='time-col'>");
		for (int i = startHour; i <= endHour; i++) {
			html.append("<div class='time-slot'>").append(String.format("%02d:00", i)).append("</div>");
		}
		html.append("</div>");

		// Days Grid — one column per resource
		html.append("<div class='days-grid'>");

		if (groups != null) {
			for (Group g : groups) {
				int resourceId = Integer.valueOf(g.getId());
				html.append("<div class='day-col' data-date='" + dayKey + "' data-resource-id='" + resourceId
						+ "' style='height: " + heightPx + "px;'>");

				// 1. Filter bookings for this resource
				List<MResourceAssignment> resEvents = new ArrayList<>();
				for (MResourceAssignment b : bookings) {
					if (b.getS_Resource_ID() == resourceId) {
						resEvents.add(b);
					}
				}

				List<List<MResourceAssignment>> columns = sortAndPackEvents(resEvents);

				renderEventCards(html, columns, startHour, resourceNameMap);

				html.append("</div>"); // day-col
			}
		}

		appendViewFooter(html);
	}

	private void renderEventCards(StringBuilder html, List<List<MResourceAssignment>> columns, int startHour,
			Map<Integer, String> resourceNameMap) {
		int numCols = columns.size();
		double colWidthPercent = 95.0 / (numCols > 0 ? numCols : 1);

		boolean canWrite = isWritable();
		int adUserId = Env.getContextAsInt(Env.getCtx(), "#AD_User_ID");

		for (int colIndex = 0; colIndex < numCols; colIndex++) {
			List<MResourceAssignment> col = columns.get(colIndex);
			for (MResourceAssignment b : col) {
				long startMs = b.getAssignDateFrom().getTime();
				long endMs = b.getAssignDateTo().getTime();

				Calendar dayStart = Calendar.getInstance();
				dayStart.setTime(b.getAssignDateFrom());
				dayStart.set(Calendar.HOUR_OF_DAY, startHour);
				dayStart.set(Calendar.MINUTE, 0);
				dayStart.set(Calendar.SECOND, 0);

				long offsetMs = startMs - dayStart.getTimeInMillis();
				long durationMs = endMs - startMs;

				double pxPerMin = 40.0 / 60.0;
				double top = (offsetMs / 60000.0) * pxPerMin;
				double height = (durationMs / 60000.0) * pxPerMin;
				if (height < 15)
					height = 15;

				double left = 2.0 + (colIndex * colWidthPercent);
				double width = colWidthPercent - 2.0;

				MUser user = new MUser(Env.getCtx(), b.getCreatedBy(), null);

				String resName = resourceNameMap.getOrDefault(b.getS_Resource_ID(), "");
				if (!resName.isEmpty())
					resName = "[" + resName + "] ";
				String title = resName + b.getName() + " (" + user.getName() + ")";

				String color = getResourceColor(b.getS_Resource_ID());
				String displayContent = title;
				if (b.getDescription() != null && !b.getDescription().isEmpty()) {
					displayContent += "<br/><span style='font-size:10px; opacity:0.9;'>" + b.getDescription()
							+ "</span>";
				}

				boolean isOwnerOrAdmin = canWrite || b.getCreatedBy() == adUserId;
				String deleteIconHtml = "";
				if (isOwnerOrAdmin) {
					deleteIconHtml = String.format(
							"<span class='delete-icon' onclick='BookingApp.WeekView.onWeekEventDelete(event, %d)'>&times;</span>",
							b.getS_ResourceAssignment_ID());
				}

				String nameJS = BookingValidator.escapeForJs(b.getName());
				String descJS = BookingValidator.escapeForJs(b.getDescription());

				String editableClass = isOwnerOrAdmin ? "editable" : "";
				String resizeHandleHtml = isOwnerOrAdmin ? "<div class='resize-handle'></div>" : "";

				html.append(String.format(
						"<div class='event-card %s' style='top:%.1fpx; height:%.1fpx; background-color:%s; width:%.1f%%; left:%.1f%%;' "
								+ "data-id='%d' data-resource-id='%d' data-start-ms='%d' data-end-ms='%d' "
								+ "onclick=\"BookingApp.WeekView.onWeekEventClick(event, '%s', '%s', '%s', '%s', %s, %s);\">"
								+ "%s%s%s</div>",
						editableClass, top, height, color, width, left,
						b.getS_ResourceAssignment_ID(), b.getS_Resource_ID(), startMs, endMs,
						b.getS_ResourceAssignment_ID(), nameJS, descJS,
						b.getS_Resource_ID(), startMs, endMs, displayContent, deleteIconHtml, resizeHandleHtml));
			}
		}
	}

	private void appendViewFooter(StringBuilder html) {
		html.append("<div class='current-time-line'></div>");
		html.append("</div>"); // days-grid
		html.append("</div>"); // week-layout
		html.append("</div>"); // scroll-body
		html.append("</div>"); // root

		Html zkHtml = new Html();
		zkHtml.setHflex("1");
		zkHtml.setVflex("1");
		zkHtml.setContent(html.toString());
		bookingContainer.appendChild(zkHtml);
	}

	private List<List<MResourceAssignment>> sortAndPackEvents(List<MResourceAssignment> events) {
		Collections.sort(events, new Comparator<MResourceAssignment>() {
			public int compare(MResourceAssignment o1, MResourceAssignment o2) {
				int val = o1.getAssignDateFrom().compareTo(o2.getAssignDateFrom());
				if (val == 0)
					return o2.getAssignDateTo().compareTo(o1.getAssignDateTo());
				return val;
			}
		});

		List<List<MResourceAssignment>> columns = new ArrayList<>();
		for (MResourceAssignment evt : events) {
			boolean placed = false;
			for (List<MResourceAssignment> col : columns) {
				MResourceAssignment last = col.get(col.size() - 1);
				if (evt.getAssignDateFrom().getTime() >= last.getAssignDateTo().getTime()) {
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
		if (groups != null) {
			for (Group g : groups) {
				try {
					map.put(Integer.valueOf(g.getId()), g.getContent());
				} catch (NumberFormatException e) {
					// Ignore invalid IDs
				}
			}
		}
		return map;
	}

	private String getResourceColor(int resourceId) {
		// Palette of pleasing colors (Material Design / Google Calendar style)
		String[] colors = {
				"#C62828", "#AD1457", "#6A1B9A", "#4527A0", "#283593",
				"#1565C0", "#0277BD", "#00838F", "#00695C", "#2E7D32",
				"#558B2F", "#9E9D24", "#F9A825", "#FF8F00", "#EF6C00",
				"#D84315", "#4E342E", "#424242", "#37474F"
		};
		return colors[Math.abs(resourceId) % colors.length];
	}
}
