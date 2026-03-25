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
