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
        updateWidgets(context, false, false);
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        if (DEBUG)
            Log.d(TAG, "AppWidgetProvider got the intent: " + intent.toString());

        // Deal with received broadcasts that force a refresh
        final String action = intent.getAction();
        if (action.equals(Intent.ACTION_PROVIDER_CHANGED)
                || action.equals(Intent.ACTION_TIME_CHANGED)
                || action.equals(Intent.ACTION_TIMEZONE_CHANGED)
                || action.equals(Intent.ACTION_DATE_CHANGED)
                || intent.getBooleanExtra(Constants.FORCE_REFRESH, false)) {
            // Calendar, Time or a settings change (excluding weather)
            updateWidgets(context, true, false);
        } else if (action.equals(Intent.ACTION_LOCALE_CHANGED)
                || intent.getBooleanExtra(Constants.REFRESH_WEATHER, false)) {
            // Location or weather settings change
            updateWidgets(context, true, true);
        } else {
            // We are not forcing a refresh
            super.onReceive(context, intent);
            updateWidgets(context, false, false);
        }
    }

    private void updateWidgets(Context context, boolean forceRefresh, boolean refreshWeather) {
        // Update the widget via the service. Build the intent to call the service on a timer
        Intent i = new Intent(context.getApplicationContext(), ClockWidgetService.class);
        PendingIntent pi = PendingIntent.getService(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);

        // See if we are forcing a refresh and trigger a single update, include weather if specified
        if (forceRefresh) {
            if (refreshWeather) {
                i.putExtra(Constants.REFRESH_WEATHER, true);
            }
            context.startService(i);
        }

        // Get the minimum refresh interval
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        final long minInterval = Long.parseLong(prefs.getString(Constants.WEATHER_REFRESH_INTERVAL, "60"))
                * 60000; // Interval is stored in hours, we need it in milliseconds

        // Clear any old alarms and schedule the new alarm that only triggers if the device is ON (RTC)
        // TODO: for now force the repeating to be the weather interval but this will need to be changed
        //       once the weather updating becomes a broadcast receiver and the repeating alarm is no
        //       longer needed
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi);
        am.setRepeating(AlarmManager.RTC, System.currentTimeMillis(), minInterval, pi);
    }

    @Override
    public void onDisabled(Context context) {
        // Unsubscribe from all AlarmManager updates
        Intent i = new Intent(context.getApplicationContext(), ClockWidgetService.class);
        PendingIntent pi = PendingIntent.getService(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi);
    }
}