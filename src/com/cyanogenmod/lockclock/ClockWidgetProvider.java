package com.cyanogenmod.lockclock;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.cyanogenmod.lockclock.misc.Constants;

import java.util.Date;

public class ClockWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "ClockWidgetProvider";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.i(TAG, "onUpdate Called");

      // Get all ids
      ComponentName thisWidget = new ComponentName(context, ClockWidgetProvider.class);
      int[] allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);

      // Build the intent to call the service
      Intent intent = new Intent(context.getApplicationContext(), ClockWidgetService.class);
      intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, allWidgetIds);

      // Update the widget via the service - once only, to get things started
      Log.i(TAG, "Do a single update");
      context.startService(intent);

      // Load the required settings from preferences
      SharedPreferences prefs = context.getSharedPreferences("LockClock", Context.MODE_MULTI_PROCESS);
      int updateFrequency = prefs.getInt(Constants.UPDATE_CHECK_PREF, Constants.UPDATE_FREQ_DEFAULT);

      // If not set to manual updates, handle the ongoing updatesbased on the defined frequency
      if (updateFrequency > 0) {
          Log.i(TAG, "Scheduling future, repeating update checks.");
          scheduleUpdateService(context, intent, updateFrequency * 60000);
      }
    }

    private void scheduleUpdateService(Context context, Intent intent, int updateFrequency) {
        // Load the required settings from preferences
        SharedPreferences prefs = context.getSharedPreferences("LockClock", Context.MODE_MULTI_PROCESS);
        Date lastCheck = new Date(prefs.getLong(Constants.LAST_UPDATE_CHECK_PREF, 0));

        // Get the intent ready
        PendingIntent pi = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        // Clear any old alarms and schedule the new alarm
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi);
        am.setRepeating(AlarmManager.RTC_WAKEUP, lastCheck.getTime() + updateFrequency, updateFrequency, pi);
    }

    @Override
    public void onDeleted (Context context, int[] appWidgetIds) { }

    @Override
    public void onEnabled (Context context) { }

    @Override
    public void onDisabled (Context context) { }

}
