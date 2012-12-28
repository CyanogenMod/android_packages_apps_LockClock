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

import com.cyanogenmod.lockclock.misc.Constants;

public class ClockWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "ClockWidgetProvider";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        updateWidgets(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        updateWidgets(context);
    }

    private void updateWidgets(Context context) {
        // Update the widget via the service. Build the intent to call the service on a timer
        Intent intent = new Intent(context.getApplicationContext(), ClockWidgetService.class);
        PendingIntent pi = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        // Clear any old alarms and schedule the new alarm that only triggers if the device is ON (RTC)
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi);
        am.setRepeating(AlarmManager.RTC, System.currentTimeMillis(), Constants.WIDGET_UPDATE_FREQ, pi);
    }

    @Override
    public void onDeleted (Context context, int[] appWidgetIds) { }

    @Override
    public void onEnabled (Context context) { }

    @Override
    public void onDisabled (Context context) { }

}