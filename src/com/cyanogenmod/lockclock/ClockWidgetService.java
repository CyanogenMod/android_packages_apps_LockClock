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
import android.app.IntentService;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import com.cyanogenmod.lockclock.calendar.CalendarWidgetService;
import com.cyanogenmod.lockclock.misc.Constants;
import com.cyanogenmod.lockclock.misc.Preferences;
import com.cyanogenmod.lockclock.misc.WidgetUtils;
import com.cyanogenmod.lockclock.weather.WeatherInfo;
import com.cyanogenmod.lockclock.weather.WeatherUpdateService;

import java.util.Date;

public class ClockWidgetService extends IntentService {
    private static final String TAG = "ClockWidgetService";
    private static final boolean D = Constants.DEBUG;

    public static final String ACTION_REFRESH = "com.cyanogenmod.lockclock.action.REFRESH_WIDGET";
    public static final String ACTION_REFRESH_CALENDAR = "com.cyanogenmod.lockclock.action.REFRESH_CALENDAR";

    private int[] mWidgetIds;
    private AppWidgetManager mAppWidgetManager;
    public ClockWidgetService() {
        super("ClockWidgetService");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        ComponentName thisWidget = new ComponentName(this, ClockWidgetProvider.class);
        mAppWidgetManager = AppWidgetManager.getInstance(this);
        mWidgetIds = mAppWidgetManager.getAppWidgetIds(thisWidget);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (D) Log.d(TAG, "Got intent " + intent);
        if (intent != null && ACTION_REFRESH_CALENDAR.equals(intent.getAction())) {
            if (D) Log.v(TAG, "Forcing a calendar refresh");
            mAppWidgetManager.notifyAppWidgetViewDataChanged(mWidgetIds, R.id.calendar_list);
        }

        if (mWidgetIds != null && mWidgetIds.length != 0) {
            refreshWidget();
        }
    }

    /**
     * Reload the widget including the Weather forecast, Alarm, Clock font and Calendar
     */
    private void refreshWidget() {
        // Get things ready
        RemoteViews remoteViews;
        boolean digitalClock = Preferences.showDigitalClock(this);
        boolean showWeather = Preferences.showWeather(this);
        boolean showWeatherWhenMinimized = Preferences.showWeatherWhenMinimized(this);
        boolean showCalendar = false;

        // Update the widgets
        for (int id : mWidgetIds) {

            // Determine which layout to use
            boolean smallWidget = showWeather && showWeatherWhenMinimized
                    && WidgetUtils.showSmallWidget(this, id, digitalClock);
            if (smallWidget) {
                // The small widget is only shown if weather needs to be shown
                // and there is not enough space for the full weather widget and
                // the user had selected to show the weather when minimized (default ON)
                remoteViews = new RemoteViews(getPackageName(), R.layout.appwidget_small);
                showCalendar = false;
            } else {
                remoteViews = new RemoteViews(getPackageName(), R.layout.appwidget);
                showCalendar = Preferences.showCalendar(this);
            }

            // Always Refresh the Clock widget
            refreshClock(remoteViews, smallWidget, digitalClock);
            refreshAlarmStatus(remoteViews, smallWidget);

            // Don't bother with Calendar if its not enabled
            if (showCalendar) {
                refreshCalendar(remoteViews, id);
            }

            // Hide the Loading indicator
            remoteViews.setViewVisibility(R.id.loading_indicator, View.GONE);

            // Now, if we need to show the actual weather, do so
            if (showWeather) {
                WeatherInfo weatherInfo = Preferences.getCachedWeatherInfo(this);

                if (weatherInfo != null) {
                    setWeatherData(remoteViews, smallWidget, weatherInfo);
                } else {
                    setNoWeatherData(remoteViews, smallWidget);
                }
            }

            // Resize the clock font if needed
            if (digitalClock) {
                float ratio = WidgetUtils.getScaleRatio(this, id);
                setClockSize(remoteViews, ratio);
            }

            if (showWeather) {
                boolean canFitWeather = smallWidget || WidgetUtils.canFitWeather(this, id, digitalClock);
                remoteViews.setViewVisibility(R.id.weather_panel, canFitWeather ? View.VISIBLE : View.GONE);
            }

            // Hide the calendar panel if there is no space for it
            if (showCalendar) {
                boolean canFitCalendar = WidgetUtils.canFitCalendar(this, id, digitalClock);
                remoteViews.setViewVisibility(R.id.calendar_panel, canFitCalendar ? View.VISIBLE : View.GONE);
            }

            // Do the update
            mAppWidgetManager.updateAppWidget(id, remoteViews);
        }
    }

    //===============================================================================================
    // Clock related functionality
    //===============================================================================================
    private void refreshClock(RemoteViews clockViews, boolean smallWidget, boolean digitalClock) {
        // Analog or Digital clock
        if (digitalClock) {
            // Hours/Minutes is specific to Didital, set it's size
            refreshClockFont(clockViews);
            clockViews.setViewVisibility(R.id.digital_clock, View.VISIBLE);
            clockViews.setViewVisibility(R.id.analog_clock, View.GONE);
        } else {
            clockViews.setViewVisibility(R.id.analog_clock, View.VISIBLE);
            clockViews.setViewVisibility(R.id.digital_clock, View.GONE);
        }

        // Date/Alarm is common to both clocks, set it's size
        refreshDateAlarmFont(clockViews, smallWidget);

        // Register an onClickListener on Clock, starting DeskClock
        ComponentName clk = new ComponentName("com.android.deskclock", "com.android.deskclock.DeskClock");
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(clk);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        clockViews.setOnClickPendingIntent(R.id.clock_panel, pi);
    }

    private void refreshClockFont(RemoteViews clockViews) {
        // Hours
        if (Preferences.useBoldFontForHours(this)) {
            clockViews.setViewVisibility(R.id.clock1_bold, View.VISIBLE);
            clockViews.setViewVisibility(R.id.clock1_regular, View.GONE);
        } else {
            clockViews.setViewVisibility(R.id.clock1_regular, View.VISIBLE);
            clockViews.setViewVisibility(R.id.clock1_bold, View.GONE);
        }

        // Minutes
        if (Preferences.useBoldFontForMinutes(this)) {
            clockViews.setViewVisibility(R.id.clock2_bold, View.VISIBLE);
            clockViews.setViewVisibility(R.id.clock2_regular, View.GONE);
        } else {
            clockViews.setViewVisibility(R.id.clock2_regular, View.VISIBLE);
            clockViews.setViewVisibility(R.id.clock2_bold, View.GONE);
        }
    }

    private void refreshDateAlarmFont(RemoteViews clockViews, boolean smallWidget) {
        // Date and Alarm font
        if (!smallWidget) {
            if (Preferences.useBoldFontForDateAndAlarms(this)) {
                clockViews.setViewVisibility(R.id.date_bold, View.VISIBLE);
                clockViews.setViewVisibility(R.id.date_regular, View.GONE);
            } else {
                clockViews.setViewVisibility(R.id.date_regular, View.VISIBLE);
                clockViews.setViewVisibility(R.id.date_bold, View.GONE);
            }
        } else {
            clockViews.setViewVisibility(R.id.date, View.VISIBLE);
        }

        // Show the panel
        clockViews.setViewVisibility(R.id.date_alarm, View.VISIBLE);
    }

    private void setClockSize(RemoteViews clockViews, float scale) {
        float fontSize = getResources().getDimension(R.dimen.widget_big_font_size);
        clockViews.setTextViewTextSize(R.id.clock1_bold, TypedValue.COMPLEX_UNIT_PX, fontSize * scale);
        clockViews.setTextViewTextSize(R.id.clock1_regular, TypedValue.COMPLEX_UNIT_PX, fontSize * scale);
        clockViews.setTextViewTextSize(R.id.clock2_bold, TypedValue.COMPLEX_UNIT_PX, fontSize * scale);
        clockViews.setTextViewTextSize(R.id.clock2_regular, TypedValue.COMPLEX_UNIT_PX, fontSize * scale);
    }

    //===============================================================================================
    // Alarm related functionality
    //===============================================================================================
    private void refreshAlarmStatus(RemoteViews alarmViews, boolean smallWidget) {
        if (Preferences.showAlarm(this)) {
            String nextAlarm = getNextAlarm();
            if (!TextUtils.isEmpty(nextAlarm)) {
                // An alarm is set, deal with displaying it
                if (!smallWidget) {
                    boolean isBold = Preferences.useBoldFontForDateAndAlarms(this);
                    alarmViews.setTextViewText(isBold ? R.id.nextAlarm_bold : R.id.nextAlarm_regular,
                            nextAlarm.toString().toUpperCase());
                    alarmViews.setViewVisibility(R.id.nextAlarm_bold, isBold ? View.VISIBLE : View.GONE);
                    alarmViews.setViewVisibility(R.id.nextAlarm_regular, isBold ? View.GONE : View.VISIBLE);
                } else {
                    alarmViews.setTextViewText(R.id.nextAlarm, nextAlarm.toString().toUpperCase());
                    alarmViews.setViewVisibility(R.id.nextAlarm, View.VISIBLE);
                }
                return;
            }
        }

        // No alarm set or Alarm display is hidden, hide the views
        if (!smallWidget) {
            alarmViews.setViewVisibility(R.id.nextAlarm_bold, View.GONE);
            alarmViews.setViewVisibility(R.id.nextAlarm_regular, View.GONE);
        } else {
            alarmViews.setViewVisibility(R.id.nextAlarm, View.GONE);
        }
    }

    /**
     * @return A formatted string of the next alarm or null if there is no next alarm.
     */
    private String getNextAlarm() {
        String nextAlarm = Settings.System.getString(
                getContentResolver(), Settings.System.NEXT_ALARM_FORMATTED);
        if (nextAlarm == null || TextUtils.isEmpty(nextAlarm)) {
            return null;
        }
        return nextAlarm;
    }

    //===============================================================================================
    // Weather related functionality
    //===============================================================================================
    /**
     * Display the weather information
     */
    private void setWeatherData(RemoteViews weatherViews, boolean smallWidget, WeatherInfo w) {

        // Weather Image
        weatherViews.setImageViewResource(R.id.weather_image, w.getConditionResource());

        // Weather Condition
        weatherViews.setTextViewText(R.id.weather_condition, w.getCondition());
        weatherViews.setViewVisibility(R.id.weather_condition, View.VISIBLE);

        // Weather Temps Panel
        weatherViews.setTextViewText(R.id.weather_temp, w.getFormattedTemperature());
        weatherViews.setViewVisibility(R.id.weather_temps_panel, View.VISIBLE);

        if (!smallWidget) {
            // Display the full weather information panel items
            // Load the preferences
            boolean showLocation = Preferences.showWeatherLocation(this);
            boolean showTimestamp = Preferences.showWeatherTimestamp(this);

            // City
            weatherViews.setTextViewText(R.id.weather_city, w.getCity());
            weatherViews.setViewVisibility(R.id.weather_city, showLocation ? View.VISIBLE : View.GONE);

            // Weather Update Time
            if (showTimestamp) {
                Date updateTime = w.getTimestamp();
                StringBuilder sb = new StringBuilder();
                sb.append(DateFormat.format("E", updateTime));
                sb.append(" ");
                sb.append(DateFormat.getTimeFormat(this).format(updateTime));
                weatherViews.setTextViewText(R.id.update_time, sb.toString());
                weatherViews.setViewVisibility(R.id.update_time, View.VISIBLE);
            } else {
                weatherViews.setViewVisibility(R.id.update_time, View.GONE);
            }

            // Weather Temps Panel additional items
            boolean invertLowhigh = Preferences.invertLowHighTemperature(this);
            final String low = w.getFormattedLow();
            final String high = w.getFormattedHigh();
            weatherViews.setTextViewText(R.id.weather_low_high, invertLowhigh ? high + " | " + low : low + " | " + high);
        }

        // Register an onClickListener on Weather
        setWeatherClickListener(weatherViews);
    }

    /**
     * There is no data to display, display 'empty' fields and the 'Tap to reload' message
     */
    private void setNoWeatherData(RemoteViews weatherViews, boolean smallWidget) {
        boolean defaultIcons = !Preferences.useAlternateWeatherIcons(this);
        final Resources res = getBaseContext().getResources();

        // Weather Image - Either the default or alternate set
        weatherViews.setImageViewResource(R.id.weather_image,
                defaultIcons ? R.drawable.weather_na : R.drawable.weather2_na);

        if (!smallWidget) {
            weatherViews.setTextViewText(R.id.weather_city, res.getString(R.string.weather_no_data));
            weatherViews.setViewVisibility(R.id.weather_city, View.VISIBLE);
            weatherViews.setViewVisibility(R.id.update_time, View.GONE);
        }

        weatherViews.setViewVisibility(R.id.weather_temps_panel, View.GONE);
        weatherViews.setTextViewText(R.id.weather_condition, res.getString(R.string.weather_tap_to_refresh));

        // Register an onClickListener on Weather
        setWeatherClickListener(weatherViews);
    }

    private void setWeatherClickListener(RemoteViews weatherViews) {
        weatherViews.setOnClickPendingIntent(R.id.weather_panel,
                WeatherUpdateService.getUpdateIntent(this, true));
    }

    //===============================================================================================
    // Calendar related functionality
    //===============================================================================================
    private void refreshCalendar(RemoteViews calendarViews, int widgetId) {
        final Intent intent = new Intent(getBaseContext(), CalendarWidgetService.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
        calendarViews.setRemoteAdapter(widgetId, R.id.calendar_list, intent);
        calendarViews.setEmptyView(R.id.calendar_list, R.id.calendar_empty_view);

        // Register an onClickListener on entries, starting the Calendar app
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        //i.setData(Uri.parse("widgetid" + widgetId));
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        calendarViews.setPendingIntentTemplate(R.id.calendar_list, pi);
    }

    public static PendingIntent getRefreshIntent(Context context) {
        Intent i = new Intent(context, ClockWidgetService.class);
        i.setAction(ClockWidgetService.ACTION_REFRESH_CALENDAR);
        return PendingIntent.getService(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static void cancelUpdates(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(getRefreshIntent(context));
    }
}
