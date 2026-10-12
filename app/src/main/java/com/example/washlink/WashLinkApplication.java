package com.example.washlink;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import com.example.washlink.ui.SystemBarInsets;
import com.example.washlink.ui.BlueHeaderStyler;
import com.example.washlink.ui.NotificationBadgeManager;

public class WashLinkApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) { }

            @Override
            public void onActivityStarted(Activity activity) { }

            @Override
            public void onActivityResumed(Activity activity) {
                SystemBarInsets.apply(activity);
                BlueHeaderStyler.apply(activity);
                NotificationBadgeManager.onActivityResumed(activity);
            }

            @Override
            public void onActivityPaused(Activity activity) {
                NotificationBadgeManager.onActivityPaused(activity);
            }

            @Override
            public void onActivityStopped(Activity activity) { }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }

            @Override
            public void onActivityDestroyed(Activity activity) {
                NotificationBadgeManager.onActivityDestroyed(activity);
            }
        });
    }
}
