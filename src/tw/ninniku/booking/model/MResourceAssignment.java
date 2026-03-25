package tw.ninniku.booking.model;

import java.sql.ResultSet;
import java.util.Properties;

import org.compiere.util.DB;

public class MResourceAssignment extends org.compiere.model.MResourceAssignment {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public MResourceAssignment(Properties ctx, int S_ResourceAssignment_ID, String trxName) {
		super(ctx, S_ResourceAssignment_ID, trxName);
		// TODO Auto-generated constructor stub
	}

	public MResourceAssignment(Properties ctx, ResultSet rs, String trxName) {
		super(ctx, rs, trxName);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected boolean beforeSave(boolean newRecord) {
		// TODO Auto-generated method stub
		// return super.beforeSave(newRecord);
		if (isOverlap())
			return false;
		return true;
	}

	public void setName(String name) {
		set_Value("Name", name);
	}

	public void setDescription(String description) {
		set_Value("Description", description);
	}

	public boolean isOverlap() {
		String sql = "select count(*) from S_ResourceAssignment "
				+ "where s_resource_id = ? "
				+ "and  (? < assigndateto) "
				+ "and ( assigndatefrom < ? ) "
				+ " and S_ResourceAssignment_ID != ? ";

		Object[] paras = new Object[] { getS_Resource_ID(), getAssignDateFrom(), getAssignDateTo(),
				getS_ResourceAssignment_ID() };

		// KNOWN LIMITATION: This check-then-act pattern is not atomic at the database level.
		// Two concurrent saves can both pass isOverlap() and both insert, causing a double-booking.
		// Fixing this requires a DB-level unique constraint or advisory lock (out of scope).
		return DB.getSQLValue(get_TrxName(), sql, paras) > 0;
	}
}
