package tw.ninniku.booking.form;

public class BookingValidationException extends Exception {
    private static final long serialVersionUID = 1L;

    public BookingValidationException(String userMessage) {
        super(userMessage);
    }
}
