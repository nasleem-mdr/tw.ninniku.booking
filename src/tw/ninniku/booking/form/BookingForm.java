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
            log.log(java.util.logging.Level.SEVERE, "Failed to init BookingForm", e);
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
