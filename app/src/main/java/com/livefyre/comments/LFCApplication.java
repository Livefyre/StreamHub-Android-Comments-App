package com.livefyre.comments;

import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import com.livefyre.comments.activities.SplashActivity;
import com.squareup.otto.Bus;
import livefyre.streamhub.LivefyreConfig;

public class LFCApplication extends Application {

    private SharedPreferences sharedPreferences;
    SharedPreferences.Editor editor;
    Bus mBus;

    @Override
    public void onCreate() {
        super.onCreate();
        LivefyreConfig.setLivefyreNetworkID(LFSConfig.NETWORK_ID);
        AppSingleton.getInstance().setApplication(this);
        init();
        boolean isFirstTime = Boolean.parseBoolean(getDataFromSharedPrefs(LFSAppConstants.IS_FIRST_TIME_STR, LFSAppConstants.IS_FIRST_TIME));
        if (isFirstTime) {
            ShortcutIcon();
            putDataInSharedPref(LFSAppConstants.IS_FIRST_TIME_STR, LFSAppConstants.IS_NOT_FIRST_TIME);
        }
    }

    private void init() {
        sharedPreferences = getApplicationContext().getSharedPreferences(
                LFSAppConstants.SHARED_PREFERENCES, MODE_PRIVATE);
        mBus=new Bus();
    }

    public void putDataInSharedPref(String key, String value) {
        (sharedPreferences.edit()).putString(key, value).commit();
    }

    public String getDataFromSharedPrefs(String key, String defaultVal) {
        return sharedPreferences.getString(key, defaultVal);
    }

    public void clearDataInSharedPref(String key) {
        (sharedPreferences.edit()).remove(key).commit();
    }

    public String getErrorStringFromResourceCode(int resourceCode) {
        return getResources().getText(resourceCode).toString();
    }

    public void printLog(boolean print, String tag, String value) {
        if (print)
            Log.d(tag, value);
    }

    private void ShortcutIcon() {

        Intent shortcutIntent = new Intent(getApplicationContext(), SplashActivity.class);
        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        Intent addIntent = new Intent();
        addIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        addIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, "Livefyre Comments");
        addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(getApplicationContext(), R.drawable.splash));
        addIntent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
        getApplicationContext().sendBroadcast(addIntent);
    }
    public  Bus getBus(){
        return mBus;
    }
}
