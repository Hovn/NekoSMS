package com.crossbowffs.nekosms.loader;

import static com.crossbowffs.nekosms.provider.DatabaseContract.FilterRules;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.RemoteException;

import com.crossbowffs.nekosms.app.FilterRulesAdapter;
import com.crossbowffs.nekosms.data.SmsFilterAction;
import com.crossbowffs.nekosms.data.SmsFilterData;
import com.crossbowffs.nekosms.data.SmsFilterMode;
import com.crossbowffs.nekosms.data.SmsFilterPatternData;
import com.crossbowffs.nekosms.provider.DatabaseContract;
import com.crossbowffs.nekosms.utils.MapUtils;
import com.crossbowffs.nekosms.utils.Xlog;
import com.crossbowffs.nekosms.widget.AutoContentLoader;

import java.util.ArrayList;
import java.util.List;

public class FilterRuleLoader extends AutoContentLoader<SmsFilterData> {
    private static FilterRuleLoader sInstance;

    public static FilterRuleLoader get() {
        if (sInstance == null) {
            sInstance = new FilterRuleLoader();
        }
        return sInstance;
    }

    private FilterRuleLoader() {
        super(FilterRules.CONTENT_URI, FilterRules.ALL);
    }

    @Override
    protected SmsFilterData newData() {
        return new SmsFilterData();
    }

    @Override
    protected void clearData(SmsFilterData data) {
        data.reset();
    }

    @Override
    protected void bindData(Cursor cursor, int column, String columnName, SmsFilterData data) {
        switch (columnName) {
            case FilterRules._ID:
                data.setId(cursor.getLong(column));
                break;
            case FilterRules.PRIORITY:
                data.setPriority(cursor.getInt(column));
                break;
        case FilterRules.ACTION:
            data.setAction(SmsFilterAction.parse(cursor.getString(column)));
            break;
        case FilterRules.SENDER_MODE:
            data.getSenderPattern().setMode(SmsFilterMode.parse(cursor.getString(column)));
            break;
        case FilterRules.SENDER_PATTERN:
            data.getSenderPattern().setPattern(cursor.getString(column));
            break;
        case FilterRules.SENDER_CASE_SENSITIVE:
            if (!cursor.isNull(column))
                data.getSenderPattern().setCaseSensitive(cursor.getInt(column) != 0);
            break;
        case FilterRules.BODY_MODE:
            data.getBodyPattern().setMode(SmsFilterMode.parse(cursor.getString(column)));
            break;
        case FilterRules.BODY_PATTERN:
            data.getBodyPattern().setPattern(cursor.getString(column));
            break;
        case FilterRules.BODY_CASE_SENSITIVE:
            if (!cursor.isNull(column))
                data.getBodyPattern().setCaseSensitive(cursor.getInt(column) != 0);
            break;
        }
    }

    @Override
    protected ContentValues serialize(SmsFilterData data) {
        ContentValues values = MapUtils.contentValuesForSize(9);
        if (data.getId() >= 0) {
            values.put(FilterRules._ID, data.getId());
        }
        values.put(FilterRules.ACTION, data.getAction().name());
        SmsFilterPatternData senderPattern = data.getSenderPattern();
        if (senderPattern.hasData()) {
            values.put(FilterRules.SENDER_MODE, senderPattern.getMode().name());
            values.put(FilterRules.SENDER_PATTERN, senderPattern.getPattern());
            values.put(FilterRules.SENDER_CASE_SENSITIVE, senderPattern.isCaseSensitive() ? 1 : 0);
        } else {
            values.putNull(FilterRules.SENDER_MODE);
            values.putNull(FilterRules.SENDER_PATTERN);
            values.putNull(FilterRules.SENDER_CASE_SENSITIVE);
        }
        SmsFilterPatternData bodyPattern = data.getBodyPattern();
        if (bodyPattern.hasData()) {
            values.put(FilterRules.BODY_MODE, bodyPattern.getMode().name());
            values.put(FilterRules.BODY_PATTERN, bodyPattern.getPattern());
            values.put(FilterRules.BODY_CASE_SENSITIVE, bodyPattern.isCaseSensitive() ? 1 : 0);
        } else {
            values.putNull(FilterRules.BODY_MODE);
            values.putNull(FilterRules.BODY_PATTERN);
            values.putNull(FilterRules.BODY_CASE_SENSITIVE);
        }
        values.put(FilterRules.PRIORITY,data.getPriority());
        return values;
    }

    @Deprecated
    public int queryMaxPriorityOld(Context context) {
        ContentResolver contentResolver = context.getContentResolver();
        Cursor cursor=contentResolver.query(
                DatabaseContract.FilterRules.CONTENT_URI,
                //DatabaseContract.FilterRules.ALL,
                new String[]{DatabaseContract.FilterRules.PRIORITY},
                null,
                null,
                DatabaseContract.FilterRules.PRIORITY + " DESC");
        cursor.moveToFirst();
        if(cursor.getCount()==0){
            return 0;
        }else {
            //int index = cursor.getColumnIndex(DatabaseContract.FilterRules.PRIORITY);
            return cursor.getInt(0);
        }
    }

    public int queryMaxPriority(Context context) {
        ContentResolver contentResolver = context.getContentResolver();
        Cursor cursor=contentResolver.query(
                DatabaseContract.FilterRules.CONTENT_URI,
                new String[]{"MAX("+DatabaseContract.FilterRules.PRIORITY+")"},
                null,
                null,
                null);
        cursor.moveToFirst();
        return cursor.getInt(0);
    }

    /**
     * 查询比当前优先级高的条目数量，可用于确定当前条目在列表的显示位置（position）
     * @param context
     * @param currentPriority
     * @return
     */
    public int queryNumHigherPriority(Context context,int currentPriority) {
        ContentResolver contentResolver = context.getContentResolver();
        Cursor cursor=contentResolver.query(
                DatabaseContract.FilterRules.CONTENT_URI,
                new String[]{"COUNT("+ FilterRules._ID+")"},
                FilterRules.PRIORITY+">?",
                new String[]{ String.valueOf(currentPriority) },
                null);
        cursor.moveToFirst();
        return cursor.getInt(0);
    }

    /**
     * 查询比当前优先级高一位的ID
     * 参考查询语句：select min(priority) from filter_rules where priority > currentId
     * 参考查询语句：select _id from filter_rules where priority > currentId order by priority ASC
     * @param context
     * @param currentPriority
     * @return
     */
    public long queryIdOfHigherPriority(Context context,int currentPriority) {
        ContentResolver contentResolver = context.getContentResolver();
        Cursor cursor=contentResolver.query(
                DatabaseContract.FilterRules.CONTENT_URI,
                new String[]{ FilterRules._ID },
                FilterRules.PRIORITY+">?",
                new String[]{ String.valueOf(currentPriority) },
                FilterRules.PRIORITY +" ASC");
        cursor.moveToFirst();

        long _id  = -1;
        if(cursor.getCount()>0){
            _id=cursor.getLong(0);
        }
        return _id;
    }

    /**
     * 查询比当前优先级高一位的 SmsFilterData
     * 参考查询语句：select min(priority) from filter_rules where priority > currentId
     * 参考查询语句：select _id from filter_rules where priority > currentId order by priority ASC
     * @param context
     * @param currentPriority
     * @return
     */
    public SmsFilterData queryDataOfHigherPriority(Context context,int currentPriority) {
        ContentResolver contentResolver = context.getContentResolver();
        Cursor cursor=contentResolver.query(
                DatabaseContract.FilterRules.CONTENT_URI,
                FilterRules.ALL,
                FilterRules.PRIORITY+">?",
                new String[]{ String.valueOf(currentPriority) },
                FilterRules.PRIORITY +" ASC");
        cursor.moveToFirst();

        SmsFilterData data = null;
        if(cursor.getCount()>0){
            data=getData(cursor, getColumns(cursor), data);
        }
        return data;
    }


    /**
     * 查询比当前优先级低一位的ID
     * 参考查询语句：select max(priority) from filter_rules where priority< currentId
     * 参考查询语句：select _id from filter_rules where priority < currentId order by priority DESC
     * @param context
     * @param currentPriority
     * @return
     */
    public long queryIdOfLowerPriority(Context context,int currentPriority) {
        ContentResolver contentResolver = context.getContentResolver();
        Cursor cursor=contentResolver.query(
                DatabaseContract.FilterRules.CONTENT_URI,
                new String[]{ FilterRules._ID },
                FilterRules.PRIORITY+"<?",
                new String[]{ String.valueOf(currentPriority) },
                FilterRules.PRIORITY +" DESC");
        cursor.moveToFirst();

        long _id  = -1;
        if(cursor.getCount()>0){
            _id=cursor.getLong(0);
        }
        return _id;
    }

    /**
     * 查询比当前优先级低一位的 SmsFilterData
     * 参考查询语句：select max(priority) from filter_rules where priority< currentId
     * 参考查询语句：select _id from filter_rules where priority < currentId order by priority DESC
     * @param context
     * @param currentPriority
     * @return
     */
    public SmsFilterData queryDataOfLowerPriority(Context context,int currentPriority) {
        ContentResolver contentResolver = context.getContentResolver();
        Cursor cursor=contentResolver.query(
                DatabaseContract.FilterRules.CONTENT_URI,
                FilterRules.ALL,
                FilterRules.PRIORITY+"<?",
                new String[]{ String.valueOf(currentPriority) },
                FilterRules.PRIORITY +" DESC");
        cursor.moveToFirst();

        SmsFilterData data = null;
        if(cursor.getCount()>0){
            data=getData(cursor, getColumns(cursor), data);
        }
        return data;
    }

    // Code work good
    public void swapPriority(Context context, FilterRulesAdapter adapter, int fromPosition, int toPosition) {
        swapPriority(context, adapter.getItemId(fromPosition), adapter.getItemId(toPosition));
    }

    //Code work good
    public void swapPriority(Context context, long firstId, long secondId) {
        Uri firstUri = ContentUris.withAppendedId(DatabaseContract.FilterRules.CONTENT_URI, firstId);
        Uri secondUri = ContentUris.withAppendedId(DatabaseContract.FilterRules.CONTENT_URI, secondId);

        SmsFilterData firstData = query(context, firstUri);
        SmsFilterData secondData = query(context, secondUri);

        int priority1=firstData.getPriority();
        int priority2=secondData.getPriority();

        firstData.setPriority(priority2);
        secondData.setPriority(priority1);

        update(context, firstUri, firstData, false);
        update(context, secondUri, secondData, false);
    }

    //Code work good
    public boolean swapPriorityByExecUpdate(Context context, FilterRulesAdapter adapter, int fromPosition, int toPosition) {
        long fromId = adapter.getItemId(fromPosition);
        long toId = adapter.getItemId(toPosition);
        Uri firstUri = ContentUris.withAppendedId(DatabaseContract.FilterRules.CONTENT_URI, fromId);
        Uri secondUri = ContentUris.withAppendedId(DatabaseContract.FilterRules.CONTENT_URI, toId);
        SmsFilterData firstData = query(context, firstUri);
        SmsFilterData secondData = query(context, secondUri);
        int priority1=firstData.getPriority();
        int priority2=secondData.getPriority();

        ContentValues contentValues1=new ContentValues();
        contentValues1.put("priority", priority2);
        ContentValues contentValues2=new ContentValues();
        contentValues2.put("priority", priority1);

        ContentResolver contentResolver = context.getContentResolver();

        int updated1_num = contentResolver.update(
                DatabaseContract.FilterRules.CONTENT_URI,
                contentValues1,
                FilterRules._ID+" = "+fromId,
                null);
        int updated2_num = contentResolver.update(
                DatabaseContract.FilterRules.CONTENT_URI,
                contentValues2,
                FilterRules._ID+" = "+toId,
                null);

        return updated1_num != 0 && updated2_num != 0;
    }

    @Deprecated //交换ID在拖拽时不是个好主意，因为条目就是根据ID来标识的
    public void swapId(Context context, long firstId, long secondId) {
        Uri firstUri = ContentUris.withAppendedId(DatabaseContract.FilterRules.CONTENT_URI, firstId);
        Uri secondUri = ContentUris.withAppendedId(DatabaseContract.FilterRules.CONTENT_URI, secondId);

        SmsFilterData firstData = query(context, firstUri);
        SmsFilterData secondData = query(context, secondUri);

        firstData.setId(secondId);
        secondData.setId(firstId);

        update(context, firstUri, secondData, false);
        update(context, secondUri, firstData, false);
    }

    @Deprecated //交换ID在拖拽时不是个好主意，因为条目就是根据ID来标识的
    public void swapId(Context context, SmsFilterData firstData, SmsFilterData secondData) {
        long firstId = firstData.getId();
        long secondId = secondData.getId();

        Uri firstUri = ContentUris.withAppendedId(DatabaseContract.FilterRules.CONTENT_URI, firstId);
        Uri secondUri = ContentUris.withAppendedId(DatabaseContract.FilterRules.CONTENT_URI, secondId);

        firstData.setId(secondId);
        secondData.setId(firstId);

        update(context, firstUri, secondData, false);
        update(context, secondUri, firstData, false);
    }


    static String whereClause(SmsFilterData data) {
        return FilterRules.ACTION + " = " + DatabaseUtils.sqlEscapeString(data.getAction().name())
                + " AND " + whereClauseForOther(FilterRules.SENDER_MODE, data.getSenderPattern().getMode())
                + " AND " + whereClauseForOther(FilterRules.SENDER_PATTERN, data.getSenderPattern().getPattern())
                + " AND " + whereClauseForOther(FilterRules.BODY_MODE, data.getBodyPattern().getMode())
                + " AND " + whereClauseForOther(FilterRules.BODY_PATTERN, data.getBodyPattern().getPattern())
                ;  //whereClauseForIssuer(index.getIssuer())
    }

    private static String whereClauseForOther(String column,String value) {
        if (value != null) {
            return column + " = " + DatabaseUtils.sqlEscapeString(value);
        }
        return column + " IS NULL";
    }

    private static String whereClauseForOther(String column,Enum value) {
        if (value != null) {
            return column + " = " + DatabaseUtils.sqlEscapeString(value.name());
        }
        return column + " IS NULL";
    }

    public Uri update(Context context, Uri filterUri, SmsFilterData filterData, boolean insertIfError) {
        if (filterUri == null && insertIfError) {
            return insert(context, filterData);
        } else if (filterUri == null) {
            throw new IllegalArgumentException("No filter URI provided, failed to write new filter");
        }

        boolean updated = update(context, filterUri, serialize(filterData));
        if (!updated && insertIfError) {
            return insert(context, filterData);
        } else if (!updated) {
            Xlog.w("Filter does not exist, failed to update");
            return null;
        } else {
            return filterUri;
        }
    }

    public SmsFilterData queryAndDelete(Context context, long messageId) {
        Uri filterUri = convertIdToUri(messageId);
        SmsFilterData filterData = query(context, filterUri);
        if (filterData != null) {
            delete(context, filterUri);
        }
        return filterData;
    }

    public boolean replaceAll(Context context, List<SmsFilterData> filters) {
        ArrayList<ContentProviderOperation> ops = new ArrayList<>(filters.size() + 1);
        ops.add(ContentProviderOperation.newDelete(FilterRules.CONTENT_URI).build());
        for (SmsFilterData filter : filters) {
            ContentValues values = serialize(filter);
            ops.add(ContentProviderOperation.newInsert(FilterRules.CONTENT_URI).withValues(values).build());
        }
        try {
            context.getContentResolver().applyBatch(DatabaseContract.AUTHORITY, ops);
            return true;
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        } catch (OperationApplicationException e) {
            return false;
        }
    }
}
