/*
 * Copyright (C) 2012 The Android Open Source Project
 * Portions Copyright (C) 2012 The CyanogenMod Project
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

package com.cyanogenmod.lockclock.misc;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;

import com.cyanogenmod.lockclock.R;

public class WidgetUtils {
    //===============================================================================================
    // Widget display and resizing related functionality
    //===============================================================================================

    private static final String TAG = "WidgetUtils";
    private static final boolean D = Constants.DEBUG;

    /**
     *  Decide whether to show the small Weather panel
     */
    public static boolean showSmallWidget(Context context, int id, boolean digitalClock, boolean isKeyguard) {
        Bundle options = AppWidgetManager.getInstance(context).getAppWidgetOptions(id);
        if (options == null) {
            // no data to make the calculation, show the list anyway
            return false;
        }
        Resources resources = context.getResources();
        int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
        int minHeightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, minHeight,
                resources.getDisplayMetrics());
        int neededFullSize = 0;
        if (isKeyguard) {
            neededFullSize = (int) resources.getDimension(
                    digitalClock ? R.dimen.min_digital_weather_height_lock
                                 : R.dimen.min_analog_weather_height_lock);
        } else {
            neededFullSize = (int) resources.getDimension(
                    digitalClock ? R.dimen.min_digital_weather_height
                                 : R.dimen.min_analog_weather_height);
        }
        int neededSmallSize = (int) resources.getDimension(R.dimen.min_digital_widget_height);

        // Check to see if the widget size is big enough, if it is return true.
        Boolean result = minHeightPx < neededFullSize && minHeightPx > neededSmallSize;
        if (D) {
            Log.d(TAG, "showSmallWidget: digital clock = " + digitalClock + " with minHeightPx = " + minHeightPx
                    + " and neededFullSize = " + neededFullSize + " and neededSmallSize = " + neededSmallSize);
            Log.d(TAG, "showsmallWidget result = " + result);
        }
        return result;
    }

    /**
     *  Decide whether to show the full Weather panel
     */
    public static boolean canFitWeather(Context context, int id, boolean digitalClock, boolean isKeyguard) {
        Bundle options = AppWidgetManager.getInstance(context).getAppWidgetOptions(id);
        if (options == null) {
            // no data to make the calculation, show the list anyway
            return true;
        }
        Resources resources = context.getResources();
        int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
        int minHeightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, minHeight,
                resources.getDisplayMetrics());
        int neededSize = 0;
        if (isKeyguard) {
            neededSize = (int) resources.getDimension(
                    digitalClock ? R.dimen.min_digital_weather_height_lock
                                 : R.dimen.min_analog_weather_height_lock);
        } else {
            neededSize = (int) resources.getDimension(
                    digitalClock ? R.dimen.min_digital_weather_height
                                 : R.dimen.min_analog_weather_height);
        }

        // Check to see if the widget size is big enough, if it is return true.
        Boolean result = minHeightPx > neededSize;
        if (D) {
            Log.d(TAG, "canFitWeather: digital clock = " + digitalClock + " with minHeightPx = "
                    + minHeightPx + "  and neededSize = " + neededSize);
            Log.d(TAG, "canFitWeather result = " + result);
        }
        return result;
    }

    /**
     *  Decide whether to show the Calendar panel
     */
    public static boolean canFitCalendar(Context context, int id, boolean digitalClock) {
        Bundle options = AppWidgetManager.getInstance(context).getAppWidgetOptions(id);
        if (options == null) {
            // no data to make the calculation, show the list anyway
            return true;
        }
        Resources resources = context.getResources();
        int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
        int minHeightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, minHeight,
                resources.getDisplayMetrics());
        int neededSize = (int) resources.getDimension(
                digitalClock ? R.dimen.min_digital_calendar_height : R.dimen.min_analog_calendar_height);

        // Check to see if the widget size is big enough, if it is return true.
        Boolean result = minHeightPx > neededSize;
        if (D) {
            if (D) Log.d(TAG, "canFitCalendar: digital clock = " + digitalClock + " with minHeightPx = "
                    + minHeightPx + "  and neededSize = " + neededSize);
            Log.d(TAG, "canFitCalendar result = " + result);
        }
        return result;
    }

    /**
     *  Calculate the scale factor of the fonts in the widget
     */
    public static float getScaleRatio(Context context, int id) {
        Bundle options = AppWidgetManager.getInstance(context).getAppWidgetOptions(id);
        if (options != null) {
            int minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
            if (minWidth == 0) {
                // No data , do no scaling
                return 1f;
            }
            Resources res = context.getResources();
            float ratio = minWidth / res.getDimension(R.dimen.def_digital_widget_width);
            return (ratio > 1) ? 1f : ratio;
        }
        return 1f;
    }

    /**
     *  The following two methods return the default DeskClock intent depending on which
     *  clock package is installed
     *
     *  Copyright 2013 Google Inc.
     */
    private static final String[] CLOCK_PACKAGES = new String[] {
        "com.google.android.deskclock",
        "com.android.deskclock",
    };

    public static Intent getDefaultClockIntent(Context context) {
        PackageManager pm = context.getPackageManager();
        for (String packageName : CLOCK_PACKAGES) {
            try {
                pm.getPackageInfo(packageName, 0);
                return pm.getLaunchIntentForPackage(packageName);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        return null;
    }

    public static Intent getDefaultAlarmsIntent(Context context) {
        PackageManager pm = context.getPackageManager();
        for (String packageName : CLOCK_PACKAGES) {
            try {
                ComponentName cn = new ComponentName(packageName,
                        "com.android.deskclock.AlarmClock");
                pm.getActivityInfo(cn, 0);
                return Intent.makeMainActivity(cn);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        return getDefaultClockIntent(context);
    }

    /**
     *  API level check to see if the new API 17 TextClock is available
     */
    public static boolean isTextClockAvailable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1;
    }

    /**
     *  API level check to see if the new API 19 transparencies are available
     */
    public static boolean isTranslucencyAvailable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    }

    /**
     *  Networking available check
     */
    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        if (info == null || !info.isConnected() || !info.isAvailable()) {
            if (D) Log.d(TAG, "No network connection is available for weather update");
            return false;
        }
        return true;
    }
}
