package tw.ninniku.booking.form;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
import org.adempiere.webui.theme.ThemeManager;
import org.compiere.model.MResourceType;
import org.compiere.model.MUser;
import org.compiere.model.Query;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Trx;
import org.json.JSONObject;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.http.ExecutionImpl;
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
	String version = "3.20";
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
	private Button btnViewTimeline, btnViewWeek, btnViewMonth;
	private Button btnPrev, btnNext, btnToday, btnRefresh, btnAddBooking;
	private org.zkoss.zul.Checkbox chkWorkHours;

	private static final String VIEW_TIMELINE = "TIMELINE";
	private static final String VIEW_WEEK = "WEEK";
	private static final String VIEW_MONTH = "MONTH";
	private String currentViewMode = VIEW_WEEK;

	private Timestamp currentViewDate; // Represents the start of the view or 'focus' date

	public String errorMessage = "";

	@Override
	public void valueChange(ValueChangeEvent evt) {
		// TODO Auto-generated method stub

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
					+ "msg += 'initChart=' + (typeof initChart) + ', '; "
					+ "msg += 'openOld=' + (typeof window.openCustomAddDialog) + ', '; "
					+ "msg += 'openRange=' + (typeof window.openCustomAddDialogRange); "
					+ "console.log(msg); "
					+ "if(window.openCustomAddDialogRange){ window.openCustomAddDialogRange("
					+ startMs + "," + endMs
					+ ", 0); } else { console.error('openCustomAddDialogRange missing'); alert(msg); }";
			Clients.evalJavaScript(cmd);
		} else if (event.getTarget() == btnViewTimeline) {
			updateViewMode(VIEW_TIMELINE);
		} else if (event.getTarget() == btnViewWeek) {
			updateViewMode(VIEW_WEEK);
		} else if (event.getTarget() == btnViewMonth) {
			updateViewMode(VIEW_MONTH);
		} else if (event.getTarget() == btnPrev) {
			navigateView(-1);
		} else if (event.getTarget() == btnNext) {
			navigateView(1);
		} else if (event.getTarget() == btnToday) {
			currentViewDate = new Timestamp(System.currentTimeMillis());
			refreshView();
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

		} else if (event.getTarget().getId().equals("bookingUpdated")) {
			// MQTT.thread(new MQTT.TimelineProducer("TIMELINE", "RAY"), false);

			JSONObject json = new JSONObject(itemData.getValue());
			if (!updateBooking(json)) {
				Clients.showNotification(errorMessage);
			}
			refreshView();
		} else if (event.getTarget().getId().equals("bookingDeleted")) {
			JSONObject json = new JSONObject(itemData.getValue());

			if (!deleteBooking(json)) {

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
		btnViewMonth.setDisabled(mode.equals(VIEW_MONTH));
		refreshView();
	}

	private void navigateView(int direction) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(currentViewDate);

		if (VIEW_WEEK.equals(currentViewMode)) {
			cal.add(Calendar.WEEK_OF_YEAR, direction);
		} else if (VIEW_MONTH.equals(currentViewMode)) {
			cal.add(Calendar.MONTH, direction);
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
			String cmd = "setTimeout(function(){ initChart(); }, 200);";
			Clients.evalJavaScript(cmd);
		} else if (VIEW_WEEK.equals(currentViewMode)) {
			renderWeekView();
		} else if (VIEW_MONTH.equals(currentViewMode)) {
			renderMonthView();
		}
	}

	private boolean deleteBooking(JSONObject json) {

		if (!isInteger((String) json.get("id")))
			return false;

		int id = Integer.valueOf((String) json.get("id"));
		if (id > 0) {
			MResourceAssignment booking = new MResourceAssignment(Env.getCtx(), id, null);
			booking.delete(true);
			return true;
		}
		return false;
	}

	public static boolean isInteger(String str) {
		return str.matches("-?\\d+");
	}

	private boolean updateBooking(JSONObject json) {

		int id = 0;
		if (json.has("s_booking_id")) {
			Object val = json.get("s_booking_id");
			if (val != null && !val.toString().isEmpty()) {
				try {
					id = Integer.valueOf(val.toString());
				} catch (NumberFormatException e) {
					// Invalid format, use 0
				}
			}
		}

		Trx trx = Trx.get(Trx.createTrxName(), true); // Create a new transaction
		boolean ok = false;
		try {
			// Start the transaction
			trx.start();

			MResourceAssignment booking = new MResourceAssignment(Env.getCtx(), id, trx.getTrxName());
			String name = json.optString("booking-name", "").trim();
			String description = json.optString("description", "").trim();

			if (name.isEmpty()) {
				errorMessage = "Subject (Name) is required.";
				return false;
			}

			booking.setDescription(description);
			booking.setName(name);
			booking.setS_Resource_ID(Integer.valueOf(json.get("s_resource_id").toString()));
			booking.setAssignDateFrom(new Timestamp(Long.valueOf((String) json.get("assign-date-from-timestamp"))));
			booking.setAssignDateTo(new Timestamp(Long.valueOf((String) json.get("assign-date-to-timestamp"))));

			// Failsafe: Ensure Org is set
			if (booking.getAD_Org_ID() == 0) {
				booking.setAD_Org_ID(Env.getAD_Org_ID(Env.getCtx()));
			}

			// Validate Resource is Active/Correct? (Add logic if needed)

			ok = booking.save(trx.getTrxName());
			if (!ok) {
				errorMessage = "時間重疊:" + booking.getAssignDateFrom().toString();
				errorMessage += " - " + booking.getAssignDateTo().toString();
				return ok;
			}

			if (id == 0 && json.has("is-weekly")) {
				boolean isWeekly = json.get("is-weekly").toString().equals("Y");

				if (!isWeekly)
					return ok;

				Timestamp repeatTo = new Timestamp(Long.valueOf((String) json.get("repeat-date-to-timestamp")));
				Calendar calendarFrom = Calendar.getInstance();
				Calendar calendarTo = Calendar.getInstance();
				Calendar calendarEnd = Calendar.getInstance();

				calendarFrom.setTime(booking.getAssignDateFrom());
				calendarFrom.add(Calendar.DAY_OF_MONTH, 7);

				calendarTo.setTime(booking.getAssignDateTo());
				calendarTo.add(Calendar.DAY_OF_MONTH, 7);

				calendarEnd.setTime(repeatTo);

				while (calendarFrom.before(calendarEnd)) {

					booking = new MResourceAssignment(Env.getCtx(), 0, trx.getTrxName());
					booking.setDescription(description);
					booking.setName(name);
					booking.setS_Resource_ID(Integer.valueOf(json.get("s_resource_id").toString()));

					booking.setAssignDateFrom(new Timestamp(calendarFrom.getTimeInMillis()));
					booking.setAssignDateTo(new Timestamp(calendarTo.getTimeInMillis()));

					if (booking.getAD_Org_ID() == 0) {
						booking.setAD_Org_ID(Env.getAD_Org_ID(Env.getCtx()));
					}

					if (!booking.save(trx.getTrxName())) {
						errorMessage = "時間重疊:" + booking.getAssignDateFrom().toString();
						errorMessage += " - " + booking.getAssignDateTo().toString();
						throw new AdempiereException();
					}

					calendarFrom.add(Calendar.DAY_OF_MONTH, 7);
					calendarTo.add(Calendar.DAY_OF_MONTH, 7);
				}

			}

			trx.commit();

			// Print a message indicating a successful commit
			trx.commit();
		} catch (Exception e) {
			ok = false;
			errorMessage = "Error saving booking: " + e.getMessage();
			trx.rollback();
		} finally {
			// Close the transaction
			trx.close();
			return ok;
		}
	}

	private Timestamp convertTimestamp(String dateString) {
		LocalDateTime localDateTime = LocalDateTime.parse(dateString);

		// Convert LocalDateTime to a timestamp in milliseconds
		long timestamp = localDateTime.toInstant(ZoneOffset.ofHours(8)).toEpochMilli();

		return new Timestamp(timestamp);
	}

	private boolean updateBooking(int s_Booking_ID, int groupID, Timestamp ds, Timestamp de) {
		if (s_Booking_ID <= 0)
			return false; // Cannot update a non-existent booking, prevents NULL Name error on insert

		MResourceAssignment booking = new MResourceAssignment(Env.getCtx(), s_Booking_ID, null);

		booking.setAssignDateFrom(ds);
		booking.setAssignDateTo(de);
		booking.setS_Resource_ID(groupID);
		return booking.save();

	}

	@Override
	protected void initForm() {
		currentViewDate = new Timestamp(System.currentTimeMillis());

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
		btnViewMonth = (Button) component.getFellow("btnViewMonth");
		btnPrev = (Button) component.getFellow("btnPrev");
		btnNext = (Button) component.getFellow("btnNext");
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
		btnViewMonth.addEventListener(Events.ON_CLICK, this);
		btnPrev.addEventListener(Events.ON_CLICK, this);
		btnNext.addEventListener(Events.ON_CLICK, this);
		btnToday.addEventListener(Events.ON_CLICK, this);

		renewGroup();

		// Default load
		if (VIEW_TIMELINE.equals(currentViewMode)) {
			renewItem(500);
			String cmd = "setTimeout(function(){" + "initChart();" + " }, 2000)";
			Clients.evalJavaScript(cmd);
		} else {
			refreshView();
		}
	}

	private void addResourceTypeItem() {
		ArrayList<Item> list = new ArrayList<Item>();

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

		String cmd = "items = new vis.DataSet(" + itemJson + ");";
		cmd = " setTimeout(function(){" + cmd + "}," + delay + ");";
		// cmd = " jq(document).ready(function () {" +cmd+ " });";
		Clients.evalJavaScript(cmd);

	}

	private void draw(int delay) {

		String cmd = " setTimeout(function(){ drawChart();}," + delay + ");";
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
		String whereSql = " AssignDateFrom >= ? AND AssignDateFrom <= ? "
				+ "and exists (select 1 from S_Resource where S_Resource_ID = S_ResourceAssignment.S_Resource_ID and S_ResourceType_ID = ?) ";

		return new Query(Env.getCtx(), MResourceAssignment.Table_Name, whereSql, null)
				.setParameters(
						new Object[] { start, end, Integer.valueOf((String) resourceType.getSelectedItem().getId()) })
				.setOrderBy("S_Resource_ID").setOnlyActiveRecords(true).list();
	}

	private void renewGroup() {

		String groupJson = getResourceJSON();

		String cmd = "groups= " + groupJson + ";";
		Clients.evalJavaScript(cmd);

	}

	private String getResourceJSON() {
		ArrayList<Group> list = new ArrayList<Group>();

		String sql = "select * from s_resource where s_resourcetype_id = ?  ";
		Listitem item = resourceType.getSelectedItem();
		if (item == null)
			return new Gson().toJson(list);
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			pstmt = DB.prepareStatement(sql, null);
			pstmt.setInt(1, Integer.valueOf((String) item.getId()));
			rs = pstmt.executeQuery();
			while (rs.next()) {

				String code = rs.getString("value");
				int id = rs.getInt("s_resource_id");
				String name = rs.getString("name");
				// Use name as content if available, code is value
				list.add(new Group(id, name));
			}
		} catch (SQLException ex) {
			throw new AdempiereException("Unable to load resource", ex);
		} finally {
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}
		groups = list;

		return new Gson().toJson(list);
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

		cal.add(Calendar.DAY_OF_YEAR, 7);
		Timestamp end = new Timestamp(cal.getTimeInMillis());

		// Initialize resource name map
		Map<Integer, String> resourceNameMap = new HashMap<>();
		if (groups != null) {
			for (Group g : groups) {
				try {
					resourceNameMap.put(Integer.valueOf(g.getId()), g.getContent());
				} catch (NumberFormatException e) {
					// Ignore invalid IDs
				}
			}
		}

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
		for (int i = 0; i < 7; i++) {
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

			// 2. Sort by start time, then end time
			Collections.sort(dayEvents, new Comparator<MResourceAssignment>() {
				public int compare(MResourceAssignment o1, MResourceAssignment o2) {
					int val = o1.getAssignDateFrom().compareTo(o2.getAssignDateFrom());
					if (val == 0)
						return o2.getAssignDateTo().compareTo(o1.getAssignDateTo());
					return val;
				}
			});

			// 3. Pack into columns (simple greedy algorithm)
			List<List<MResourceAssignment>> columns = new ArrayList<>();
			for (MResourceAssignment evt : dayEvents) {
				boolean placed = false;
				for (List<MResourceAssignment> col : columns) {
					MResourceAssignment last = col.get(col.size() - 1);
					// Check if evt starts after last event ends (>=)
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

			// 4. Render columns
			int numCols = columns.size();
			double colWidthPercent = 95.0 / (numCols > 0 ? numCols : 1);

			boolean canWrite = isWritable();
			int adUserId = Env.getContextAsInt(Env.getCtx(), "#AD_User_ID");

			for (int colIndex = 0; colIndex < numCols; colIndex++) {
				List<MResourceAssignment> col = columns.get(colIndex);
				for (MResourceAssignment b : col) {
					long startMs = b.getAssignDateFrom().getTime();
					long endMs = b.getAssignDateTo().getTime();

					// Calculate offset from day start 00:00
					Calendar dayStart = Calendar.getInstance();
					dayStart.setTime(b.getAssignDateFrom());
					dayStart.set(Calendar.HOUR_OF_DAY, startHour); // Base is dynamic
					dayStart.set(Calendar.MINUTE, 0);
					dayStart.set(Calendar.SECOND, 0);

					// If event starts before startHour, we clamp it visually or just let it be
					// negative
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

					// Escape single quotes in strings to avoid JS errors
					// Encode strictly for JS string
					String color = getResourceColor(b.getS_Resource_ID());
					String displayContent = title;
					if (b.getDescription() != null && !b.getDescription().isEmpty()) {
						displayContent += "<br/><span style='font-size:10px; opacity:0.9;'>" + b.getDescription()
								+ "</span>";
					}

					// Deletion Logic (reuses the same permission for now, or can be distinct)
					boolean isOwnerOrAdmin = canWrite || b.getCreatedBy() == adUserId;
					String deleteIconHtml = "";
					if (isOwnerOrAdmin) {
						deleteIconHtml = String.format(
								"<span class='delete-icon' onclick='window.onWeekEventDelete(event, %d)'>&times;</span>",
								b.getS_ResourceAssignment_ID());
					}

					// Encode strictly for JS string
					String nameJS = b.getName().replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"");
					String descJS = "";
					if (b.getDescription() != null) {
						descJS = b.getDescription().replace("\r", "").replace("\n", " ").replace("\\", "\\\\")
								.replace("'", "\\'").replace("\"", "\\\"");
					}

					String editableClass = isOwnerOrAdmin ? "editable" : "";
					String resizeHandleHtml = isOwnerOrAdmin ? "<div class='resize-handle'></div>" : "";

					html.append(String.format(
							"<div class='event-card %s' style='top:%.1fpx; height:%.1fpx; background-color:%s; width:%.1f%%; left:%.1f%%;' "
									+ "data-id='%d' data-resource-id='%d' data-start-ms='%d' data-end-ms='%d' "
									+ "onclick=\"onWeekEventClick(event, '%s', '%s', '%s', '%s', %s, %s);\">"
									+ "%s%s%s</div>", // %s for resize handle
							editableClass, top, height, color, width, left,
							b.getS_ResourceAssignment_ID(), b.getS_Resource_ID(), startMs, endMs,
							b.getS_ResourceAssignment_ID(), nameJS, descJS,
							b.getS_Resource_ID(), startMs, endMs, displayContent, deleteIconHtml, resizeHandleHtml));
				}
			}

			html.append("</div>");
		}
		html.append("</div>"); // days-grid
		html.append("</div>"); // week-layout
		html.append("</div>"); // scroll-body
		html.append("</div>"); // root

		Html zkHtml = new Html();
		zkHtml.setHflex("1");
		zkHtml.setVflex("1");
		zkHtml.setContent(html.toString());
		bookingContainer.appendChild(zkHtml);

		// triggers client rendering
		// Clients.evalJavaScript("setTimeout(function(){ if(window.weekViewScrollTo8Am)
		// window.weekViewScrollTo8Am(); }, 100);");
	}

	private void renderMonthView() {
		Calendar cal = Calendar.getInstance();
		cal.setTime(currentViewDate);

		cal.set(Calendar.DAY_OF_MONTH, 1);
		int currentMonth = cal.get(Calendar.MONTH);
		cal.setFirstDayOfWeek(Calendar.MONDAY);
		cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
		// Backtrack to start of week can go to prev month
		if (cal.get(Calendar.MONTH) == currentMonth && cal.get(Calendar.DAY_OF_MONTH) > 1) {
			cal.add(Calendar.DATE, -7); // Safety
		}
		// Actually typical logic: set to 1st, then while day != Monday subtract 1
		cal.setTime(currentViewDate);
		cal.set(Calendar.DAY_OF_MONTH, 1);
		while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
			cal.add(Calendar.DATE, -1);
		}
		Timestamp start = new Timestamp(cal.getTimeInMillis());

		// End date: 6 weeks (42 days) from start is usually enough to cover
		cal.add(Calendar.DATE, 35); // 5 rows
		// Check if we cover the whole month
		Calendar test = Calendar.getInstance();
		test.setTime(currentViewDate);
		test.set(Calendar.DAY_OF_MONTH, 1);
		test.add(Calendar.MONTH, 1);
		test.add(Calendar.DATE, -1); // Last day of month
		if (test.getTimeInMillis() > cal.getTimeInMillis()) {
			cal.add(Calendar.DATE, 7); // add one more week
		}
		Timestamp end = new Timestamp(cal.getTimeInMillis());

		// Initialize resource name map
		Map<Integer, String> resourceNameMap = new HashMap<>();
		if (groups != null) {
			for (Group g : groups) {
				try {
					resourceNameMap.put(Integer.valueOf(g.getId()), g.getContent());
				} catch (NumberFormatException e) {
					// Ignore invalid IDs
				}
			}
		}

		List<MResourceAssignment> bookings = fetchBookings(start, end);

		StringBuilder html = new StringBuilder();

		html.append("<style>");
		html.append(
				".month-view-root { font-family: 'Roboto', sans-serif; background: transparent; padding: 0; margin: 0; height: 100%; display: flex; flex-direction: column; }");
		html.append(
				".month-container { display: flex; flex-direction: column; height: 100%; border: 1px solid #ddd; background: white; flex: 1; }");
		html.append(
				".month-header-row { display: flex; height: 30px; background: #f5f5f5; border-bottom: 1px solid #ddd; flex-shrink: 0; }");
		html.append(
				".month-day-header { flex: 1; text-align: center; line-height: 30px; font-weight: bold; color: #666; font-size: 13px; border-right: 1px solid #eee; }");
		html.append(
				".month-grid { display: flex; flex: 1; flex-wrap: wrap; overflow-y: auto; align-content: flex-start; }");
		html.append(
				".month-cell { width: 14.28%; height: 20%; min-height: 100px; border-right: 1px solid #eee; border-bottom: 1px solid #eee; box-sizing: border-box; padding: 4px; overflow-y: auto; cursor: pointer; }");
		html.append(".date-label { font-size: 12px; font-weight: bold; color: #333; margin-bottom: 4px; }");
		html.append(".date-label.other-month { color: #ccc; }");
		html.append(
				".m-event { font-size: 10px; margin-bottom: 2px; padding: 1px 3px; border-radius: 2px; color: white; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; cursor: pointer; }");
		html.append("</style>");

		html.append("<div class='month-view-root'>");
		html.append("<div class='month-container'>");

		// Header
		html.append("<div class='month-header-row'>");
		String[] days = { "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun" };
		for (String d : days)
			html.append("<div class='month-day-header'>").append(d).append("</div>");
		html.append("</div>");

		// Grid
		html.append("<div class='month-grid'>");

		cal.setTime(start);
		SimpleDateFormat sdfKey = new SimpleDateFormat("yyyy-MM-dd");

		while (cal.getTimeInMillis() < end.getTime()) {
			String dayKey = sdfKey.format(cal.getTime());
			boolean isOther = cal.get(Calendar.MONTH) != currentMonth;

			html.append("<div class='month-cell' onclick=\"window.openCustomAddDialog('" + dayKey + "', 540, '"
					+ (groups != null && groups.size() > 0 ? groups.get(0).getId() : "") + "');\">");
			html.append("<div class='date-label").append(isOther ? " other-month" : "").append("'>")
					.append(cal.get(Calendar.DAY_OF_MONTH)).append("</div>");

			// Render events
			for (MResourceAssignment b : bookings) {
				// Simplified whole-day check or start-day check
				// This logic doesn't handle multi-day events well visually (repeats them), but
				// good enough for MVP
				// Check if b intersects with this day
				long dayStart = cal.getTimeInMillis();
				long dayEnd = dayStart + 86400000L;
				if (b.getAssignDateFrom().getTime() < dayEnd && b.getAssignDateTo().getTime() > dayStart) {
					MUser user = new MUser(Env.getCtx(), b.getCreatedBy(), null);
					String nameEsc = b.getName().replace("'", "\\'");
					String descEsc = (b.getDescription() != null
							? b.getDescription().replace("\n", " ").replace("'", "\\'")
							: "");
					String color = getResourceColor(b.getS_Resource_ID());

					String resName = resourceNameMap.getOrDefault(b.getS_Resource_ID(), "");
					if (!resName.isEmpty())
						resName = "[" + resName + "] ";

					SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm");
					String timeRange = sdfTime.format(b.getAssignDateFrom()) + " - "
							+ sdfTime.format(b.getAssignDateTo());
					String tooltip = String.format("Applicant: %s\nTime: %s\nSubject: %s\nDescription: %s",
							user.getName(), timeRange, b.getName(),
							(b.getDescription() != null ? b.getDescription() : ""));
					// Escape single quotes for HTML attribute
					tooltip = tooltip.replace("'", "&#39;");

					html.append(String.format("<div class='m-event' style='background-color:%s;' title='%s' "
							+ "onclick=\"event.stopPropagation(); window.openCustomEditDialog('%d', '%s', '%s', '%d', %d, %d);\">%s%s</div>",
							color, tooltip, b.getS_ResourceAssignment_ID(), nameEsc, descEsc, b.getS_Resource_ID(),
							b.getAssignDateFrom().getTime(), b.getAssignDateTo().getTime(), resName, b.getName()));
				}
			}

			html.append("</div>");
			cal.add(Calendar.DATE, 1);
		}

		html.append("</div></div></div>");

		Html zkHtml = new Html();
		zkHtml.setHflex("1");
		zkHtml.setVflex("1");
		zkHtml.setContent(html.toString());
		bookingContainer.appendChild(zkHtml);
	}

	/**
	 * Returns a deterministic color for a resource ID.
	 */
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
