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
import java.util.Locale;

public class ClockWidgetService extends IntentService {
    private static final String TAG = "ClockWidgetService";
    private static final boolean D = Constants.DEBUG;

    public static final String ACTION_REFRESH = "com.cyanogenmod.lockclock.action.REFRESH_WIDGET";
    public static final String ACTION_REFRESH_CALENDAR = "com.cyanogenmod.lockclock.action.REFRESH_CALENDAR";
    public static final String ACTION_HIDE_CALENDAR = "com.cyanogenmod.lockclock.action.HIDE_CALENDAR";

    // This needs to be static to persist between refreshes until explicitly changed by an intent
    private static boolean mHideCalendar = false;

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

        if (mWidgetIds != null && mWidgetIds.length != 0) {
            // Check passed in intents
            if (intent != null) {
                if (ACTION_HIDE_CALENDAR.equals(intent.getAction())) {
                    if (D) Log.v(TAG, "Force hiding the calendar panel");
                    // Explicitly hide the panel since we received a broadcast indicating no events
                    mHideCalendar = true;
                } else if (ACTION_REFRESH_CALENDAR.equals(intent.getAction())) {
                    if (D) Log.v(TAG, "Forcing a calendar refresh");
                    // Start with the panel not explicitly hidden
                    // If there are no events, a broadcast to the service will hide the panel
                    mHideCalendar = false;
                    mAppWidgetManager.notifyAppWidgetViewDataChanged(mWidgetIds, R.id.calendar_list);
                }
            }
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

        // Update the widgets
        for (int id : mWidgetIds) {
            boolean showCalendar = false;

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
                // show calendar if enabled and events available and enough space available
                showCalendar = Preferences.showCalendar(this) && !mHideCalendar
                        && WidgetUtils.canFitCalendar(this, id, digitalClock);
            }

            // Hide the Loading indicator
            remoteViews.setViewVisibility(R.id.loading_indicator, View.GONE);

            // Always Refresh the Clock widget
            refreshClock(remoteViews, smallWidget, digitalClock);
            refreshAlarmStatus(remoteViews, smallWidget);

            // Don't bother with Calendar if its not visible
            if (showCalendar) {
                refreshCalendar(remoteViews, id);
            }
            // Hide the calendar panel if not visible
            remoteViews.setViewVisibility(R.id.calendar_panel, showCalendar ? View.VISIBLE : View.GONE);

            boolean canFitWeather = smallWidget || WidgetUtils.canFitWeather(this, id, digitalClock);
            // Now, if we need to show the actual weather, do so
            if (showWeather && canFitWeather) {
                WeatherInfo weatherInfo = Preferences.getCachedWeatherInfo(this);

                if (weatherInfo != null) {
                    setWeatherData(remoteViews, smallWidget, weatherInfo);
                } else {
                    setNoWeatherData(remoteViews, smallWidget);
                }
            }
            remoteViews.setViewVisibility(R.id.weather_panel, (showWeather && canFitWeather) ? View.VISIBLE : View.GONE);

            // Resize the clock font if needed
            if (digitalClock) {
                float ratio = WidgetUtils.getScaleRatio(this, id);
                setClockSize(remoteViews, ratio);
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
        int color = Preferences.clockFontColor(this);

        // Hours
        if (Preferences.useBoldFontForHours(this)) {
            clockViews.setViewVisibility(R.id.clock1_bold, View.VISIBLE);
            clockViews.setViewVisibility(R.id.clock1_regular, View.GONE);
            clockViews.setTextColor(R.id.clock1_bold, color);
        } else {
            clockViews.setViewVisibility(R.id.clock1_regular, View.VISIBLE);
            clockViews.setViewVisibility(R.id.clock1_bold, View.GONE);
            clockViews.setTextColor(R.id.clock1_regular, color);
        }

        // Minutes
        if (Preferences.useBoldFontForMinutes(this)) {
            clockViews.setViewVisibility(R.id.clock2_bold, View.VISIBLE);
            clockViews.setViewVisibility(R.id.clock2_regular, View.GONE);
            clockViews.setTextColor(R.id.clock2_bold, color);
        } else {
            clockViews.setViewVisibility(R.id.clock2_regular, View.VISIBLE);
            clockViews.setViewVisibility(R.id.clock2_bold, View.GONE);
            clockViews.setTextColor(R.id.clock2_regular, color);
        }
    }

    private void refreshDateAlarmFont(RemoteViews clockViews, boolean smallWidget) {
        int color = Preferences.clockFontColor(this);

        // Date and Alarm font
        if (!smallWidget) {
            if (Preferences.useBoldFontForDateAndAlarms(this)) {
                clockViews.setViewVisibility(R.id.date_bold, View.VISIBLE);
                clockViews.setViewVisibility(R.id.date_regular, View.GONE);
                clockViews.setTextColor(R.id.date_bold, color);
            } else {
                clockViews.setViewVisibility(R.id.date_regular, View.VISIBLE);
                clockViews.setViewVisibility(R.id.date_bold, View.GONE);
                clockViews.setTextColor(R.id.date_regular, color);
            }
        } else {
            clockViews.setViewVisibility(R.id.date, View.VISIBLE);
            clockViews.setTextColor(R.id.date, color);
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
                int color = Preferences.clockAlarmFontColor(this);

                // Overlay the selected color on the alarm icon and set the imageview
                alarmViews.setImageViewBitmap(R.id.alarm_icon,
                        WidgetUtils.getOverlaidBitmap(this, R.drawable.ic_alarm_small, color));
                alarmViews.setViewVisibility(R.id.alarm_icon, View.VISIBLE);

                if (!smallWidget) {
                    if (Preferences.useBoldFontForDateAndAlarms(this)) {
                        alarmViews.setTextViewText(R.id.nextAlarm_bold, nextAlarm.toString().toUpperCase(Locale.getDefault()));
                        alarmViews.setViewVisibility(R.id.nextAlarm_bold, View.VISIBLE);
                        alarmViews.setViewVisibility(R.id.nextAlarm_regular, View.GONE);
                        alarmViews.setTextColor(R.id.nextAlarm_bold, color);
                    } else {
                        alarmViews.setTextViewText(R.id.nextAlarm_regular, nextAlarm.toString().toUpperCase(Locale.getDefault()));
                        alarmViews.setViewVisibility(R.id.nextAlarm_regular, View.VISIBLE);
                        alarmViews.setViewVisibility(R.id.nextAlarm_bold, View.GONE);
                        alarmViews.setTextColor(R.id.nextAlarm_regular, color);
                    }
                } else {
                    alarmViews.setTextViewText(R.id.nextAlarm, nextAlarm.toString().toUpperCase(Locale.getDefault()));
                    alarmViews.setViewVisibility(R.id.nextAlarm, View.VISIBLE);
                    alarmViews.setTextColor(R.id.nextAlarm, color);
                }
                return;
            }
        }

        // No alarm set or Alarm display is hidden, hide the views
        alarmViews.setViewVisibility(R.id.alarm_icon, View.GONE);
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
        int color = Preferences.weatherFontColor(this);
        int timestampColor = Preferences.weatherTimestampFontColor(this);
        boolean colorIcons = Preferences.useAlternateWeatherIcons(this);

        // Weather Image
        if (colorIcons) {
            // No additional color overlays needed
            weatherViews.setImageViewResource(R.id.weather_image, w.getConditionResource());
        } else {
            // Overlay the condition image with the appropriate color
            weatherViews.setImageViewBitmap(R.id.weather_image, w.getConditionBitmap(color));
        }

        // Weather Condition
        weatherViews.setTextViewText(R.id.weather_condition, w.getCondition());
        weatherViews.setViewVisibility(R.id.weather_condition, View.VISIBLE);
        weatherViews.setTextColor(R.id.weather_condition, color);

        // Weather Temps Panel
        weatherViews.setTextViewText(R.id.weather_temp, w.getFormattedTemperature());
        weatherViews.setViewVisibility(R.id.weather_temps_panel, View.VISIBLE);
        weatherViews.setTextColor(R.id.weather_temp, color);

        if (!smallWidget) {
            // Display the full weather information panel items
            // Load the preferences
            boolean showLocation = Preferences.showWeatherLocation(this);
            boolean showTimestamp = Preferences.showWeatherTimestamp(this);

            // City
            weatherViews.setTextViewText(R.id.weather_city, w.getCity());
            weatherViews.setViewVisibility(R.id.weather_city, showLocation ? View.VISIBLE : View.GONE);
            weatherViews.setTextColor(R.id.weather_city, color);

            // Weather Update Time
            if (showTimestamp) {
                Date updateTime = w.getTimestamp();
                StringBuilder sb = new StringBuilder();
                sb.append(DateFormat.format("E", updateTime));
                sb.append(" ");
                sb.append(DateFormat.getTimeFormat(this).format(updateTime));
                weatherViews.setTextViewText(R.id.update_time, sb.toString());
                weatherViews.setViewVisibility(R.id.update_time, View.VISIBLE);
                weatherViews.setTextColor(R.id.update_time, timestampColor);
            } else {
                weatherViews.setViewVisibility(R.id.update_time, View.GONE);
            }

            // Weather Temps Panel additional items
            boolean invertLowhigh = Preferences.invertLowHighTemperature(this);
            final String low = w.getFormattedLow();
            final String high = w.getFormattedHigh();
            weatherViews.setTextViewText(R.id.weather_low_high, invertLowhigh ? high + " | " + low : low + " | " + high);
            weatherViews.setTextColor(R.id.weather_low_high, color);
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
        int color = Preferences.weatherFontColor(this);

        // Weather Image - Either the default or alternate set
        weatherViews.setImageViewResource(R.id.weather_image,
                defaultIcons ? R.drawable.weather_na : R.drawable.weather2_na);

        if (!smallWidget) {
            weatherViews.setTextViewText(R.id.weather_city, res.getString(R.string.weather_no_data));
            weatherViews.setViewVisibility(R.id.weather_city, View.VISIBLE);
            weatherViews.setViewVisibility(R.id.update_time, View.GONE);
            weatherViews.setTextColor(R.id.weather_city, color);
        }

        weatherViews.setViewVisibility(R.id.weather_temps_panel, View.GONE);
        weatherViews.setTextViewText(R.id.weather_condition, res.getString(R.string.weather_tap_to_refresh));
        weatherViews.setTextColor(R.id.weather_condition, color);

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
        // Calendar icon: Overlay the selected color and set the imageview
        int color = Preferences.calendarFontColor(this);
        calendarViews.setImageViewBitmap(R.id.calendar_icon,
                WidgetUtils.getOverlaidBitmap(this, R.drawable.ic_lock_idle_calendar, color));

        // Set up and start the Calendar RemoteViews service
        final Intent remoteAdapterIntent = new Intent(this, CalendarWidgetService.class);
        remoteAdapterIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        remoteAdapterIntent.setData(Uri.parse(remoteAdapterIntent.toUri(Intent.URI_INTENT_SCHEME)));
        calendarViews.setRemoteAdapter(R.id.calendar_list, remoteAdapterIntent);
        calendarViews.setEmptyView(R.id.calendar_list, R.id.calendar_empty_view);

        // Register an onClickListener on Calendar starting the Calendar app
        final Intent calendarClickIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR);
        final PendingIntent calendarClickPendingIntent = PendingIntent.getActivity(this, 0, calendarClickIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        calendarViews.setOnClickPendingIntent(R.id.calendar_icon, calendarClickPendingIntent);

        final Intent eventClickIntent = new Intent(Intent.ACTION_VIEW);
        final PendingIntent eventClickPendingIntent = PendingIntent.getActivity(this, 0, eventClickIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        calendarViews.setPendingIntentTemplate(R.id.calendar_list, eventClickPendingIntent);
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
