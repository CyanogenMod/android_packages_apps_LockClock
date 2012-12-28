package com.cyanogenmod.lockclock;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.cyanogenmod.lockclock.misc.Constants;

public class ClockWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "ClockWidgetProvider";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.i(TAG, "onUpdate Called");

      // Get all ids
      ComponentName thisWidget = new ComponentName(context, ClockWidgetProvider.class);
      int[] allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);

      // Update the widget via the service. Build the intent to call the service on a timer
      Intent intent = new Intent(context.getApplicationContext(), ClockWidgetService.class);
      intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, allWidgetIds);
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
