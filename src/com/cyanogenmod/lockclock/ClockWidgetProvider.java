/*
 * Copyright (C) 2012 The CyanogenMod Project (DvTonder)
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

import static com.cyanogenmod.lockclock.misc.Constants.PREF_NAME;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.cyanogenmod.lockclock.misc.Constants;

public class ClockWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "ClockWidgetProvider";
    private static boolean DEBUG = true;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        updateWidgets(context, false);
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        if (DEBUG)
            Log.d(TAG, "AppWidgetProvider got the intent: " + intent.toString());

        // Deal with received broadcasts that force a refresh
        String action = intent.getAction();
        if (action == null) {
            // Temporary fix for NPE
            action = "";
        }
        if (action.equals(Intent.ACTION_PROVIDER_CHANGED)
                || action.equals(Intent.ACTION_TIME_CHANGED)
                || action.equals(Intent.ACTION_TIMEZONE_CHANGED)
                || action.equals(Intent.ACTION_DATE_CHANGED)
                || intent.getBooleanExtra(Constants.FORCE_REFRESH, false)) {
            // Calendar, Time or a settings change (excluding weather)
            updateWidgets(context, false);
        } else if (action.equals(Intent.ACTION_LOCALE_CHANGED)
                || intent.getBooleanExtra(Constants.REFRESH_WEATHER, false)) {
            // Location or weather settings change
            updateWidgets(context, true);
        } else {
            // We are not forcing a refresh
            super.onReceive(context, intent);
            updateWidgets(context, false);
        }
    }

    private void updateWidgets(Context context, boolean refreshWeather) {
        // Update the widget via the service. Build the intent to call the service on a timer
        Intent i = new Intent(context.getApplicationContext(), ClockWidgetService.class);
        if (refreshWeather) {
            // See if we are forcing a refresh of weather - its done via settings and user click
            i.putExtra(Constants.REFRESH_WEATHER, true);
        }

        // Start the service once, the service itself will take care of scheduling refreshes if needed
        context.startService(i);
    }

    @Override
    public void onDisabled(Context context) {
        // Unsubscribe from all AlarmManager updates if any exist
        Intent i = new Intent(context.getApplicationContext(), ClockWidgetService.class);
        PendingIntent pi = PendingIntent.getService(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi);
    }
}