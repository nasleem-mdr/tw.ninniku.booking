package tw.ninniku.booking.model;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Properties;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.ModelValidationEngine;
import org.compiere.model.ModelValidator;
import org.compiere.process.DocAction;
import org.compiere.process.DocOptions;
import org.compiere.process.DocumentEngine;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Msg;

/**
	 * Extended MResourceAssignment with DocAction / Workflow-Approval support.
	 *
	 * DocStatus lifecycle:
	 *   DR (Draft) → IP (In Progress / Submitted for approval) → AP (Approved) or VO (Voided)
	 *
	 * Required DB columns on S_ResourceAssignment (run migration SQL separately):
	 *   DocStatus     CHAR(2)  DEFAULT 'DR' NOT NULL
	 *   DocAction     CHAR(2)  DEFAULT 'CO' NOT NULL
	 *   Processing    CHAR(1)  DEFAULT 'N'
	 *   Processed     CHAR(1)  DEFAULT 'N'
	 *   IsApproved    CHAR(1)  DEFAULT 'N'
	 */

public class MResourceAssignment extends org.compiere.model.MResourceAssignment implement DocAction, DocOptions {


	private static final long serialVersionUID = 1L;
	// ── DocStatus constants used by this model ────────────────────────────
    public static final String DOCSTATUS_Drafted    = "DR";
    public static final String DOCSTATUS_InProgress = "IP";   // submitted / pending approval
    public static final String DOCSTATUS_Approved   = "AP";
    public static final String DOCSTATUS_Voided     = "VO";
    public static final String DOCSTATUS_Reversed   = "RE";

    // ── DocAction constants ───────────────────────────────────────────────
    public static final String DOCACTION_Prepare  = "PR";   // submit for approval
    public static final String DOCACTION_Approve  = "AP";
    public static final String DOCACTION_Void     = "VO";
    public static final String DOCACTION_Close    = "CL";
    public static final String DOCACTION_None     = "--";

    /** Transient error message populated during processIt(). */
    private String processMsg = null;

	public MResourceAssignment(Properties ctx, int S_ResourceAssignment_ID, String trxName) {
		super(ctx, S_ResourceAssignment_ID, trxName);
		// TODO Auto-generated constructor stub
	}

	public MResourceAssignment(Properties ctx, ResultSet rs, String trxName) {
		super(ctx, rs, trxName);
		// TODO Auto-generated constructor stub
	}
	// ── Setters (columns added via migration) ─────────────────────────────

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
	/** DocStatus: DR / IP / AP / VO */
    public void setDocStatus(String docStatus) {
        set_Value("DocStatus", docStatus);
    }

    public String getDocStatus() {
        String s = (String) get_Value("DocStatus");
        return s == null ? DOCSTATUS_Drafted : s;
    }

    /** DocAction requested by the user (e.g. "PR", "AP", "VO"). */
    public void setDocAction(String docAction) {
        set_Value("DocAction", docAction);
    }

    public String getDocAction() {
        String s = (String) get_Value("DocAction");
        return s == null ? DOCACTION_Prepare : s;
    }

    public void setProcessed(boolean processed) {
        set_Value("Processed", processed ? "Y" : "N");
    }

    public boolean isProcessed() {
        return "Y".equals(get_Value("Processed"));
    }

    public void setIsApproved(boolean approved) {
        set_Value("IsApproved", approved ? "Y" : "N");
    }

    public boolean isApproved() {
        return "Y".equals(get_Value("IsApproved"));
    }
// ── DocOptions: which buttons appear in the document toolbar ──────────

    @Override
    public int customizeValidActions(String docStatus, Object processing,
            String orderType, String isSOTrx,
            int AD_Table_ID, String[] docAction, String[] options, int index) {

        if (DOCSTATUS_Drafted.equals(docStatus)) {
            options[index++] = DOCACTION_Prepare;   // "Submit for Approval"
            options[index++] = DOCACTION_Void;
        } else if (DOCSTATUS_InProgress.equals(docStatus)) {
            options[index++] = DOCACTION_Approve;
            options[index++] = DOCACTION_Void;
        } else if (DOCSTATUS_Approved.equals(docStatus)) {
            options[index++] = DOCACTION_Void;
        }
        return index;
    }

    // ── DocAction implementation ───────────────────────────────────────────

    @Override
    public boolean processIt(String action) throws Exception {
        processMsg = null;
        DocumentEngine engine = new DocumentEngine(this, getDocStatus());
        return engine.processIt(action, getDocAction());
    }

    // Called by DocumentEngine — runs model validators BEFORE the action
    @Override
    public boolean unlockIt() {
        set_Value("Processing", "N");
        return true;
    }

    @Override
    public boolean invalidateIt() {
        setDocAction(DOCACTION_Prepare);
        return true;
    }

    /**
     * prepareIt → transitions DR → IP (submitted for approval).
     * Workflow engine will call this when the user clicks "Submit".
     */
    @Override
    public String prepareIt() {
        // Validate overlap before submitting
        if (isOverlap()) {
            processMsg = "Booking overlaps with an existing reservation.";
            return DocAction.STATUS_Invalid;
        }

        // Fire model validators
        processMsg = ModelValidationEngine.get().fireDocValidate(this,
                ModelValidator.TIMING_BEFORE_PREPARE);
        if (processMsg != null) return DocAction.STATUS_Invalid;

        setDocStatus(DOCSTATUS_InProgress);

        processMsg = ModelValidationEngine.get().fireDocValidate(this,
                ModelValidator.TIMING_AFTER_PREPARE);
        if (processMsg != null) return DocAction.STATUS_Invalid;

        return DocAction.STATUS_InProgress;
    }

    @Override
    public boolean approveIt() {
        setIsApproved(true);
        setDocStatus(DOCSTATUS_Approved);
        setDocAction(DOCACTION_None);
        setProcessed(true);

        String msg = ModelValidationEngine.get().fireDocValidate(this,
                ModelValidator.TIMING_AFTER_COMPLETE);
        if (msg != null) {
            processMsg = msg;
            return false;
        }
        return true;
    }

    @Override
    public boolean rejectIt() {
        setIsApproved(false);
        setDocStatus(DOCSTATUS_Drafted);   // send back to draft so user can re-submit
        setDocAction(DOCACTION_Prepare);
        setProcessed(false);
        return true;
    }

    /**
     * completeIt — direct approval without workflow (admin path).
     */
    @Override
    public String completeIt() {
        // Model validators
        processMsg = ModelValidationEngine.get().fireDocValidate(this,
                ModelValidator.TIMING_BEFORE_COMPLETE);
        if (processMsg != null) return DocAction.STATUS_Invalid;

        approveIt();

        processMsg = ModelValidationEngine.get().fireDocValidate(this,
                ModelValidator.TIMING_AFTER_COMPLETE);
        if (processMsg != null) {
            setIsApproved(false);
            setDocStatus(DOCSTATUS_InProgress);
            return DocAction.STATUS_Invalid;
        }
        return DocAction.STATUS_Completed;
    }

    @Override
    public boolean voidIt() {
        String currentStatus = getDocStatus();
        if (DOCSTATUS_Approved.equals(currentStatus)) {
            // Already approved — set a reversal note
            setDescription((getDescription() == null ? "" : getDescription() + " | ")
                    + "Voided on " + new Timestamp(System.currentTimeMillis()));
        }
        setDocStatus(DOCSTATUS_Voided);
        setDocAction(DOCACTION_None);
        setProcessed(true);
        setIsApproved(false);

        String msg = ModelValidationEngine.get().fireDocValidate(this,
                ModelValidator.TIMING_AFTER_VOID);
        if (msg != null) { processMsg = msg; return false; }
        return true;
    }

    @Override
    public boolean closeIt() {
        setDocAction(DOCACTION_None);
        return true;
    }

    @Override
    public boolean reverseCorrectIt() {
        processMsg = "Reverse-correct not supported for bookings.";
        return false;
    }

    @Override
    public boolean reverseAccrualIt() {
        processMsg = "Reverse-accrual not supported for bookings.";
        return false;
    }

    @Override
    public boolean reActivateIt() {
        if (!DOCSTATUS_Approved.equals(getDocStatus())) {
            processMsg = "Only approved bookings can be re-activated.";
            return false;
        }
        setDocStatus(DOCSTATUS_Drafted);
        setDocAction(DOCACTION_Prepare);
        setProcessed(false);
        setIsApproved(false);
        return true;
    }

    // ── DocAction metadata ────────────────────────────────────────────────

    @Override
    public String getSummary() {
        return "ResourceAssignment[" + getS_ResourceAssignment_ID()
                + "] " + get_ValueAsString("Name")
                + " Status=" + getDocStatus();
    }

    @Override
    public String getDocumentNo() {
        // Use the PK as document number; replace with a real sequence if needed.
        return String.valueOf(getS_ResourceAssignment_ID());
    }

    @Override
    public String getDocumentInfo() {
        return "Booking #" + getDocumentNo() + " [" + getDocStatus() + "]";
    }

    @Override
    public java.io.File createPDF() {
        return null;   // PDF generation not implemented
    }

    @Override
    public String getProcessMsg() {
        return processMsg;
    }

    @Override
    public int getDoc_User_ID() {
        // Return the user who created / owns this booking.
        return Env.getContextAsInt(getCtx(), "#AD_User_ID");
    }

    @Override
    public java.math.BigDecimal getApprovalAmt() {
        return java.math.BigDecimal.ZERO;   // no monetary amount to approve
    }

    @Override
    public int getC_Currency_ID() {
        return 0;
    }

    // ── beforeSave ────────────────────────────────────────────────────────

    @Override
    protected boolean beforeSave(boolean newRecord) {
        // Initialise DocStatus for brand-new records
        if (newRecord && (getDocStatus() == null || getDocStatus().isEmpty())) {
            setDocStatus(DOCSTATUS_Drafted);
            setDocAction(DOCACTION_Prepare);
        }

        // Overlap check only for non-voided/non-approved states
        if (!DOCSTATUS_Voided.equals(getDocStatus())
                && !DOCSTATUS_Approved.equals(getDocStatus())) {
            if (isOverlap()) {
                log.saveError("Error",
                        Msg.getMsg(getCtx(), "BookingOverlap",
                                new Object[]{"Booking overlaps with an existing reservation."}));
                return false;
            }
        }
        return true;
    }

    // ── Business logic helpers ────────────────────────────────────────────

    /**
     * Returns true if another non-voided booking occupies the same resource
     * during the same time window.
     */
    public boolean isOverlap() {
        String sql =
            "SELECT COUNT(*) FROM S_ResourceAssignment "
            + "WHERE S_Resource_ID = ? "
            + "  AND ? < AssignDateTo "
            + "  AND AssignDateFrom < ? "
            + "  AND S_ResourceAssignment_ID != ? "
            + "  AND COALESCE(DocStatus,'DR') != 'VO'";   // ignore voided bookings

        return DB.getSQLValue(get_TrxName(), sql,
                getS_Resource_ID(),
                getAssignDateFrom(),
                getAssignDateTo(),
                getS_ResourceAssignment_ID()) > 0;
    }

    /**
     * Convenience: submit this booking for approval and save in one call.
     * Used by BookingService after creating/updating a booking record.
     *
     * @throws AdempiereException if processIt fails
     */
    public void submitForApproval(String trxName) {
        setDocAction(DOCACTION_Prepare);
        try {
            if (!processIt(DOCACTION_Prepare)) {
                throw new AdempiereException(
                        processMsg != null ? processMsg : "Failed to submit booking for approval");
            }
        } catch (AdempiereException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AdempiereException("Failed to submit booking for approval: " + ex.getMessage(), ex);
        }
        saveEx(trxName);
    }

	// public boolean isOverlap() {
	// 	String sql = "select count(*) from S_ResourceAssignment "
	// 			+ "where s_resource_id = ? "
	// 			+ "and  (? < assigndateto) "
	// 			+ "and ( assigndatefrom < ? ) "
	// 			+ " and S_ResourceAssignment_ID != ? ";

	// 	Object[] paras = new Object[] { getS_Resource_ID(), getAssignDateFrom(), getAssignDateTo(),
	// 			getS_ResourceAssignment_ID() };

	// 	// KNOWN LIMITATION: This check-then-act pattern is not atomic at the database level.
	// 	// Two concurrent saves can both pass isOverlap() and both insert, causing a double-booking.
	// 	// Fixing this requires a DB-level unique constraint or advisory lock (out of scope).
	// 	return DB.getSQLValue(get_TrxName(), sql, paras) > 0;
	// }
}
