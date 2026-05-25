package tw.ninniku.booking.model;

import java.sql.ResultSet;
import java.util.Properties;

import org.adempiere.base.IModelFactory;
import org.compiere.model.PO;
import org.compiere.util.Env;

/**
 * OSGI Model Factory — tells iDempiere's model/workflow engine
 * to use MResourceAssignment (our extended class) whenever it needs
 * to load an S_ResourceAssignment record.
 *
 * Registration: declared in OSGI-INF/MResourceAssignmentFactory.xml
 * and referenced from META-INF/MANIFEST.MF (Service-Component header).
 *
 * Without this factory:
 *   - The workflow engine loads a generic PO instead of our DocAction model
 *   - processIt() / approveIt() etc. are never called
 *   - Workflow Activities in iDempiere UI cannot transition the document
 */
public class MResourceAssignmentFactory implements IModelFactory {

    private static final String TABLE_NAME = "S_ResourceAssignment";

    // ── IModelFactory ────────────────────────────────────────────────────

    /**
     * Returns our extended class for S_ResourceAssignment,
     * null for all other tables (let other factories handle them).
     */
    @Override
    public Class<?> getClass(String tableName) {
        if (TABLE_NAME.equalsIgnoreCase(tableName)) {
            return MResourceAssignment.class;
        }
        return null;
    }

    /**
     * Instantiates MResourceAssignment by primary key.
     * Called by the workflow engine, model validator engine, etc.
     */
    @Override
    public PO getPO(String tableName, int Record_ID, String trxName) {
        if (TABLE_NAME.equalsIgnoreCase(tableName)) {
            Properties ctx = Env.getCtx();
            return new MResourceAssignment(ctx, Record_ID, trxName);
        }
        return null;
    }

    /**
     * Instantiates MResourceAssignment from a ResultSet row.
     * Called by Query.list() and similar bulk-load paths.
     */
    @Override
    public PO getPO(String tableName, ResultSet rs, String trxName) {
        if (TABLE_NAME.equalsIgnoreCase(tableName)) {
            Properties ctx = Env.getCtx();
            return new MResourceAssignment(ctx, rs, trxName);
        }
        return null;
    }
}
