package tw.ninniku.booking.form;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MWFActivity;
import org.compiere.model.MWorkflow;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.Trx;

import tw.ninniku.booking.model.MResourceAssignment;
import tw.ninniku.timeline.Group;

public class BookingService {

    private final Properties ctx;

    public BookingService(Properties ctx) {
        this.ctx = ctx;
    }

    /**
     * Creates or updates a booking. For weekly bookings, creates one record per week
     * until dto.weeklyEndDate (all-or-nothing: any failure rolls back all iterations).
     *
     * @param dto       validated, parsed DTO
     * @param isAdmin   result of BookingTimeline.isWritable() — true if role has write access
     * @param currentUserId  Env.getContextAsInt(ctx, "#AD_User_ID")
     * @return S_ResourceAssignment_ID of the saved (or first-created) record
     * @throws BookingValidationException if the user lacks permission to edit an existing booking
     * @throws AdempiereException on DB failure (transaction rolled back before throwing)
     */
    public int saveBooking(BookingDTO dto, boolean isAdmin, int currentUserId)
            throws BookingValidationException, AdempiereException {

        // Permission check for existing bookings
        if (dto.bookingId > 0) {
            MResourceAssignment existing = new MResourceAssignment(ctx, dto.bookingId, null);
            if (!isAdmin && existing.getCreatedBy() != currentUserId) {
                throw new BookingValidationException(Msg.getMsg(ctx, "BK_PermissionUpdate"));
            }
        }

        Trx trx = Trx.get(Trx.createTrxName(), true);
        int savedId = 0;
        try {
            trx.start();

            MResourceAssignment booking = new MResourceAssignment(ctx, dto.bookingId, trx.getTrxName());
            booking.setName(dto.name);
            booking.setDescription(dto.description);
            booking.setS_Resource_ID(dto.sResourceId);
            booking.setAssignDateFrom(dto.assignFrom);
            booking.setAssignDateTo(dto.assignTo);
            if (booking.getAD_Org_ID() == 0) {
                booking.setAD_Org_ID(Env.getAD_Org_ID(ctx));
            }

            if (!booking.save(trx.getTrxName())) {
                throw new AdempiereException(Msg.getMsg(ctx, "BK_TimeOverlap") + ": " + booking.getAssignDateFrom() + " - " + booking.getAssignDateTo());
            }
            savedId = booking.getS_ResourceAssignment_ID();

            // Weekly recurrence — only for new bookings
            if (dto.bookingId == 0 && dto.isWeekly && dto.weeklyEndDate != null) {
                Calendar calFrom = Calendar.getInstance();
                Calendar calTo = Calendar.getInstance();
                Calendar calEnd = Calendar.getInstance();
                calFrom.setTime(dto.assignFrom);
                calFrom.add(Calendar.DAY_OF_MONTH, 7);
                calTo.setTime(dto.assignTo);
                calTo.add(Calendar.DAY_OF_MONTH, 7);
                calEnd.setTime(dto.weeklyEndDate);

                while (calFrom.before(calEnd)) {
                    MResourceAssignment weekly = new MResourceAssignment(ctx, 0, trx.getTrxName());
                    weekly.setName(dto.name);
                    weekly.setDescription(dto.description);
                    weekly.setS_Resource_ID(dto.sResourceId);
                    weekly.setAssignDateFrom(new Timestamp(calFrom.getTimeInMillis()));
                    weekly.setAssignDateTo(new Timestamp(calTo.getTimeInMillis()));
                    if (weekly.getAD_Org_ID() == 0) {
                        weekly.setAD_Org_ID(Env.getAD_Org_ID(ctx));
                    }
                    if (!weekly.save(trx.getTrxName())) {
                        throw new AdempiereException(Msg.getMsg(ctx, "BK_TimeOverlap") + ": " + weekly.getAssignDateFrom() + " - " + weekly.getAssignDateTo());
                    }
                    calFrom.add(Calendar.DAY_OF_MONTH, 7);
                    calTo.add(Calendar.DAY_OF_MONTH, 7);
                }
            }

            trx.commit();
            return savedId;
        } catch (AdempiereException e) {
            trx.rollback();
            throw e;
        } catch (Exception e) {
            trx.rollback();
            throw new AdempiereException(Msg.getMsg(ctx, "BK_ErrorSaving") + ": " + e.getMessage(), e);
        } finally {
            trx.close();
        }
    }

    /**
     * Updates only the time and resource of an existing booking (used by drag-drop / resize).
     * Uses auto-commit — single-record update, no weekly recurrence.
     *
     * @throws BookingValidationException if user lacks permission
     * @throws AdempiereException on DB failure
     */
    public void updateBookingTime(int bookingId, int resourceId, Timestamp start, Timestamp end,
            boolean isAdmin, int currentUserId)
            throws BookingValidationException, AdempiereException {
        if (bookingId <= 0) return;

        MResourceAssignment booking = new MResourceAssignment(ctx, bookingId, null);
        if (!isAdmin && booking.getCreatedBy() != currentUserId) {
            throw new BookingValidationException(Msg.getMsg(ctx, "BK_PermissionUpdate"));
        }
        booking.setAssignDateFrom(start);
        booking.setAssignDateTo(end);
        booking.setS_Resource_ID(resourceId);
        if (!booking.save()) {
            throw new AdempiereException(Msg.getMsg(ctx, "BK_TimeOverlapUpdate"));
        }
    }

    /**
     * Deletes a booking if the user has permission.
     *
     * @throws BookingValidationException if user lacks permission
     * @throws AdempiereException on DB failure
     */
    public void deleteBooking(int bookingId, boolean isAdmin, int currentUserId)
            throws BookingValidationException, AdempiereException {
        if (bookingId <= 0) return;
        MResourceAssignment booking = new MResourceAssignment(ctx, bookingId, null);
        if (!isAdmin && booking.getCreatedBy() != currentUserId) {
            throw new BookingValidationException(Msg.getMsg(ctx, "BK_PermissionDelete"));
        }
        booking.delete(true);
    }

    /**
     * Loads all active bookings where AssignDateFrom is within [start, end]
     * for the given resource type.
     * Returns empty list if none found.
     * Throws AdempiereException (unchecked) on DB failure.
     */
    public List<MResourceAssignment> fetchBookings(int resourceTypeId, Timestamp start, Timestamp end) {
        String whereSql = " AssignDateFrom >= ? AND AssignDateFrom <= ? "
                + "and exists (select 1 from S_Resource where S_Resource_ID = "
                + "S_ResourceAssignment.S_Resource_ID and S_ResourceType_ID = ?) ";
        return new org.compiere.model.Query(ctx, MResourceAssignment.Table_Name, whereSql, null)
                .setParameters(new Object[]{start, end, resourceTypeId})
                .setOrderBy("S_Resource_ID")
                .setOnlyActiveRecords(true)
                .list();
    }

    /**
     * Loads timeline groups (resources) for the given resource type.
     * Returns empty list if none found.
     * Throws AdempiereException (unchecked) on DB failure.
     */
    public List<Group> fetchGroups(int resourceTypeId) {
        List<Group> list = new ArrayList<>();
        String sql = "select * from s_resource where s_resourcetype_id = ?";
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = DB.prepareStatement(sql, null);
            pstmt.setInt(1, resourceTypeId);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                list.add(new Group(rs.getInt("s_resource_id"), rs.getString("name")));
            }
        } catch (SQLException ex) {
            throw new AdempiereException(Msg.getMsg(ctx, "BK_FailedLoad"), ex);
        } finally {
            DB.close(rs, pstmt);
        }
        return list;
    }
}
