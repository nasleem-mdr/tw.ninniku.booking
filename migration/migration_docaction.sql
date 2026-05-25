-- ============================================================
--  Migration: Add DocAction support to S_ResourceAssignment
--  Run once against your iDempiere database.
--  Compatible with PostgreSQL (iDempiere default).
-- ============================================================

-- 1. Add DocStatus column (DR=Draft, IP=In-Progress, AP=Approved, VO=Voided)
ALTER TABLE S_ResourceAssignment
    ADD COLUMN IF NOT EXISTS DocStatus CHAR(2) NOT NULL DEFAULT 'DR';

-- 2. Add DocAction column (the NEXT action to be executed)
ALTER TABLE S_ResourceAssignment
    ADD COLUMN IF NOT EXISTS DocAction CHAR(2) NOT NULL DEFAULT 'PR';

-- 3. Processing flag (set by DocumentEngine while processing)
ALTER TABLE S_ResourceAssignment
    ADD COLUMN IF NOT EXISTS Processing CHAR(1) NOT NULL DEFAULT 'N';

-- 4. Processed flag (set to Y once a terminal state is reached)
ALTER TABLE S_ResourceAssignment
    ADD COLUMN IF NOT EXISTS Processed CHAR(1) NOT NULL DEFAULT 'N';

-- 5. IsApproved shortcut flag
ALTER TABLE S_ResourceAssignment
    ADD COLUMN IF NOT EXISTS IsApproved CHAR(1) NOT NULL DEFAULT 'N';

-- 6. Name column (if not already present from a previous migration)
ALTER TABLE S_ResourceAssignment
    ADD COLUMN IF NOT EXISTS Name VARCHAR(60);

-- 7. Migrate existing rows → treat all as already-approved (DR would block them)
UPDATE S_ResourceAssignment
SET DocStatus  = 'AP',
    DocAction  = '--',
    Processed  = 'Y',
    IsApproved = 'Y'
WHERE DocStatus = 'DR';   -- only touches rows that got the default

-- ============================================================
--  AD_Column registration (run in a migration script / via SQL)
--  Replace 1000000 etc. with real AD_Client_ID / AD_Org_ID values.
--  You can also add these columns via the iDempiere "Column" window.
-- ============================================================

DO $$
DECLARE
    v_table_id    INTEGER;
    v_client_id   INTEGER := 1000000;   -- ganti dengan AD_Client_ID Anda
    v_org_id      INTEGER := 0;
    v_next_id     INTEGER;
BEGIN
    SELECT AD_Table_ID INTO v_table_id
    FROM AD_Table WHERE TableName = 'S_ResourceAssignment';

    -- Ambil next ID dari AD_Sequence
    SELECT currentnext INTO v_next_id
    FROM AD_Sequence WHERE name = 'AD_Column';

    -- DocStatus
    IF NOT EXISTS (
        SELECT 1 FROM AD_Column
        WHERE AD_Table_ID = v_table_id AND ColumnName = 'DocStatus'
    ) THEN
        INSERT INTO AD_Column (
            AD_Column_ID, AD_Client_ID, AD_Org_ID, IsActive,
            Created, CreatedBy, Updated, UpdatedBy,
            ColumnName, AD_Table_ID, AD_Reference_ID, FieldLength,
            IsKey, IsParent, IsMandatory, IsUpdateable, IsAlwaysUpdateable,
            Name, Description, AD_Element_ID, EntityType, Version
        ) VALUES (
            v_next_id, v_client_id, v_org_id, 'Y',
            now(), 100, now(), 100,
            'DocStatus', v_table_id, 17, 2,
            'N', 'N', 'Y', 'Y', 'N',
            'Document Status', 'Current document status',
            289, 'U', 1
        );
        -- Update AD_Sequence setelah pakai
        UPDATE AD_Sequence SET currentnext = v_next_id + 1
        WHERE name = 'AD_Column';
        v_next_id := v_next_id + 1;
    END IF;

    -- DocAction
    IF NOT EXISTS (
        SELECT 1 FROM AD_Column
        WHERE AD_Table_ID = v_table_id AND ColumnName = 'DocAction'
    ) THEN
        INSERT INTO AD_Column (
            AD_Column_ID, AD_Client_ID, AD_Org_ID, IsActive,
            Created, CreatedBy, Updated, UpdatedBy,
            ColumnName, AD_Table_ID, AD_Reference_ID, FieldLength,
            IsKey, IsParent, IsMandatory, IsUpdateable, IsAlwaysUpdateable,
            Name, Description, AD_Element_ID, EntityType, Version
        ) VALUES (
            v_next_id, v_client_id, v_org_id, 'Y',
            now(), 100, now(), 100,
            'DocAction', v_table_id, 28, 2,
            'N', 'N', 'Y', 'Y', 'N',
            'Document Action', 'The targeted status of the document',
            287, 'U', 1
        );
        UPDATE AD_Sequence SET currentnext = v_next_id + 1
        WHERE name = 'AD_Column';
        v_next_id := v_next_id + 1;
    END IF;

    -- Processing
    IF NOT EXISTS (
        SELECT 1 FROM AD_Column
        WHERE AD_Table_ID = v_table_id AND ColumnName = 'Processing'
    ) THEN
        INSERT INTO AD_Column (
            AD_Column_ID, AD_Client_ID, AD_Org_ID, IsActive,
            Created, CreatedBy, Updated, UpdatedBy,
            ColumnName, AD_Table_ID, AD_Reference_ID, FieldLength,
            IsKey, IsParent, IsMandatory, IsUpdateable, IsAlwaysUpdateable,
            Name, Description, AD_Element_ID, EntityType, Version
        ) VALUES (
            v_next_id, v_client_id, v_org_id, 'Y',
            now(), 100, now(), 100,
            'Processing', v_table_id, 20, 1,
            'N', 'N', 'Y', 'Y', 'N',
            'Process Now', 'Set this to trigger processing',
            524, 'U', 1
        );
        UPDATE AD_Sequence SET currentnext = v_next_id + 1
        WHERE name = 'AD_Column';
        v_next_id := v_next_id + 1;
    END IF;

    -- Processed
    IF NOT EXISTS (
        SELECT 1 FROM AD_Column
        WHERE AD_Table_ID = v_table_id AND ColumnName = 'Processed'
    ) THEN
        INSERT INTO AD_Column (
            AD_Column_ID, AD_Client_ID, AD_Org_ID, IsActive,
            Created, CreatedBy, Updated, UpdatedBy,
            ColumnName, AD_Table_ID, AD_Reference_ID, FieldLength,
            IsKey, IsParent, IsMandatory, IsUpdateable, IsAlwaysUpdateable,
            Name, Description, AD_Element_ID, EntityType, Version
        ) VALUES (
            v_next_id, v_client_id, v_org_id, 'Y',
            now(), 100, now(), 100,
            'Processed', v_table_id, 20, 1,
            'N', 'N', 'Y', 'Y', 'N',
            'Processed', 'Document has been processed',
            523, 'U', 1
        );
        UPDATE AD_Sequence SET currentnext = v_next_id + 1
        WHERE name = 'AD_Column';
        v_next_id := v_next_id + 1;
    END IF;

    -- IsApproved
    IF NOT EXISTS (
        SELECT 1 FROM AD_Column
        WHERE AD_Table_ID = v_table_id AND ColumnName = 'IsApproved'
    ) THEN
        INSERT INTO AD_Column (
            AD_Column_ID, AD_Client_ID, AD_Org_ID, IsActive,
            Created, CreatedBy, Updated, UpdatedBy,
            ColumnName, AD_Table_ID, AD_Reference_ID, FieldLength,
            IsKey, IsParent, IsMandatory, IsUpdateable, IsAlwaysUpdateable,
            Name, Description, AD_Element_ID, EntityType, Version
        ) VALUES (
            v_next_id, v_client_id, v_org_id, 'Y',
            now(), 100, now(), 100,
            'IsApproved', v_table_id, 20, 1,
            'N', 'N', 'Y', 'Y', 'N',
            'Approved', 'Indicates if this document requires approval',
            351, 'U', 1
        );
        UPDATE AD_Sequence SET currentnext = v_next_id + 1
        WHERE name = 'AD_Column';
        v_next_id := v_next_id + 1;
    END IF;

    RAISE NOTICE 'Migration complete. Last used AD_Column_ID: %', v_next_id - 1;
END $$;

-- ============================================================
--  Optional: create a Document Workflow for S_ResourceAssignment
--  Adjust node IDs, AD_WF_Node_IDs, responsible user/role as needed.
-- ============================================================

/*
-- Create workflow header
INSERT INTO AD_Workflow (
    AD_Workflow_ID, AD_Client_ID, AD_Org_ID, IsActive,
    Created, CreatedBy, Updated, UpdatedBy,
    Name, Description, EntityType, WorkflowType,
    AD_Table_ID, DocValueLogic, IsDefault, Value
)
SELECT nextval('AD_Workflow_Seq'), 1000000, 0, 'Y',
       now(), 100, now(), 100,
       'Booking Approval Workflow',
       'Approval workflow for meeting room bookings',
       'U', 'D',                              -- D = Document Process
       (SELECT AD_Table_ID FROM AD_Table WHERE TableName = 'S_ResourceAssignment'),
       '@DocStatus@=''DR''',                  -- trigger condition
       'Y', 'BK_APPROVAL'
WHERE NOT EXISTS (
    SELECT 1 FROM AD_Workflow WHERE Value = 'BK_APPROVAL'
);
*/

-- ============================================================
--  Verification
-- ============================================================
SELECT column_name, data_type, column_default
FROM information_schema.columns
WHERE table_name = 's_resourceassignment'
  AND column_name IN ('docstatus','docaction','processing','processed','isapproved')
ORDER BY column_name;
