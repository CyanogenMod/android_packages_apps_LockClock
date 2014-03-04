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
import android.graphics.Color;

import com.cyanogenmod.lockclock.weather.OpenWeatherMapProvider;
import com.cyanogenmod.lockclock.weather.WeatherInfo;
import com.cyanogenmod.lockclock.weather.WeatherProvider;
import com.cyanogenmod.lockclock.weather.YahooWeatherProvider;

import java.util.Calendar;
import java.util.Locale;
import java.util.Set;

public class Preferences {
    private Preferences() {
    }

    public static boolean isFirstWeatherUpdate(Context context) {
        return getPrefs(context).getBoolean(Constants.WEATHER_FIRST_UPDATE, true);
    }

    public static boolean showDigitalClock(Context context) {
        return getPrefs(context).getBoolean(Constants.CLOCK_DIGITAL, true);
    }

    public static boolean showAlarm(Context context) {
        return getPrefs(context).getBoolean(Constants.CLOCK_SHOW_ALARM, true);
    }

    public static boolean showWeather(Context context) {
        return getPrefs(context).getBoolean(Constants.SHOW_WEATHER, true);
    }

    public static boolean showCalendar(Context context) {
        return getPrefs(context).getBoolean(Constants.SHOW_CALENDAR, false);
    }

    public static boolean useBoldFontForHours(Context context) {
        return getPrefs(context).getBoolean(Constants.CLOCK_FONT, false);
    }

    public static boolean useBoldFontForMinutes(Context context) {
        return getPrefs(context).getBoolean(Constants.CLOCK_FONT_MINUTES, false);
    }

    public static boolean useBoldFontForDateAndAlarms(Context context) {
        return getPrefs(context).getBoolean(Constants.CLOCK_FONT_DATE, true);
    }

    public static boolean showAmPmIndicator(Context context) {
        return getPrefs(context).getBoolean(Constants.CLOCK_AM_PM_INDICATOR, false);
    }

    public static int clockFontColor(Context context) {
        int color = Color.parseColor(getPrefs(context).getString(Constants.CLOCK_FONT_COLOR,
                Constants.DEFAULT_LIGHT_COLOR));
        return color;
    }

    public static int clockAlarmFontColor(Context context) {
        int color = Color.parseColor(getPrefs(context).getString(Constants.CLOCK_ALARM_FONT_COLOR,
                Constants.DEFAULT_DARK_COLOR));
        return color;
    }

    public static int weatherFontColor(Context context) {
        int color = Color.parseColor(getPrefs(context).getString(Constants.WEATHER_FONT_COLOR,
                Constants.DEFAULT_LIGHT_COLOR));
        return color;
    }

    public static int weatherTimestampFontColor(Context context) {
        int color = Color.parseColor(getPrefs(context).getString(Constants.WEATHER_TIMESTAMP_FONT_COLOR,
                Constants.DEFAULT_DARK_COLOR));
        return color;
    }

    public static int calendarFontColor(Context context) {
        int color = Color.parseColor(getPrefs(context).getString(Constants.CALENDAR_FONT_COLOR,
                Constants.DEFAULT_LIGHT_COLOR));
        return color;
    }

    public static int calendarDetailsFontColor(Context context) {
        int color = Color.parseColor(getPrefs(context).getString(Constants.CALENDAR_DETAILS_FONT_COLOR,
                Constants.DEFAULT_DARK_COLOR));
        return color;
    }

    public static boolean calendarHighlightUpcomingEvents(Context context) {
        return getPrefs(context).getBoolean(Constants.CALENDAR_HIGHLIGHT_UPCOMING_EVENTS, false);
    }

    public static boolean calendarUpcomingEventsBold(Context context) {
        return getPrefs(context).getBoolean(Constants.CALENDAR_UPCOMING_EVENTS_BOLD, false);
    }

    public static int calendarUpcomingEventsFontColor(Context context) {
        int color = Color.parseColor(getPrefs(context).getString(Constants.CALENDAR_UPCOMING_EVENTS_FONT_COLOR,
                Constants.DEFAULT_LIGHT_COLOR));
        return color;
    }

    public static int calendarUpcomingEventsDetailsFontColor(Context context) {
        int color = Color.parseColor(getPrefs(context).getString(Constants.CALENDAR_UPCOMING_EVENTS_DETAILS_FONT_COLOR,
                Constants.DEFAULT_DARK_COLOR));
        return color;
    }

    public static boolean showWeatherWhenMinimized(Context context) {
        return getPrefs(context).getBoolean(Constants.WEATHER_SHOW_WHEN_MINIMIZED, true);
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

    public static String getWeatherIconSet(Context context) {
        return getPrefs(context).getString(Constants.WEATHER_ICONS, "color");
    }

    public static boolean useMetricUnits(Context context) {
        Locale locale = context.getResources().getConfiguration().locale;
        boolean defValue = !(locale.equals(Locale.US)
                        || locale.toString().equals("ms_MY") // Malaysia
                        || locale.toString().equals("si_LK") // Sri Lanka
                        );
        return getPrefs(context).getBoolean(Constants.WEATHER_USE_METRIC, defValue);
    }

    public static void setUseMetricUnits(Context context, boolean value) {
        getPrefs(context).edit().putBoolean(Constants.WEATHER_USE_METRIC, value).apply();
    }

    public static long weatherRefreshIntervalInMs(Context context) {
        String value = getPrefs(context).getString(Constants.WEATHER_REFRESH_INTERVAL, "60");
        return Long.parseLong(value) * 60 * 1000;
    }

    public static boolean useCustomWeatherLocation(Context context) {
        return getPrefs(context).getBoolean(Constants.WEATHER_USE_CUSTOM_LOCATION, false);
    }

    public static void setUseCustomWeatherLocation(Context context, boolean value) {
        getPrefs(context).edit().putBoolean(Constants.WEATHER_USE_CUSTOM_LOCATION, value).apply();
    }

    public static String customWeatherLocationId(Context context) {
        return getPrefs(context).getString(Constants.WEATHER_CUSTOM_LOCATION_ID, null);
    }

    public static void setCustomWeatherLocationId(Context context, String id) {
        getPrefs(context).edit().putString(Constants.WEATHER_CUSTOM_LOCATION_ID, id).apply();
    }

    public static String customWeatherLocationCity(Context context) {
        return getPrefs(context).getString(Constants.WEATHER_CUSTOM_LOCATION_CITY, null);
    }

    public static void setCustomWeatherLocationCity(Context context, String city) {
        getPrefs(context).edit().putString(Constants.WEATHER_CUSTOM_LOCATION_CITY, city).apply();
    }

    public static WeatherProvider weatherProvider(Context context) {
        String name = getPrefs(context).getString(Constants.WEATHER_SOURCE, "yahoo");
        if (name.equals("openweathermap")) {
            return new OpenWeatherMapProvider(context);
        }
        return new YahooWeatherProvider(context);
    }

    public static void setCachedWeatherInfo(Context context, long timestamp, WeatherInfo data) {
        SharedPreferences.Editor editor = getPrefs(context).edit();
        editor.putLong(Constants.WEATHER_LAST_UPDATE, timestamp);
        if (data != null) {
            // We now have valid weather data to display
            editor.putBoolean(Constants.WEATHER_FIRST_UPDATE, false);
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

    public static String getCachedLocationId(Context context) {
        return getPrefs(context).getString(Constants.WEATHER_LOCATION_ID, null);
    }

    public static void setCachedLocationId(Context context, String id) {
        getPrefs(context).edit().putString(Constants.WEATHER_LOCATION_ID, id).apply();
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

    public static boolean showCalendarIcon(Context context) {
        return getPrefs(context).getBoolean(Constants.CALENDAR_ICON, true);
    }

    public static long lookAheadTimeInMs(Context context) {
        long lookAheadTime;
        String preferenceSetting = getPrefs(context).getString(Constants.CALENDAR_LOOKAHEAD, "1209600000");

        if (preferenceSetting.equals("today")) {
            long now = System.currentTimeMillis();

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 23);
            cal.set(Calendar.MINUTE, 59);
            cal.set(Calendar.SECOND, 59);
            cal.set(Calendar.MILLISECOND, 500);
            long endtimeToday = cal.getTimeInMillis();

            lookAheadTime = endtimeToday - now;
        } else {
            lookAheadTime = Long.parseLong(preferenceSetting);
        }
        return lookAheadTime;
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
