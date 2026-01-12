package tw.ninniku.booking.form;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Properties;

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
import org.zkoss.zul.Listbox;
import org.zkoss.zul.Listcell;
import org.zkoss.zul.Listitem;
import org.zkoss.zul.Textbox;

import com.google.gson.Gson;

import tw.ninniku.booking.model.MResourceAssignment;
import tw.ninniku.timeline.Group;
import tw.ninniku.timeline.Item;

public class BookingTimeline extends ADForm implements IFormController, EventListener<Event>, ValueChangeListener {

	/**
	 *  
	 */
	private static final long serialVersionUID = 1L;
	private ArrayList<Group> groups;
	String version = "1.01";
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
	public String errorMessage = "";

	@Override
	public void valueChange(ValueChangeEvent evt) {
		// TODO Auto-generated method stub

	}

	@Override
	public ADForm getForm() {
		// TODO Auto-generated method stub
		return this;
	}

	@Override
	public void onEvent(Event event) throws Exception {
		System.out.println(event);
		System.out.println(event.getTarget().getId());
		if (event.getTarget().getId().equals("btnRefresh")) {
			renewItem(0);
			String cmd = " setTimeout(function(){" + "   drawChart();},500) ;";
			Clients.evalJavaScript(cmd);

			/**
			 * update onMove
			 */
		} else if (event.getTarget().getId().equals("dateLast")) {

			Gson gson = new Gson();
			JSONObject json = gson.fromJson(itemData.getValue(), JSONObject.class);
			Timestamp ds = new Timestamp(Long.valueOf((String) json.get("startTimestamp")));
			Timestamp de = new Timestamp(Long.valueOf((String) json.get("endTimestamp")));

			int S_Booking_ID = Integer.valueOf((String) json.get("s_booking_id"));
			int s_recource_id = Integer.valueOf((String) json.get("group"));
			if (!updateBooking(S_Booking_ID, s_recource_id, ds, de)) {
				Clients.showNotification("Time overlap, update failed.");
				renewItem(100);
				draw(100);
			}

		} else if (event.getTarget().getId().equals("bookingUpdated")) {
			// MQTT.thread(new MQTT.TimelineProducer("TIMELINE", "RAY"), false);

			Gson gson = new Gson();

			JSONObject json = gson.fromJson(itemData.getValue(), JSONObject.class);
			if (!updateBooking(json)) {

				Clients.showNotification(errorMessage);
			}
			renewItem(100);
			draw(100);
		} else if (event.getTarget().getId().equals("bookingDeleted")) {
			Gson gson = new Gson();
			JSONObject json = gson.fromJson(itemData.getValue(), JSONObject.class);

			if (!deleteBooking(json)) {

			}

		} else if (event.getTarget().getId().equals("resourceType")) {
			renewGroup();
			renewItem(0);
			String cmd = " setTimeout(function(){"
					+ "   drawChart();},500) ;";
			Clients.evalJavaScript(cmd);
		}

		super.onEvent(event);
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

		int id = Integer.valueOf(json.get("s_booking_id").toString());

		Trx trx = Trx.get(Trx.createTrxName(), true); // Create a new transaction
		boolean ok = false;
		try {
			// Start the transaction
			trx.start();

			MResourceAssignment booking = new MResourceAssignment(Env.getCtx(), id, trx.getTrxName());
			booking.setDescription(json.get("description").toString());
			booking.setName(json.get("booking-name").toString());
			booking.setS_Resource_ID(Integer.valueOf(json.get("s_resource_id").toString()));
			booking.setAssignDateFrom(new Timestamp(Long.valueOf((String) json.get("assign-date-from-timestamp"))));
			booking.setAssignDateTo(new Timestamp(Long.valueOf((String) json.get("assign-date-to-timestamp"))));

			ok = booking.save(trx.getTrxName());
			if (!ok) {
				errorMessage = "時間重疊:" + booking.getAssignDateFrom().toString();
				errorMessage += " - " + booking.getAssignDateTo().toString();
				return ok;
			}

			if (id == 0 && json.get("is-weekly") != null) {
				boolean isWeekly = ((String) json.get("is-weekly")).equals("Y");

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
					booking.setDescription(json.get("description").toString());
					booking.setName(json.get("booking-name").toString());
					booking.setS_Resource_ID(Integer.valueOf(json.get("s_resource_id").toString()));

					booking.setAssignDateFrom(new Timestamp(calendarFrom.getTimeInMillis()));
					booking.setAssignDateTo(new Timestamp(calendarTo.getTimeInMillis()));
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
			System.out.println("Transaction committed successfully.");
		} catch (Exception e) {
			ok = false;
			trx.rollback();

			// Print the error message
			System.out.println("Transaction rolled back due to an exception: " + e.getMessage());
		} finally {
			// Close the transaction
			trx.close();
			return ok;
		}

		//
		// MResourceAssignment booking = new MResourceAssignment(Env.getCtx(), id,
		// null);
		// booking.setDescription(json.get("description").toString());
		// booking.setName(json.get("booking-name").toString());
		// booking.setS_Resource_ID(Integer.valueOf(json.get("s_resource_id").toString()));
		// booking.setAssignDateFrom(new Timestamp(Long.valueOf((String)
		// json.get("assign-date-from-timestamp"))));
		// booking.setAssignDateTo(new Timestamp(Long.valueOf((String)
		// json.get("assign-date-to-timestamp"))));
		//
		// boolean ok =booking.save();
		//
		// if(!ok)
		// return ok;
		//
		// //repeat only for new booking
		// if(id == 0 && json.get("is-weekly") != null)
		// {
		// boolean isWeekly = ((String) json.get("is-weekly")).equals("Y");
		//
		// if(!isWeekly)
		// return ok;
		//
		// Timestamp repeatTo = new Timestamp(Long.valueOf((String)
		// json.get("repeat-date-to-timestamp")));
		// Calendar calendarFrom = Calendar.getInstance();
		// Calendar calendarTo = Calendar.getInstance();
		// Calendar calendarEnd = Calendar.getInstance();
		//
		// calendarFrom.setTime(booking.getAssignDateFrom());
		// calendarFrom.add(Calendar.DAY_OF_MONTH, 7);
		//
		// calendarTo.setTime(booking.getAssignDateTo());
		// calendarTo.add(Calendar.DAY_OF_MONTH, 7);
		//
		// calendarEnd.setTime(repeatTo);
		//
		// while(calendarFrom.before(calendarEnd))
		// {
		//
		// booking = new MResourceAssignment(Env.getCtx(), 0, null);
		// booking.setDescription(json.get("description").toString());
		// booking.setName(json.get("booking-name").toString());
		// booking.setS_Resource_ID(Integer.valueOf(json.get("s_resource_id").toString()));
		//
		// booking.setAssignDateFrom(new Timestamp(calendarFrom.getTimeInMillis()));
		// booking.setAssignDateTo(new Timestamp(calendarTo.getTimeInMillis()));
		// booking.save();
		//
		// calendarFrom.add(Calendar.DAY_OF_MONTH, 7);
		// calendarTo.add(Calendar.DAY_OF_MONTH, 7);
		// }
		//
		// }
		// return ok;
	}

	private Timestamp convertTimestamp(String dateString) {
		LocalDateTime localDateTime = LocalDateTime.parse(dateString);

		// Convert LocalDateTime to a timestamp in milliseconds
		long timestamp = localDateTime.toInstant(ZoneOffset.ofHours(8)).toEpochMilli();

		return new Timestamp(timestamp);
	}

	private boolean updateBooking(int s_Booking_ID, int groupID, Timestamp ds, Timestamp de) {
		MResourceAssignment booking = new MResourceAssignment(Env.getCtx(), s_Booking_ID, null);

		booking.setAssignDateFrom(ds);
		booking.setAssignDateTo(de);
		booking.setS_Resource_ID(groupID);
		return booking.save();

	}

	@Override
	protected void initForm() {

		String zulPath;
		Properties p = Env.getCtx();
		if (p.getProperty("#Locale").equalsIgnoreCase("zh_TW"))
			zulPath = "~./meetingroom_tw.zul";
		else
			zulPath = "~./meetingroom.zul";

		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		Component component = null;
		try {
			Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
			component = Executions.createComponents(zulPath, this, null);
		} finally {
			Thread.currentThread().setContextClassLoader(cl);
		}
		// getContextPath()
		Button btn = (Button) component.getFellow("btnRefresh");
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
		btn.addEventListener(Events.ON_CLICK, this);

		renewGroup();
		renewItem(500);
		String cmd = "setTimeout(function(){" + "initChart();" + " }, 2000)";
		// cmd = " jq(document).ready(function () {drawChart();});";
		Clients.evalJavaScript(cmd);
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

		String whereSql = " AssignDateFrom >= (now() - INTERVAL '1 day ') "
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

			/**
			 * Resource
			 */
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
		Gson gson = new Gson();
		return gson.toJson(list);
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
		Gson gson = new Gson();
		if (item == null)
			return gson.toJson(list);
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			pstmt = DB.prepareStatement(sql, null);
			pstmt.setInt(1, Integer.valueOf((String) item.getId()));
			rs = pstmt.executeQuery();
			while (rs.next()) {

				String code = rs.getString("value");
				int id = rs.getInt("s_resource_id");
				System.out.println(id);
				list.add(new Group(id, code));
			}
		} catch (SQLException ex) {
			throw new AdempiereException("Unable to load resource", ex);
		} finally {
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}
		groups = list;

		return gson.toJson(list);
	}

	private boolean isWritable() {

		Properties p = Env.getCtx();
		String sql = "select isreadwrite from AD_Form_Access where ad_role_id = ? and ad_form_id = ?";
		String reslut = DB.getSQLValueString(null, sql,
				new Object[] { Integer.valueOf(p.getProperty("#AD_Role_ID")), getAdFormId() });
		return "Y".equals(reslut);
	}
}
