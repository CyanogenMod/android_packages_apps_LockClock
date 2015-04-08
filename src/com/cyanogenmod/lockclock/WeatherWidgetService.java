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
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import com.cyanogenmod.lockclock.calendar.CalendarViewsService;
import com.cyanogenmod.lockclock.misc.Constants;
import com.cyanogenmod.lockclock.misc.IconUtils;
import com.cyanogenmod.lockclock.misc.Preferences;
import com.cyanogenmod.lockclock.misc.WidgetUtils;
import com.cyanogenmod.lockclock.weather.WeatherInfo;
import com.cyanogenmod.lockclock.weather.WeatherUpdateService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class WeatherWidgetService extends IntentService {
    private static final String TAG = "WeatherWidgetService";
    private static final boolean D = Constants.DEBUG;

    public static final String ACTION_REFRESH = "com.cyanogenmod.lockclock.action.REFRESH_WIDGET";

    private int[] mWidgetIds;
    private AppWidgetManager mAppWidgetManager;

    public WeatherWidgetService() {
        super("WeatherWidgetService");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        ComponentName thisWidget = new ComponentName(this, WeatherWidgetProvider.class);
        mAppWidgetManager = AppWidgetManager.getInstance(this);
        mWidgetIds = mAppWidgetManager.getAppWidgetIds(thisWidget);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (D) Log.d(TAG, "Got intent " + intent);

        if (mWidgetIds != null && mWidgetIds.length != 0) {
            refreshWidget();
        }
    }

    /**
     * Reload the widget including the Weather forecast
     */
    private void refreshWidget() {
        // Get things ready
        RemoteViews remoteViews;

        // Update the widgets
        for (int id : mWidgetIds) {

            // Determine if its a home or a lock screen widget
            Bundle myOptions = mAppWidgetManager.getAppWidgetOptions (id);
            boolean isKeyguard = false;
            if (WidgetUtils.isTextClockAvailable()) {
                // This is only available on API 17+, make sure we are not calling it on API16
                // This generates an API level Lint warning, ignore it
                int category = myOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, -1);
                isKeyguard = category == AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD;
            }
            if (D) Log.d(TAG, "For Widget id " + id + " isKeyguard is set to " + isKeyguard);

            // Determine which layout to use
            boolean smallWidget = WidgetUtils.showSmallWidget(this, id, true, isKeyguard);
            if (smallWidget) {
                // The small widget is only shown if weather needs to be shown
                // and there is not enough space for the full weather widget and
                // the user had selected to show the weather when minimized (default ON)
                remoteViews = new RemoteViews(getPackageName(), R.layout.weather_panel_small);
            } else {
                remoteViews = new RemoteViews(getPackageName(), R.layout.weather_panel);
            }

            // Hide the Loading indicator
            remoteViews.setViewVisibility(R.id.loading_indicator, View.GONE);

            // Hide the calendar panel if not visible
            remoteViews.setViewVisibility(R.id.calendar_panel, View.GONE);

            // Now, if we need to show the actual weather, do so
                WeatherInfo weatherInfo = Preferences.getCachedWeatherInfo(this);

                if (weatherInfo != null) {
                    setWeatherData(remoteViews, smallWidget, weatherInfo);
                } else {
                    setNoWeatherData(remoteViews, smallWidget);
                }

            remoteViews.setViewVisibility(R.id.weather_panel, View.VISIBLE);

            // Do the update
            mAppWidgetManager.updateAppWidget(id, remoteViews);
        }
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
        String iconsSet = Preferences.getWeatherIconSet(this);

        // Reset no weather visibility
        weatherViews.setViewVisibility(R.id.weather_no_data, View.GONE);
        weatherViews.setViewVisibility(R.id.weather_refresh, View.GONE);

        // Weather Image
        int resId = w.getConditionResource(iconsSet);
        weatherViews.setViewVisibility(R.id.weather_image, View.VISIBLE);
        if (resId != 0) {
            weatherViews.setImageViewResource(R.id.weather_image, w.getConditionResource(iconsSet));
        } else {
            weatherViews.setImageViewBitmap(R.id.weather_image, w.getConditionBitmap(iconsSet, color));
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
        setWeatherClickListener(weatherViews, false);
    }

    /**
     * There is no data to display, display 'empty' fields and the 'Tap to reload' message
     */
    private void setNoWeatherData(RemoteViews weatherViews, boolean smallWidget) {
        int color = Preferences.weatherFontColor(this);
        boolean firstRun = Preferences.isFirstWeatherUpdate(this);

        // Hide the normal weather stuff
        int providerNameResource = Preferences.weatherProvider(this).getNameResourceId();
        String noData = getString(R.string.weather_cannot_reach_provider, getString(providerNameResource));
        weatherViews.setViewVisibility(R.id.weather_image, View.INVISIBLE);
        if (!smallWidget) {
            weatherViews.setViewVisibility(R.id.weather_city, View.GONE);
            weatherViews.setViewVisibility(R.id.update_time, View.GONE);
            weatherViews.setViewVisibility(R.id.weather_temps_panel, View.GONE);
            weatherViews.setViewVisibility(R.id.weather_condition, View.GONE);

            // Set up the no data and refresh indicators
            weatherViews.setTextViewText(R.id.weather_no_data, noData);
            weatherViews.setTextViewText(R.id.weather_refresh, getString(R.string.weather_tap_to_refresh));
            weatherViews.setTextColor(R.id.weather_no_data, color);
            weatherViews.setTextColor(R.id.weather_refresh, color);

            // For a better OOBE, dont show the no_data message if this is the first run
            weatherViews.setViewVisibility(R.id.weather_no_data, firstRun ? View.GONE : View.VISIBLE);
            weatherViews.setViewVisibility(R.id.weather_refresh,  firstRun ? View.GONE : View.VISIBLE);
        } else {
            weatherViews.setTextViewText(R.id.weather_temp, firstRun ? null : noData);
            weatherViews.setTextViewText(R.id.weather_condition, firstRun ? null : getString(R.string.weather_tap_to_refresh));
            weatherViews.setTextColor(R.id.weather_temp, color);
            weatherViews.setTextColor(R.id.weather_condition, color);
        }

        // Register an onClickListener on Weather with the default (Refresh) action
        if (!firstRun) {
            setWeatherClickListener(weatherViews, true);
        }
    }

    private void setWeatherClickListener(RemoteViews weatherViews, boolean forceRefresh) {
        // Register an onClickListener on the Weather panel, default action is show forecast
        PendingIntent pi = null;
        if (forceRefresh) {
            pi = WeatherUpdateService.getUpdateIntent(this, true);
        }

        if (pi == null) {
            Intent i = new Intent(this, WeatherWidgetProvider.class);
            i.setAction(Constants.ACTION_SHOW_FORECAST);
            pi = PendingIntent.getBroadcast(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        weatherViews.setOnClickPendingIntent(R.id.weather_panel, pi);
    }

    public static PendingIntent getRefreshIntent(Context context) {
        Intent i = new Intent(context, WeatherWidgetService.class);
        return PendingIntent.getService(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static void cancelUpdates(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(getRefreshIntent(context));
    }
}
