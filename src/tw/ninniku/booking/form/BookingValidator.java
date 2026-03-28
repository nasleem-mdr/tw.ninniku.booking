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

    /**
     * Escapes a string for safe embedding in HTML content (text nodes and attribute values).
     * Returns "" for null input.
     */
    public static String escapeForHtml(String value) {
        if (value == null) return "";
        return value
            .replace("&", "&amp;")   // must be first
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;");
    }
}
