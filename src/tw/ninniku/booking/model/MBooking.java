package tw.ninniku.booking.model;

import java.io.File;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.Calendar;
import java.util.List;
import java.util.Properties;

import org.compiere.model.I_M_InOutLine;
import org.compiere.model.MInOutLine;
import org.compiere.model.MResourceAssignment;
import org.compiere.model.Query;
import org.compiere.process.DocAction;
import org.compiere.util.DB;

public class MBooking extends X_S_Booking  implements DocAction{

	@Override
	protected boolean afterSave(boolean newRecord, boolean success) {
		
		
		if(getS_ResourceAssignment_ID() > 0)
		{
			
			MResourceAssignment assignment = new  MResourceAssignment(getCtx(), getS_ResourceAssignment_ID(), get_TrxName());
			if(assignment.getName()== null || assignment.getName().length() < 2)
			{
				assignment.setName(getName());
			}
			if(assignment.getDescription()== null)
			{
				assignment.setDescription(getDescription());
			}
			if(assignment.is_Changed())
				assignment.saveEx(get_TrxName());
		}
		
		
		return super.afterSave(newRecord, success);
	}

	@Override
	protected boolean beforeDelete() {
		
		// remove Resource Assignment
		MResourceAssignment assignment = new  MResourceAssignment(getCtx(), getS_ResourceAssignment_ID(), get_TrxName());
		assignment.delete(true);
		
		//remove 
		List<MBookingAttendee> list = new Query(getCtx(), MBookingAttendee.Table_Name, "S_Booking_ID=?", get_TrxName())
		.setParameters(getS_Booking_ID())
		.list();
		
		for (MBookingAttendee mBookingAttendee : list) {
			mBookingAttendee.delete(true);
		}
		return super.beforeDelete();
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 8096681258499005667L;

	public MBooking(Properties ctx, int S_Booking_ID, String trxName) {
		super(ctx, S_Booking_ID, trxName);
		// TODO Auto-generated constructor stub
	}
    public  boolean isOverlap() {
        String sql = "select count(*) from s_booking "
        		+ "where s_resource_id = ? "
        		+ "and  (? < assigndateto) "
        		+ "and ( assigndatefrom < ? ) "
        		+ " and s_booking_id != ? ";
        
        Object[] paras = new Object[] { getS_Resource_ID(),getAssignDateFrom(),getAssignDateTo(),getS_Booking_ID() };
        
        return DB.getSQLValue(get_TrxName(), sql, paras) > 0;
    }
	@Override
	protected boolean beforeSave(boolean newRecord) {
	
		/**
		 * check isOverlap
		 */
		if(isOverlap())
			return false;
		if(getS_ResourceAssignment_ID() > 0)
		{
			
			MResourceAssignment assignment = new  MResourceAssignment(getCtx(), getS_ResourceAssignment_ID(), get_TrxName());
				setS_Resource_ID(assignment.getS_Resource_ID());
				setAssignDateFrom(assignment.getAssignDateFrom());
				setAssignDateTo(assignment.getAssignDateTo());
		}
		return super.beforeSave(newRecord);
	}

	public MBooking(Properties ctx, ResultSet rs, String trxName) {
		super(ctx, rs, trxName);
		// TODO Auto-generated constructor stub
	}

	@Override
	public boolean processIt(String action) throws Exception {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public boolean unlockIt() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean invalidateIt() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String prepareIt() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean approveIt() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean rejectIt() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String completeIt() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean voidIt() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean closeIt() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean reverseCorrectIt() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean reverseAccrualIt() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean reActivateIt() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getSummary() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getDocumentInfo() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public File createPDF() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getProcessMsg() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getDoc_User_ID() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getC_Currency_ID() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public BigDecimal getApprovalAmt() {
		// TODO Auto-generated method stub
		return null;
	}

}
