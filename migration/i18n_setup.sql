-- Booking plugin i18n messages (BK_ prefix)
-- Idempotent: safe to re-run after updates.

DELETE FROM AD_Message_Trl WHERE AD_Message_ID IN (SELECT AD_Message_ID FROM AD_Message WHERE Value LIKE 'BK_%');
DELETE FROM AD_Message WHERE Value LIKE 'BK_%';

INSERT INTO AD_Message (AD_Message_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, CreatedBy, Updated, UpdatedBy, Value, MsgText, MsgType, EntityType)
SELECT nextid('AD_Message'::varchar, 'N'::varchar), 0, 0, 'Y', now(), 100, now(), 100, v.val, v.msg, v.type, 'U'
FROM (VALUES
    -- UI labels (toolbar + dialog + form)
    ('BK_Booking',        'Booking',              'I'),
    ('BK_Refresh',        'Refresh',              'I'),
    ('BK_Resource',       'Resource',             'I'),
    ('BK_Week',           'Week',                 'I'),
    ('BK_Day',            'Day',                  'I'),
    ('BK_Timeline',       'Timeline',             'I'),
    ('BK_Today',          'Today',                'I'),
    ('BK_NewBooking',     'New Booking',          'I'),
    ('BK_EditBooking',    'Edit Booking',         'I'),
    ('BK_Name',           'Name *',               'I'),
    ('BK_Memo',           'Memo',                 'I'),
    ('BK_Start',          'Start',                'I'),
    ('BK_End',            'End',                  'I'),
    ('BK_Weekly',         'Weekly',               'I'),
    ('BK_RepeatUntil',    'Repeat Until',         'I'),
    ('BK_Save',           'Save',                 'I'),
    ('BK_Delete',         'Delete',               'I'),
    ('BK_Cancel',         'Cancel',               'I'),
    -- Error / validation messages
    ('BK_NameRequired',       'Name is required.',                                                           'E'),
    ('BK_StartRequired',      'Start date/time is required.',                                                'E'),
    ('BK_EndRequired',        'End date/time is required.',                                                  'E'),
    ('BK_StartBeforeEnd',     'Start time must be before end time.',                                         'E'),
    ('BK_WeeklyEndRequired',  'Weekly end date is required.',                                               'E'),
    ('BK_WeeklyEndAfterEnd',  'Weekly end date must be after booking end time.',                            'E'),
    ('BK_PermissionUpdate',   'Permission denied: only the creator or admin can update this booking.',      'E'),
    ('BK_PermissionDelete',   'Permission denied: only the creator or admin can delete this booking.',      'E'),
    ('BK_TimeOverlap',        'Time overlap',                                                               'E'),
    ('BK_TimeOverlapUpdate',  'Time overlap, update failed.',                                               'E'),
    ('BK_FailedLoad',         'Failed to load data.',                                                       'E'),
    ('BK_ErrorSaving',        'Error saving booking.',                                                      'E')
) AS v(val, msg, type);

INSERT INTO AD_Message_Trl (AD_Message_ID, AD_Language, AD_Client_ID, AD_Org_ID, IsActive, Created, CreatedBy, Updated, UpdatedBy, MsgText, MsgTip, IsTranslated)
SELECT AD_Message_ID, 'zh_TW', 0, 0, 'Y', now(), 100, now(), 100,
    CASE Value
        WHEN 'BK_Booking'           THEN '預約管理'
        WHEN 'BK_Refresh'           THEN '更新'
        WHEN 'BK_Resource'          THEN '資源'
        WHEN 'BK_Week'              THEN '週'
        WHEN 'BK_Day'               THEN '日'
        WHEN 'BK_Timeline'          THEN '時間軸'
        WHEN 'BK_Today'             THEN '今日'
        WHEN 'BK_NewBooking'        THEN '新增預約'
        WHEN 'BK_EditBooking'       THEN '編輯預約'
        WHEN 'BK_Name'              THEN '名稱 *'
        WHEN 'BK_Memo'              THEN '備忘'
        WHEN 'BK_Start'             THEN '開始'
        WHEN 'BK_End'               THEN '結束'
        WHEN 'BK_Weekly'            THEN '每週重複'
        WHEN 'BK_RepeatUntil'       THEN '重複至'
        WHEN 'BK_Save'              THEN '存檔'
        WHEN 'BK_Delete'            THEN '刪除'
        WHEN 'BK_Cancel'            THEN '取消'
        WHEN 'BK_NameRequired'      THEN '名稱為必填'
        WHEN 'BK_StartRequired'     THEN '開始時間為必填'
        WHEN 'BK_EndRequired'       THEN '結束時間為必填'
        WHEN 'BK_StartBeforeEnd'    THEN '開始時間必須早於結束時間'
        WHEN 'BK_WeeklyEndRequired' THEN '每週重複結束日期為必填'
        WHEN 'BK_WeeklyEndAfterEnd' THEN '重複結束日期必須晚於預約結束時間'
        WHEN 'BK_PermissionUpdate'  THEN '權限不足：僅建立者或管理員可修改此預約'
        WHEN 'BK_PermissionDelete'  THEN '權限不足：僅建立者或管理員可刪除此預約'
        WHEN 'BK_TimeOverlap'       THEN '時間重疊'
        WHEN 'BK_TimeOverlapUpdate' THEN '時間重疊，更新失敗'
        WHEN 'BK_FailedLoad'        THEN '資料載入失敗'
        WHEN 'BK_ErrorSaving'       THEN '預約儲存失敗'
    END, NULL, 'Y'
FROM AD_Message WHERE Value LIKE 'BK_%';
