/*
 * Copyright (C) 2013 David van Tonder
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cyanogenmod.lockclock;

import com.cyanogenmod.lockclock.misc.Constants;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

public class WidgetApplication extends Application implements
        Application.ActivityLifecycleCallbacks {
    private static final String TAG = "WidgetApplication";
    private static boolean D = Constants.DEBUG;

    private BroadcastReceiver mTickReceiver = null;
    private BroadcastReceiver mScreenStateReceiver = null;

    @Override
    public void onCreate() {
        registerActivityLifecycleCallbacks(this);
    }

    @Override
    public void onActivityCreated (Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityDestroyed (Activity activity) {
    }

    @Override
    public void onActivityPaused (Activity activity) {
    }

    @Override
    public void onActivityResumed (Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState (Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityStarted (Activity activity) {
    }

    @Override
    public void onActivityStopped (Activity activity) {
    }

    /**
     * BroadReceiver and supporting functions used for handling clock ticks
     * (every minute) for the TextView clock support (API 16)
     */
    public class TickReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Forces a refresh of the widget when the time has ticked
            String action = intent.getAction();
            if(Intent.ACTION_TIME_TICK.equals(action)){
                if (D) Log.d(TAG, "Clock tick action received - triggering widget refresh");
                Intent refreshIntent = new Intent(context, ClockWidgetProvider.class);
                refreshIntent.setAction(ClockWidgetService.ACTION_REFRESH);
                context.sendBroadcast(refreshIntent);
            }
        }
    }

    public void startTickReceiver() {
        // Clean up first, just in case
        stopTickReceiver();

        // Start the new receiver
        if (D) Log.d(TAG, "Starting API16 support: Registering the tick receiver");
        mTickReceiver = new TickReceiver();
        registerReceiver(mTickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
    }

    public void stopTickReceiver() {
        if (mTickReceiver != null) {
            if (D) Log.d(TAG, "Cleaning up: Unregistering the tick receiver");
            unregisterReceiver(mTickReceiver);
            mTickReceiver = null;
        }
    }

    /**
     * BroadReceiver and supporting functions used for handling Screen ON and OFF
     * events for the TextView clock support (API 16), stopping the Tick Receiver
     * while the screen is OFF and restarting it when the screen turns ON.
     * 
     * We also trigger a widget refresh on screen ON since the next tick event could
     * be up to 59 seconds in the future which will leave the clock stuck at the time
     * the screen was last turned off.
     */
    public class ScreenStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                if (D) Log.d(TAG, "Screen went OFF");
                stopTickReceiver();
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                if (D) Log.d(TAG, "Screen went ON");
                startTickReceiver();
                if (D) Log.d(TAG, "Trigger widget refresh");
                Intent refreshIntent = new Intent(context, ClockWidgetProvider.class);
                refreshIntent.setAction(ClockWidgetService.ACTION_REFRESH);
                context.sendBroadcast(refreshIntent);
            }
        }
    }

    public void startScreenStateReceiver() {
        // Clean up first, just in case
        stopScreenStateReceiver();

        // Start the new receiver
        if (D) Log.d(TAG, "Starting API16 support: Registering the screen state receiver");
        mScreenStateReceiver = new ScreenStateReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(mScreenStateReceiver, filter);
    }

    public void stopScreenStateReceiver() {
        if (mScreenStateReceiver != null) {
            if (D) Log.d(TAG, "Cleaning up: Unregistering the screen state receiver");
            unregisterReceiver(mScreenStateReceiver);
            mScreenStateReceiver = null;
        }
    }
}
