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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Bitmap.Config;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;

import com.cyanogenmod.lockclock.R;

public class WidgetUtils {
    //===============================================================================================
    // Widget display and resizing related functionality
    //===============================================================================================

    /**
     *  Load a resource by Id and overlay with a specified color
     */
    public static Bitmap getOverlaidBitmap(Context context, int resId, int overlayColor) {
        final Resources res = context.getResources();
        final Bitmap src = BitmapFactory.decodeResource(res, resId);
        final Bitmap dest = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Config.ARGB_8888);
        Canvas c = new Canvas(dest);
        final Paint paint = new Paint();

        // Overlay the selected color and set the imageview
        paint.setColorFilter(new PorterDuffColorFilter(overlayColor, PorterDuff.Mode.SRC_ATOP));
        c.drawBitmap(src, 0, 0, paint);
        return dest;
    }

    /**
     *  Decide whether to show the small Weather panel
     */
    public static boolean showSmallWidget(Context context, int id, boolean digitalClock) {
        Bundle options = AppWidgetManager.getInstance(context).getAppWidgetOptions(id);
        if (options == null) {
            // no data to make the calculation, show the list anyway
            return false;
        }
        Resources resources = context.getResources();
        int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
        int minHeightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, minHeight,
                resources.getDisplayMetrics());
        int neededFullSize = (int) resources.getDimension(
                digitalClock ? R.dimen.min_digital_weather_height : R.dimen.min_analog_weather_height);
        int neededSmallSize = (int) resources.getDimension(R.dimen.min_digital_widget_height);

        // Check to see if the widget size is big enough, if it is return true.
        return (minHeightPx < neededFullSize && minHeightPx > neededSmallSize);
    }

    /**
     *  Decide whether to show the full Weather panel
     */
    public static boolean canFitWeather(Context context, int id, boolean digitalClock) {
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
                digitalClock ? R.dimen.min_digital_weather_height : R.dimen.min_analog_weather_height);

        // Check to see if the widget size is big enough, if it is return true.
        return (minHeightPx > neededSize);
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
        return (minHeightPx > neededSize);
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
    public static boolean isTextClockAvailable(){
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1;
    }
}
