package tw.ninniku.booking.form;

import java.sql.Timestamp;
import java.util.Properties;
import java.util.logging.Logger;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MWFActivity;
import org.compiere.model.MWorkflow;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Trx;

import tw.ninniku.booking.model.MResourceAssignment;

/**
 * BookingService — handles save, update, delete and DocAction transitions
 * for S_ResourceAssignment.
 *
 * DocAction flow:
 *   createBooking()      → saves record with DocStatus=DR, then submitForApproval → IP
 *   approveBooking()     → admin shortcut: DR/IP → AP  (no workflow)
 *   rejectBooking()      → IP → DR  (workflow rejection path)
 *   voidBooking()        → any → VO
 *   processDocAction()   → generic dispatcher used by BookingVM
 */
public class BookingService {

    private static final Logger log = Logger.getLogger(BookingService.class.getName());

    private final Properties ctx;

    public BookingService(Properties ctx) {
        this.ctx = ctx;
    }

    // ─────────────────────────────────────────────────────────────────────
    // CREATE
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Persists a new booking and immediately submits it for approval.
     *
     * @return saved MResourceAssignment (DocStatus = IP after workflow start)
     * @throws BookingValidationException on overlap or invalid data
     * @throws AdempiereException         on DB/workflow errors
     */
    public MResourceAssignment createBooking(BookingDTO dto,
            boolean adminUser, int creatorUserId)
            throws BookingValidationException {

        validateDto(dto, true);

        String trxName = Trx.createTrxName("BK_CREATE");
        Trx trx = Trx.get(trxName, true);
        try {
            MResourceAssignment ra = new MResourceAssignment(ctx, 0, trxName);
            populateFromDto(ra, dto);
            ra.set_ValueOfColumn("CreatedBy", creatorUserId);

            if (!ra.save()) {
                throw new AdempiereException("Could not save booking record.");
            }

            // Submit for approval (triggers workflow if one is configured)
            triggerDocAction(ra, MResourceAssignment.DOCACTION_Prepare, trxName, adminUser);

            trx.commit();
            return ra;

        } catch (BookingValidationException ex) {
            trx.rollback();
            throw ex;
        } catch (Exception ex) {
            trx.rollback();
            throw new AdempiereException("createBooking failed: " + ex.getMessage(), ex);
        } finally {
            trx.close();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // UPDATE (edit dialog)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Updates an existing booking. If the booking was previously Approved it
     * is moved back to Draft first, then re-submitted.
     *
     * @throws BookingValidationException on overlap / invalid data
     */
    public MResourceAssignment updateBooking(BookingDTO dto,
            boolean adminUser, int editorUserId)
            throws BookingValidationException {

        if (dto.bookingId <= 0) {
            throw new BookingValidationException("Cannot update: invalid booking ID.");
        }
        validateDto(dto, false);

        String trxName = Trx.createTrxName("BK_UPDATE");
        Trx trx = Trx.get(trxName, true);
        try {
            MResourceAssignment ra = new MResourceAssignment(ctx, dto.bookingId, trxName);
            if (ra.getS_ResourceAssignment_ID() == 0) {
                throw new BookingValidationException("Booking #" + dto.bookingId + " not found.");
            }

            // If approved, re-open before editing
            if (MResourceAssignment.DOCSTATUS_Approved.equals(ra.getDocStatus())) {
                if (!adminUser) {
                    throw new BookingValidationException(
                            "Only administrators can modify an approved booking.");
                }
                ra.reActivateIt();
                ra.saveEx(trxName);
            }

            populateFromDto(ra, dto);

            if (!ra.save()) {
                throw new AdempiereException("Could not update booking record.");
            }

            // Re-submit for approval
            triggerDocAction(ra, MResourceAssignment.DOCACTION_Prepare, trxName, adminUser);

            trx.commit();
            return ra;

        } catch (BookingValidationException ex) {
            trx.rollback();
            throw ex;
        } catch (Exception ex) {
            trx.rollback();
            throw new AdempiereException("updateBooking failed: " + ex.getMessage(), ex);
        } finally {
            trx.close();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // DRAG-DROP / RESIZE update (timeline only)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Updates booking time from timeline drag-drop.
     * Admins bypass approval; regular users re-submit.
     */
    public void updateBookingTime(int bookingId, int resourceId,
            Timestamp start, Timestamp end,
            boolean adminUser, int editorUserId)
            throws BookingValidationException {

        if (bookingId <= 0) throw new BookingValidationException("Invalid booking ID.");
        if (start == null || end == null || !end.after(start)) {
            throw new BookingValidationException("Invalid time range.");
        }

        String trxName = Trx.createTrxName("BK_DRAG");
        Trx trx = Trx.get(trxName, true);
        try {
            MResourceAssignment ra = new MResourceAssignment(ctx, bookingId, trxName);
            if (ra.getS_ResourceAssignment_ID() == 0) {
                throw new BookingValidationException("Booking #" + bookingId + " not found.");
            }

            // Only admin can move an approved booking
            if (MResourceAssignment.DOCSTATUS_Approved.equals(ra.getDocStatus()) && !adminUser) {
                throw new BookingValidationException(
                        "Cannot move an approved booking without admin rights.");
            }

            if (resourceId > 0) ra.setS_Resource_ID(resourceId);
            ra.setAssignDateFrom(start);
            ra.setAssignDateTo(end);

            if (!ra.save()) {
                throw new AdempiereException("Could not save drag-drop update.");
            }

            // Admin → direct approve; user → re-submit
            if (adminUser) {
                triggerDocAction(ra, MResourceAssignment.DOCACTION_Approve, trxName, true);
            } else {
                triggerDocAction(ra, MResourceAssignment.DOCACTION_Prepare, trxName, false);
            }

            trx.commit();

        } catch (BookingValidationException ex) {
            trx.rollback();
            throw ex;
        } catch (Exception ex) {
            trx.rollback();
            throw new AdempiereException("updateBookingTime failed: " + ex.getMessage(), ex);
        } finally {
            trx.close();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // DELETE / VOID
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Voids (soft-delete) a booking via DocAction.
     * Hard-delete is avoided because approved bookings need an audit trail.
     */
    public void deleteBooking(int bookingId, boolean adminUser, int userId)
            throws BookingValidationException {

        if (bookingId <= 0) throw new BookingValidationException("Invalid booking ID.");

        String trxName = Trx.createTrxName("BK_VOID");
        Trx trx = Trx.get(trxName, true);
        try {
            MResourceAssignment ra = new MResourceAssignment(ctx, bookingId, trxName);
            if (ra.getS_ResourceAssignment_ID() == 0) {
                throw new BookingValidationException("Booking #" + bookingId + " not found.");
            }

            if (MResourceAssignment.DOCSTATUS_Approved.equals(ra.getDocStatus()) && !adminUser) {
                throw new BookingValidationException(
                        "Only administrators can void an approved booking.");
            }

            triggerDocAction(ra, MResourceAssignment.DOCACTION_Void, trxName, adminUser);
            trx.commit();

        } catch (BookingValidationException ex) {
            trx.rollback();
            throw ex;
        } catch (Exception ex) {
            trx.rollback();
            throw new AdempiereException("deleteBooking failed: " + ex.getMessage(), ex);
        } finally {
            trx.close();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // GENERIC DocAction dispatcher (called from BookingVM)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Generic entry point: apply any DocAction to a booking.
     * Supported actions: PR (prepare/submit), AP (approve), VO (void).
     *
     * @param bookingId  S_ResourceAssignment_ID
     * @param docAction  one of MResourceAssignment.DOCACTION_* constants
     */
    public MResourceAssignment processDocAction(int bookingId, String docAction,
            boolean adminUser) throws BookingValidationException {

        if (bookingId <= 0) throw new BookingValidationException("Invalid booking ID.");

        String trxName = Trx.createTrxName("BK_DOC");
        Trx trx = Trx.get(trxName, true);
        try {
            MResourceAssignment ra = new MResourceAssignment(ctx, bookingId, trxName);
            if (ra.getS_ResourceAssignment_ID() == 0) {
                throw new BookingValidationException("Booking #" + bookingId + " not found.");
            }

            // Guard: non-admins cannot directly approve
            if (MResourceAssignment.DOCACTION_Approve.equals(docAction) && !adminUser) {
                throw new BookingValidationException("Insufficient privileges to approve bookings.");
            }

            triggerDocAction(ra, docAction, trxName, adminUser);
            trx.commit();
            return ra;

        } catch (BookingValidationException ex) {
            trx.rollback();
            throw ex;
        } catch (Exception ex) {
            trx.rollback();
            throw new AdempiereException("processDocAction failed: " + ex.getMessage(), ex);
        } finally {
            trx.close();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Internal: execute DocAction + optional workflow
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Executes a DocAction on the model object.
     * If a Workflow is defined for the S_ResourceAssignment table AND the action
     * is "Prepare", the workflow is started instead of calling processIt directly.
     * Otherwise processIt() is called and the record is saved.
     */
    private void triggerDocAction(MResourceAssignment ra, String action,
            String trxName, boolean adminUser) throws Exception {

        ra.setDocAction(action);

        // Try to find a workflow attached to the S_ResourceAssignment table
        int wfId = findWorkflowForTable(trxName);

        if (wfId > 0 && MResourceAssignment.DOCACTION_Prepare.equals(action)) {
            // Start the iDempiere workflow — it will call approve/reject via WF activities
            MWorkflow wf = new MWorkflow(ctx, wfId, trxName);
            wf.start(ra, Env.getContextAsInt(ctx, "#AD_User_ID"));
            log.info("Workflow " + wfId + " started for booking " + ra.getS_ResourceAssignment_ID());

        } else {
            // Direct processIt (admin approve / void / no workflow configured)
            if (!ra.processIt(action)) {
                String msg = ra.getProcessMsg();
                throw new BookingValidationException(
                        msg != null ? msg : "DocAction '" + action + "' failed.");
            }
            ra.saveEx(trxName);
        }
    }

    /**
     * Looks for a Document Workflow (AD_Workflow) that is set as the
     * "Document Value Workflow" for the S_ResourceAssignment table.
     * Returns 0 if none found.
     */
    private int findWorkflowForTable(String trxName) {
        // AD_Table.AD_Workflow_ID is the standard column for document workflows in iDempiere
        String sql =
            "SELECT COALESCE(w.AD_Workflow_ID, 0) "
            + "FROM AD_Table t "
            + "LEFT JOIN AD_Workflow w ON w.AD_Table_ID = t.AD_Table_ID "
            + "    AND w.IsActive = 'Y' "
            + "    AND w.WorkflowType = 'D' "   // D = Document Process
            + "WHERE t.TableName = 'S_ResourceAssignment' "
            + "  AND t.IsActive = 'Y' "
            + "FETCH FIRST 1 ROWS ONLY";
        return DB.getSQLValue(trxName, sql);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    private void populateFromDto(MResourceAssignment ra, BookingDTO dto) {
        ra.setS_Resource_ID(dto.sResourceId > 0 ? dto.sResourceId : dto.groupResourceId);
        ra.setName(dto.name);
        ra.setDescription(dto.description);
        ra.setAssignDateFrom(dto.assignFrom != null ? dto.assignFrom : dto.startTime);
        ra.setAssignDateTo(dto.assignTo != null ? dto.assignTo : dto.endTime);
        ra.setQty(java.math.BigDecimal.ONE);
        ra.setIsConfirmed(false);
    }

    private void validateDto(BookingDTO dto, boolean requireNewId)
            throws BookingValidationException {
        if (requireNewId && dto.bookingId != 0) {
            throw new BookingValidationException("Expected bookingId=0 for new records.");
        }
        if (dto.sResourceId <= 0 && dto.groupResourceId <= 0) {
            throw new BookingValidationException("Resource is required.");
        }
        if (dto.name == null || dto.name.trim().isEmpty()) {
            throw new BookingValidationException("Booking name is required.");
        }
        if (dto.assignFrom == null || dto.assignTo == null) {
            throw new BookingValidationException("Start and end times are required.");
        }
        if (!dto.assignTo.after(dto.assignFrom)) {
            throw new BookingValidationException("End time must be after start time.");
        }
    }
}
