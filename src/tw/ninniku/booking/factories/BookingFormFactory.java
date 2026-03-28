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
