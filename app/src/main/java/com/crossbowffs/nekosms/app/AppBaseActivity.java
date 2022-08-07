package com.crossbowffs.nekosms.app;

import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.crossbowffs.nekosms.consts.PreferenceConsts;

public abstract class AppBaseActivity extends AppCompatActivity {
    public enum ThemeEnum {
        // 以下是枚举的成员，必须先定义，而且使用分号结束
        DARK("DARK", AppCompatDelegate.MODE_NIGHT_YES),
        LIGHT("LIGHT", AppCompatDelegate.MODE_NIGHT_NO),
        SYSTEM("SYSTEM", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        // 成员变量
        String name;
        int value;

        // 构造方法
        ThemeEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Log.d("AppBaseActivity", "onCreate");
        super.onCreate(savedInstanceState);
        //设置日间/夜间模式
        //AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM     //-1
        //AppCompatDelegate.MODE_NIGHT_NO                // 1
        //AppCompatDelegate.MODE_NIGHT_YES               // 2

        //String theme_type = PreferenceManager.getDefaultSharedPreferences(this).getString(PreferenceConsts.KEY_THEME_TYPE, PreferenceConsts.KEY_THEME_TYPE_DEFAULT);
        //AppCompatDelegate.setDefaultNightMode(Integer.parseInt(theme_type));

        String theme_type_name = PreferenceManager.getDefaultSharedPreferences(this)
                .getString(PreferenceConsts.KEY_THEME_TYPE, PreferenceConsts.KEY_THEME_TYPE_DEFAULT);
        int theme_type_value=AppBaseActivity.ThemeEnum.valueOf(theme_type_name).value;
        AppCompatDelegate.setDefaultNightMode(theme_type_value);
    }

    @Override
    public void recreate() {
        Log.d("AppBaseActivity", "recreate");
        super.recreate();

        //finish();
        //overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        //startActivity(getIntent());
        //overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    // Activity 是否配置 android:configChanges="uiMode" ？
    // 是：调用 onConfigurationChanged()，人工写逻辑处理。
    // 否：调用 recreate()方法，系统自动处理。
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        Log.d("AppBaseActivity", "onConfigurationChanged");
        super.onConfigurationChanged(newConfig);

        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        startActivity(getIntent());
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onNightModeChanged(int mode) {
        Log.d("AppBaseActivity", "onNightModeChanged");
        super.onNightModeChanged(mode);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        Log.d("AppBaseActivity", "onSaveInstanceState");
        super.onSaveInstanceState(outState);
    }
}
