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

        // Initially, when the widget is ENABLED we only show the clock so only update it
        // UPDATE_OPTIONS gets called for a lock screen widget every time the screen turns on
        // but only for a home screen widget on resize. For now, don't do anything
        // special other than updating the clock since the calendar and weather will
        // refresh as needed
        if (action.equals("android.appwidget.action.APPWIDGET_ENABLED")
                || action.equals("android.appwidget.action.APPWIDGET_UPDATE_OPTIONS")) {
            mRefreshClock = true;

        // Calendar, Time or a settings change, force a calendar refresh
        } else if (action.equals(Intent.ACTION_PROVIDER_CHANGED)
                || action.equals(Intent.ACTION_TIME_CHANGED)
                || action.equals(Intent.ACTION_TIMEZONE_CHANGED)
                || action.equals(Intent.ACTION_DATE_CHANGED)
                || action.equals(Intent.ACTION_LOCALE_CHANGED)
                || intent.getBooleanExtra(Constants.REFRESH_CALENDAR, false)) {
            mRefreshCalendar = true;

        // Weather settings change, force a weather refresh
        // TODO: This should also include a location listener
        } else if (intent.getBooleanExtra(Constants.REFRESH_WEATHER, false)) {
            mRefreshWeather = true;

        // Clock settings change, force a clock refresh
        } else if (intent.getBooleanExtra(Constants.REFRESH_CLOCK, false)) {
            mRefreshClock = true;

        // We are not forcing a refresh, normal handling
        } else {
            mRefreshClock = false;
            mRefreshCalendar = false;
            mRefreshWeather = false;
            super.onReceive(context, intent);
        }

        if (DEBUG) {
            Log.d(TAG, "Refreshing clock = " + mRefreshClock
                    + ", weather = " + mRefreshWeather
                    + " and Calendar = " + mRefreshCalendar);
        }

        // Now call the update
        updateWidgets(context);
    }

    /**
     *  Update the widget via the service.
     * @param context
     */
    private void updateWidgets(Context context) {
        // Build the intent to call the service
        Intent i = new Intent(context.getApplicationContext(), ClockWidgetService.class);

        // See if we are forcing a refresh of the clock - its done via settings change
        if (mRefreshClock) {
            i.putExtra(Constants.REFRESH_CLOCK, true);
            mRefreshClock = false;
        }

        // See if we are forcing a refresh of weather - its done via settings and user click
        if (mRefreshWeather) {
            i.putExtra(Constants.REFRESH_WEATHER, true);
            mRefreshWeather = false;
        }

        // See if we are forcing a refresh of the calendar - its done via settings change
        if (mRefreshCalendar) {
            i.putExtra(Constants.REFRESH_CALENDAR, true);
            mRefreshCalendar = false;
        }

        // Start the service. The service itself will take care of scheduling refreshes if needed
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