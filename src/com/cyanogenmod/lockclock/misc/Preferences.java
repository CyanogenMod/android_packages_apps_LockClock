/*
 * Copyright (C) 2012 The CyanogenMod Project
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

import android.content.Context;
import android.content.SharedPreferences;

import com.cyanogenmod.lockclock.weather.WeatherInfo;

import java.util.Set;

public class Preferences {
    private Preferences() {
    }

    public static boolean showDigitalClock(Context context) {
        return getPrefs(context).getBoolean(Constants.CLOCK_DIGITAL, true);
    }
    public static boolean showAlarm(Context context) {
        return getPrefs(context).getBoolean(Constants.CLOCK_SHOW_ALARM, true);
    }
    public static boolean showWeather(Context context) {
        return getPrefs(context).getBoolean(Constants.SHOW_WEATHER, false);
    }
    public static boolean showCalendar(Context context) {
        return getPrefs(context).getBoolean(Constants.SHOW_CALENDAR, false);
    }

    public static boolean useBoldFontForHours(Context context) {
        return getPrefs(context).getBoolean(Constants.CLOCK_FONT, true);
    }
    public static boolean useBoldFontForMinutes(Context context) {
        return getPrefs(context).getBoolean(Constants.CLOCK_FONT_MINUTES, false);
    }
    public static boolean useBoldFontForDateAndAlarms(Context context) {
        return getPrefs(context).getBoolean(Constants.CLOCK_FONT_DATE, true);
    }

    public static boolean showWeatherLocation(Context context) {
        return getPrefs(context).getBoolean(Constants.WEATHER_SHOW_LOCATION, true);
    }
    public static boolean showWeatherTimestamp(Context context) {
        return getPrefs(context).getBoolean(Constants.WEATHER_SHOW_TIMESTAMP, true);
    }
    public static boolean invertLowHighTemperature(Context context) {
        return getPrefs(context).getBoolean(Constants.WEATHER_INVERT_LOWHIGH, false);
    }
    public static boolean useAlternateWeatherIcons(Context context) {
        return getPrefs(context).getBoolean(Constants.WEATHER_USE_ALTERNATE_ICONS, false);
    }
    public static boolean useMetricUnits(Context context) {
        return getPrefs(context).getBoolean(Constants.WEATHER_USE_METRIC, true);
    }
    public static long weatherRefreshIntervalInMs(Context context) {
        String value = getPrefs(context).getString(Constants.WEATHER_REFRESH_INTERVAL, "60");
        return Long.parseLong(value) * 60 * 1000;
    }
    public static boolean useCustomWeatherLocation(Context context) {
        return getPrefs(context).getBoolean(Constants.WEATHER_USE_CUSTOM_LOCATION, false);
    }
    public static String customWeatherLocation(Context context) {
        return getPrefs(context).getString(Constants.WEATHER_CUSTOM_LOCATION_STRING, null);
    }

    public static void setCachedWeatherInfo(Context context, long timestamp, WeatherInfo data) {
        SharedPreferences.Editor editor = getPrefs(context).edit();
        editor.putLong(Constants.WEATHER_LAST_UPDATE, timestamp);
        if (data != null) {
            editor.putString(Constants.WEATHER_DATA, data.toSerializedString());
        }
        editor.apply();
    }
    public static long lastWeatherUpdateTimestamp(Context context) {
        return getPrefs(context).getLong(Constants.WEATHER_LAST_UPDATE, 0);
    }
    public static WeatherInfo getCachedWeatherInfo(Context context) {
        return WeatherInfo.fromSerializedString(context,
                getPrefs(context).getString(Constants.WEATHER_DATA, null));
    }
    public static String getCachedWoeid(Context context) {
        return getPrefs(context).getString(Constants.WEATHER_WOEID, null);
    }
    public static void setCachedWoeid(Context context, String woeid) {
        getPrefs(context).edit().putString(Constants.WEATHER_WOEID, woeid).apply();
    }

    public static Set<String> calendarsToDisplay(Context context) {
        return getPrefs(context).getStringSet(Constants.CALENDAR_LIST, null);
    }
    public static boolean showEventsWithRemindersOnly(Context context) {
        return getPrefs(context).getBoolean(Constants.CALENDAR_REMINDERS_ONLY, false);
    }
    public static boolean showAllDayEvents(Context context) {
        return !getPrefs(context).getBoolean(Constants.CALENDAR_HIDE_ALLDAY, false);
    }
    public static long lookAheadTimeInMs(Context context) {
        return Long.parseLong(getPrefs(context).getString(Constants.CALENDAR_LOOKAHEAD, "10800000"));
    }

    public static final int SHOW_NEVER = 0;
    public static final int SHOW_FIRST_LINE = 1;
    public static final int SHOW_ALWAYS = 2;

    public static int calendarLocationMode(Context context) {
        return Integer.parseInt(getPrefs(context).getString(Constants.CALENDAR_SHOW_LOCATION, "0"));
    }
    public static int calendarDescriptionMode(Context context) {
        return Integer.parseInt(getPrefs(context).getString(Constants.CALENDAR_SHOW_DESCRIPTION, "0"));
    }

    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
    }
}
