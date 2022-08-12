package com.crossbowffs.nekosms.backup;

import android.content.Context;

import com.crossbowffs.nekosms.data.SmsFilterData;

import org.json.JSONException;
import org.json.JSONObject;

/* package */ class BackupImporterDelegate4 extends BackupImporterDelegate3 {
    public BackupImporterDelegate4(Context context) {
        super(context);
    }

    @Override
    protected SmsFilterData readFilterData(JSONObject filterJson) throws JSONException, InvalidBackupException {
        SmsFilterData data = super.readFilterData(filterJson);
        int priority = filterJson.getInt(BackupConsts.KEY_FILTER_PRIORITY);
        data.setPriority(priority);
        return data;
    }
}
