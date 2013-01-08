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
    private static boolean DEBUG = false;

    private boolean mWidgetDeleted = false;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // Default handling, triggered via the super class
        if (DEBUG)
            Log.d(TAG, "Updating widgets, default handling.");
        updateWidgets(context, false, false);
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        // Deal with received broadcasts that force a refresh
        String action = intent.getAction() != null ? intent.getAction() : "";
        if (DEBUG) {
            Log.d(TAG, "Received " + intent.toString());
            Log.d(TAG, "Action = " + action);
        }

        // Initialize some items
        mWidgetDeleted = false;

        // A widget has been deleted, prevent our handling and ask the super class handle it
        if (action.equals("android.appwidget.action.APPWIDGET_DELETED")
                || action.equals("android.appwidget.action.APPWIDGET_DISABLED")) {
            mWidgetDeleted = true;
            super.onReceive(context, intent);

        // Calendar, Time or a settings change, force a calendar refresh
        } else if (action.equals(Intent.ACTION_PROVIDER_CHANGED)
                || action.equals(Intent.ACTION_TIME_CHANGED)
                || action.equals(Intent.ACTION_TIMEZONE_CHANGED)
                || action.equals(Intent.ACTION_DATE_CHANGED)
                || action.equals(Intent.ACTION_LOCALE_CHANGED)
                || intent.getBooleanExtra(Constants.REFRESH_CALENDAR, false)) {
            updateWidgets(context, false, true);

        // Weather settings change, force a weather refresh
        // TODO: This should also include a location listener
        } else if (intent.getBooleanExtra(Constants.REFRESH_WEATHER, false)) {
            updateWidgets(context, true, false);

        // Something we did not handle, let the super class deal with it.
        // This includes the REFRESH_CLOCK intent from Clock settings
        } else {
            if (DEBUG)
                Log.d(TAG, "We did not handle the intent, trigger normal handling");
            super.onReceive(context, intent);
            updateWidgets(context, false, false);
        }
    }

    /**
     *  Update the widget via the service.
     */
    private void updateWidgets(Context context, boolean refreshWeather, boolean refreshCalendar) {
        if (mWidgetDeleted) {
            // We don't handle deletions, the super class does
            return;
        }

        // Build the intent and pass on the weather and calendar refresh triggers
        Intent i = new Intent(context.getApplicationContext(), ClockWidgetService.class);
        i.putExtra(Constants.REFRESH_WEATHER, refreshWeather);
        i.putExtra(Constants.REFRESH_CALENDAR, refreshCalendar);

        // Start the service. The service itself will take care of scheduling refreshes if needed
        if (DEBUG)
            Log.d(TAG, "Starting the service to update the widgets...");
        context.startService(i);
    }

    @Override
    public void onDisabled(Context context) {
        if (DEBUG)
            Log.d(TAG, "Cleaning up: Clearing all pending alarms");

        // Unsubscribe from all AlarmManager updates if any exist
        Intent i = new Intent(context.getApplicationContext(), ClockWidgetService.class);
        PendingIntent pi = PendingIntent.getService(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi);
    }
}