package com.crossbowffs.nekosms.filters;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;

import com.crossbowffs.nekosms.BuildConfig;
import com.crossbowffs.nekosms.consts.PreferenceConsts;
import com.crossbowffs.nekosms.data.SmsFilterAction;
import com.crossbowffs.nekosms.data.SmsFilterData;
import com.crossbowffs.nekosms.loader.FilterRuleLoader;
import com.crossbowffs.nekosms.provider.DatabaseContract;
import com.crossbowffs.nekosms.utils.Xlog;
import com.crossbowffs.nekosms.widget.CursorWrapper;
import com.crossbowffs.remotepreferences.RemotePreferences;

import java.util.ArrayList;
import java.util.List;

public class SmsFilterLoader {
    private static final String NEKOSMS_PACKAGE = BuildConfig.APPLICATION_ID;

    private final Context mContext;
    private final ContentObserver mContentObserver;
    private final BroadcastReceiver mBroadcastReceiver;
    private List<SmsFilter> mCachedFilters;

    public SmsFilterLoader(Context context) {
        mContext = context;
        mContentObserver = registerContentObserver();
        mBroadcastReceiver = registerBroadcastReceiver();
    }

    public void close() {
        unregisterContentObserver(mContentObserver);
        unregisterBroadcastReceiver(mBroadcastReceiver);
        invalidateCache();
    }

    public boolean shouldBlockMessage(String sender, String body) {
        List<SmsFilter> filters = getFilters();
        if (filters == null) {
            Xlog.i("Allowing message (filters failed to load)");
            return false;
        }

        // Filters are already sorted whitelist first,
        // so we can just return on the first match.
        for (SmsFilter filter : filters) {
            if (filter.match(sender, body)) {
                switch (filter.getAction()) {
                case ALLOW:
                    Xlog.i("Allowing message (matched whitelist)");
                    return false;
                case BLOCK:
                    Xlog.i("Blocking message (matched blacklist)");
                    return true;
                }
            }
        }

        Xlog.i("Allowing message (did not match any rules)");
        return false;
    }

    private List<SmsFilter> getFilters() {//boolean priority_enable
        List<SmsFilter> filters = mCachedFilters;
        if (filters == null) {
            Xlog.i("Cached SMS filters dirty, loading from database");
            RemotePreferences mPreferences = new RemotePreferences(mContext, PreferenceConsts.REMOTE_PREFS_AUTHORITY, PreferenceConsts.FILE_MAIN, true);
            if (mPreferences.getBoolean(PreferenceConsts.KEY_PRIORITY_ENABLE, PreferenceConsts.KEY_PRIORITY_DEFAULT)) {
                filters = mCachedFilters = loadFilters(DatabaseContract.FilterRules.PRIORITY+" DESC");
            } else {
                //filters = mCachedFilters = loadFilters_Old();
                filters = mCachedFilters = loadFilters(DatabaseContract.FilterRules.ACTION+" ASC");
            }
        }
        return filters;
    }

    private void invalidateCache() {
        mCachedFilters = null;
    }

    //原作者的方法，白名单优先
    private List<SmsFilter> loadFilters_Old() {
        try (CursorWrapper<SmsFilterData> filterCursor = FilterRuleLoader.get().queryAll(mContext)) {
            if (filterCursor == null) {
                // This might occur if the app has been uninstalled (removing the DB),
                // but the user has not rebooted their device yet. We should not filter
                // any messages in this state.
                Xlog.e("Failed to load SMS filters (queryAll returned null)");
                return null;
            }

            int count = filterCursor.getCount();
            Xlog.i("filterCursor.getCount() = %d", count);

            // It's better to just over-reserve since we expect most
            // rules to go into the blacklist, but all rules will
            // be merged into the whitelist list in the end (with
            // whitelist rules coming first).

            // 白名单优先
            ArrayList<SmsFilter> whitelist = new ArrayList<>(count);
            ArrayList<SmsFilter> blacklist = new ArrayList<>(count);

            SmsFilterData data = new SmsFilterData();
            while (filterCursor.moveToNext()) {
                SmsFilter filter;
                try {
                    data = filterCursor.get(data);
                    filter = new SmsFilter(data);
                } catch (Exception e) {
                    Xlog.e("Failed to load SMS filter", e);
                    continue;
                }

                if (data.getAction() == SmsFilterAction.BLOCK) {
                    blacklist.add(filter);
                } else if (data.getAction() == SmsFilterAction.ALLOW) {
                    whitelist.add(filter);
                }
            }

            Xlog.i("Loaded %d blacklist filters", blacklist.size());
            Xlog.i("Loaded %d whitelist filters", whitelist.size());
            whitelist.addAll(blacklist);
            whitelist.trimToSize();
            return whitelist;
        }
    }

    //按action或priority排序。 根据orderBy来对原始查询进行排序
    private List<SmsFilter> loadFilters(String orderBy) {
        try (CursorWrapper<SmsFilterData> filterCursor = FilterRuleLoader.get().queryAll(mContext,null,null, orderBy)) {
            if (filterCursor == null) {
                // This might occur if the app has been uninstalled (removing the DB),
                // but the user has not rebooted their device yet. We should not filter
                // any messages in this state.
                Xlog.e("Failed to load SMS filters (queryAll returned null)");
                return null;
            }

            int count = filterCursor.getCount();
            Xlog.i("filterCursor.getCount() = %d", count);

            // It's better to just over-reserve since we expect most
            // rules to go into the blacklist, but all rules will
            // be merged into the whitelist list in the end (with
            // whitelist rules coming first).

            // (之前)按照_id顺序作为优先级，现在有专门的 PRIORITY 字段了
            ArrayList<SmsFilter> rulelist = new ArrayList<>(count);

            SmsFilterData data = new SmsFilterData();
            while (filterCursor.moveToNext()) {
                SmsFilter filter;
                try {
                    data = filterCursor.get(data);
                    filter = new SmsFilter(data);
                } catch (Exception e) {
                    Xlog.e("Failed to load SMS filter", e);
                    continue;
                }

                rulelist.add(filter);
            }

            Xlog.i("Loaded %d rulelist filters", rulelist.size());
            rulelist.trimToSize();
            return rulelist;
        }
    }

    private ContentObserver registerContentObserver() {
        Xlog.i("Registering SMS filter content observer");

        ContentObserver contentObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                Xlog.i("SMS filter database updated, marking cache as dirty");
                invalidateCache();
            }
        };

        ContentResolver contentResolver = mContext.getContentResolver();
        contentResolver.registerContentObserver(DatabaseContract.FilterRules.CONTENT_URI, true, contentObserver);
        return contentObserver;
    }

    private BroadcastReceiver registerBroadcastReceiver() {
        // It is necessary to listen for these events because uninstalling
        // an app or clearing its data does not notify registered ContentObservers.
        // If the filter cache is not cleared, messages may be unintentionally blocked.
        // A user might be able to get around this by manually modifying the
        // database file itself, but at that point, it's not worth trying to handle.
        // The only other alternative would be to reload the entire filter list every
        // time a SMS is received, which does not scale well to a large number of filters.
        Xlog.i("Registering app package state receiver");

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (!Intent.ACTION_PACKAGE_REMOVED.equals(action) &&
                    !Intent.ACTION_PACKAGE_DATA_CLEARED.equals(action)) {
                    return;
                }

                Uri data = intent.getData();
                if (data == null) {
                    return;
                }

                String packageName = data.getSchemeSpecificPart();
                if (!NEKOSMS_PACKAGE.equals(packageName)) {
                    return;
                }

                if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                    Xlog.i("App uninstalled, resetting filters");
                    invalidateCache();
                } else if (Intent.ACTION_PACKAGE_DATA_CLEARED.equals(action)) {
                    Xlog.i("App data cleared, resetting filters");
                    invalidateCache();
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_DATA_CLEARED);
        filter.addDataScheme("package");
        mContext.registerReceiver(receiver, filter);
        return receiver;
    }

    private void unregisterContentObserver(ContentObserver observer) {
        mContext.getContentResolver().unregisterContentObserver(observer);
    }

    private void unregisterBroadcastReceiver(BroadcastReceiver receiver) {
        mContext.unregisterReceiver(receiver);
    }
}
