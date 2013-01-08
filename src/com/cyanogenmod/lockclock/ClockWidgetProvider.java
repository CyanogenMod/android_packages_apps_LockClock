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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.cyanogenmod.lockclock.misc.Constants;

public class ClockWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "ClockWidgetProvider";
    private static boolean DEBUG = true;

    // We always start with the clock as true, the rest we set later
    private static boolean mRefreshClock = true;
    private static boolean mRefreshWeather = false;
    private static boolean mRefreshCalendar = false;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        if (DEBUG) {
            Log.d(TAG, "OnUpdate: Refreshing clock = " + mRefreshClock
                    + ", weather = " + mRefreshWeather
                    + " and Calendar = " + mRefreshCalendar);
        }
        updateWidgets(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        if (DEBUG)
            Log.d(TAG, "onReceive: " + intent.toString());

        // Deal with received broadcasts that force a refresh
        String action = intent.getAction() != null ? intent.getAction() : "";
        if (DEBUG)
            Log.d(TAG, "onReceive: Action = " + action);

        if (action.equals("android.appwidget.action.APPWIDGET_ENABLED")) {
            // Initially we only show the clock so only update it
            mRefreshClock = true;
        } else if (action.equals("android.appwidget.action.APPWIDGET_UPDATE_OPTIONS")) {
            // Initially we only show the clock so only update it
            mRefreshClock = true;
            mRefreshCalendar = true;
            mRefreshWeather = true;
        } else if (action.equals(Intent.ACTION_PROVIDER_CHANGED)
                || action.equals(Intent.ACTION_TIME_CHANGED)
                || action.equals(Intent.ACTION_TIMEZONE_CHANGED)
                || action.equals(Intent.ACTION_DATE_CHANGED)
                || action.equals(Intent.ACTION_LOCALE_CHANGED)
                || intent.getBooleanExtra(Constants.REFRESH_CALENDAR, false)) {
            // Calendar, Time or a settings change (excluding weather)
            mRefreshCalendar = true;
        } else if (intent.getBooleanExtra(Constants.REFRESH_WEATHER, false)) {
            // Location or weather settings change
            mRefreshWeather = true;
        } else if (intent.getBooleanExtra(Constants.REFRESH_CLOCK, false)) {
            // Location or weather settings change
            mRefreshClock = true;
        } else {
            // We are not forcing a refresh
            mRefreshClock = false;
            mRefreshCalendar = false;
            mRefreshWeather = false;
            super.onReceive(context, intent);
        }

        // Now call the update
        if (DEBUG) {
            Log.d(TAG, "OnReceive: Refreshing clock = " + mRefreshClock
                    + ", weather = " + mRefreshWeather
                    + " and Calendar = " + mRefreshCalendar);
        }
        updateWidgets(context);
    }

    /**
     *  Update the widget via the service.
     * @param context
     */
    private void updateWidgets(Context context) {
        // Build the intent to call the service
        Intent i = new Intent(context.getApplicationContext(), ClockWidgetService.class);
        if (mRefreshClock) {
            // See if we are forcing a refresh of the clock - its done via settings change
            i.putExtra(Constants.REFRESH_CLOCK, true);
            mRefreshClock = false;
        }
        if (mRefreshWeather) {
            // See if we are forcing a refresh of weather - its done via settings and user click
            i.putExtra(Constants.REFRESH_WEATHER, true);
            mRefreshWeather = false;
        }
        if (mRefreshCalendar) {
            // See if we are forcing a refresh of the calendar - its done via settings change
            i.putExtra(Constants.REFRESH_CALENDAR, true);
            mRefreshCalendar = false;
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