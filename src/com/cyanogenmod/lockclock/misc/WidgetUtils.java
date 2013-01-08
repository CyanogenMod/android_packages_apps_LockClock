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
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.TypedValue;

import com.cyanogenmod.lockclock.R;

public class WidgetUtils {
    static final String TAG = "WidgetUtils";

    //===============================================================================================
    // Widget display and resizing related functionality
    //===============================================================================================
    /**
     *  Decide whether to show the Weather panel
     * @param context
     * @param id
     * @param digitalClock
     * @return
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
     * @param context
     * @param id
     * @param digitalClock
     * @return
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
     * @param context
     * @param id
     * @return
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

    //===============================================================================================
    // Calendar event information class
    //===============================================================================================
    /**
     * {@link EventInfo} is a class that represents an event in the widget. It
     * contains all of the data necessary to display that event.
     */
    public static class EventInfo {
        public String description;
        public String title;

        public long id;
        public long start;
        public long end;
        public boolean allDay;

        public EventInfo() {
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("EventInfo [Title=");
            builder.append(title);
            builder.append(", id=");
            builder.append(id);
            builder.append(", description=");
            builder.append(description);
            builder.append("]");
            return builder.toString();
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (allDay ? 1231 : 1237);
            result = prime * result + (int) (id ^ (id >>> 32));
            result = prime * result + (int) (end ^ (end >>> 32));
            result = prime * result + (int) (start ^ (start >>> 32));
            result = prime * result + ((title == null) ? 0 : title.hashCode());
            result = prime * result + ((description == null) ? 0 : description.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            EventInfo other = (EventInfo) obj;
            if (id != other.id)
                return false;
            if (allDay != other.allDay)
                return false;
            if (end != other.end)
                return false;
            if (start != other.start)
                return false;
            if (title == null) {
                if (other.title != null)
                    return false;
            } else if (!title.equals(other.title))
                return false;
            if (description == null) {
                if (other.description != null)
                    return false;
            } else if (!description.equals(other.description)) {
                return false;
            }
            return true;
        }
    }
}