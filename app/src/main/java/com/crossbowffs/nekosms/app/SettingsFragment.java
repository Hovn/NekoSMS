package com.crossbowffs.nekosms.app;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import androidx.appcompat.app.AppCompatDelegate;

import com.crossbowffs.nekosms.BuildConfig;
import com.crossbowffs.nekosms.R;
import com.crossbowffs.nekosms.consts.PreferenceConsts;
import com.crossbowffs.nekosms.utils.XposedUtils;

public class SettingsFragment extends PreferenceFragment {
    private static final String NEKOSMS_PACKAGE = BuildConfig.APPLICATION_ID;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // General settings
        addPreferencesFromResource(R.xml.settings);
        // Notification settings
        addPreferencesFromResource(R.xml.settings_notifications);

        //相关特殊处理
        if (!XposedUtils.isModuleEnabled()) {
            Preference enablePreference = findPreference(PreferenceConsts.KEY_ENABLE);
            enablePreference.setEnabled(false);
            enablePreference.setSummary(R.string.pref_enable_summary_alt);
        }

        //切换主题
        findPreference(PreferenceConsts.KEY_THEME_TYPE).setOnPreferenceChangeListener((preference, newValue) -> {
            //int theme_type_value=Integer.parseInt((String) newValue);
            int theme_type_value=AppBaseActivity.ThemeEnum.valueOf(newValue.toString()).value;

            //方式1-建议
            AppCompatDelegate.setDefaultNightMode(theme_type_value);

            //方式2
            //AppBaseActivity AppActivity = (AppBaseActivity) getActivity();
            //AppActivity.getDelegate().setLocalNightMode(theme_type_value);
            //AppActivity.getDelegate().applyDayNight();//该句可以不用

            //方式3
            //AppActivity.recreate();
            //getActivity().recreate();
            return true; //true表示上级的监听器可以继续处理；false反之。
        });

    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        MainActivity activity = ((MainActivity)getActivity());
        activity.disableFab();
        activity.setTitle(R.string.settings);
    }
}
