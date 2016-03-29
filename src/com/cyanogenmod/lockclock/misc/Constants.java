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

package com.cyanogenmod.lockclock.misc;

import android.text.format.DateUtils;

public class Constants {
    public static final boolean DEBUG = false;

    public static final String PREF_NAME = "LockClock";

    // Widget Settings
    public static final String CLOCK_DIGITAL = "clock_digital";
    public static final String CLOCK_FONT = "clock_font";
    public static final String CLOCK_FONT_MINUTES = "clock_font_minutes";
    public static final String CLOCK_FONT_DATE = "clock_font_date";
    public static final String CLOCK_SHOW_ALARM = "clock_show_alarm";
    public static final String CLOCK_FONT_COLOR = "clock_font_color";
    public static final String CLOCK_ALARM_FONT_COLOR = "clock_alarm_font_color";
    public static final String CLOCK_BACKGROUND_COLOR = "clock_background_color";
    public static final String CLOCK_BACKGROUND_TRANSPARENCY = "clock_background_transparency";
    public static final String CLOCK_AM_PM_INDICATOR = "clock_am_pm_indicator";

    public static final String SHOW_WEATHER = "show_weather";
    public static final String WEATHER_SOURCE = "weather_source";
    public static final String WEATHER_USE_CUSTOM_LOCATION = "weather_use_custom_location";
    public static final String WEATHER_CUSTOM_LOCATION_CITY = "weather_custom_location_city";
    public static final String WEATHER_CUSTOM_LOCATION = "weather_custom_location";
    public static final String WEATHER_LOCATION = "weather_location";
    public static final String WEATHER_SHOW_LOCATION = "weather_show_location";
    public static final String WEATHER_SHOW_TIMESTAMP = "weather_show_timestamp";
    public static final String WEATHER_USE_METRIC = "weather_use_metric";
    public static final String WEATHER_INVERT_LOWHIGH = "weather_invert_lowhigh";
    public static final String WEATHER_REFRESH_INTERVAL = "weather_refresh_interval";
    public static final String WEATHER_SHOW_WHEN_MINIMIZED = "weather_show_when_minimized";
    public static final String WEATHER_FONT_COLOR = "weather_font_color";
    public static final String WEATHER_TIMESTAMP_FONT_COLOR = "weather_timestamp_font_color";
    public static final String WEATHER_ICONS = "weather_icons";
    public static final String MONOCHROME = "mono";
    public static final String COLOR_STD = "color";
    public static final String SHOW_CALENDAR = "show_calendar";
    public static final String CALENDAR_LIST = "calendar_list";
    public static final String CALENDAR_LOOKAHEAD = "calendar_lookahead";
    public static final String CALENDAR_REMINDERS_ONLY = "calendar_reminders_only";
    public static final String CALENDAR_HIDE_ALLDAY = "calendar_hide_allday";
    public static final String CALENDAR_ICON="calendar_icon";
    public static final String CALENDAR_SHOW_LOCATION = "calendar_show_location";
    public static final String CALENDAR_SHOW_DESCRIPTION = "calendar_show_description";
    public static final String CALENDAR_FONT_COLOR = "calendar_font_color";
    public static final String CALENDAR_DETAILS_FONT_COLOR = "calendar_details_font_color";
    public static final String CALENDAR_HIGHLIGHT_UPCOMING_EVENTS = "calendar_highlight_upcoming_events";
    public static final String CALENDAR_UPCOMING_EVENTS_BOLD = "calendar_highlight_upcoming_events_bold";
    public static final String CALENDAR_UPCOMING_EVENTS_FONT_COLOR = "calendar_highlight_upcoming_events_font_color";
    public static final String CALENDAR_UPCOMING_EVENTS_DETAILS_FONT_COLOR = "calendar_highlight_upcoming_events_details_font_color";

    // other shared pref entries
    public static final String WEATHER_LAST_UPDATE = "last_weather_update";
    public static final String WEATHER_DATA = "weather_data";

    // First run is used to hide the initial no-weather message for a better OOBE
    public static final String WEATHER_FIRST_UPDATE = "weather_first_update";

    public static final int MAX_CALENDAR_ITEMS = 30;
    public static final long CALENDAR_UPCOMING_EVENTS_FROM_HOUR = 20L;
    public static final int CALENDAR_FORMAT_TIME =
            DateUtils.FORMAT_SHOW_TIME
            | DateUtils.FORMAT_NO_NOON
            | DateUtils.FORMAT_NO_MIDNIGHT;
    public static final int CALENDAR_FORMAT_ABBREV_DATE =
            DateUtils.FORMAT_SHOW_WEEKDAY
            | DateUtils.FORMAT_ABBREV_ALL
            | DateUtils.FORMAT_SHOW_DATE;
    public static final int CALENDAR_FORMAT_ABBREV_DATETIME =
            CALENDAR_FORMAT_ABBREV_DATE
            | CALENDAR_FORMAT_TIME;
    public static final int CALENDAR_FORMAT_ALLDAY = CALENDAR_FORMAT_ABBREV_DATE;
    public static final int CALENDAR_FORMAT_TODAY = CALENDAR_FORMAT_TIME;
    public static final int CALENDAR_FORMAT_FUTURE = CALENDAR_FORMAT_ABBREV_DATETIME;

    public static final String DEFAULT_LIGHT_COLOR = "#ffffffff";
    public static final String DEFAULT_DARK_COLOR = "#80ffffff";
    public static final String DEFAULT_BACKGROUND_COLOR = "#00000000";
    public static final int DEFAULT_BACKGROUND_TRANSPARENCY = 0;

    // Intent actions
    public static final String ACTION_SHOW_FORECAST = "com.cyanogenmod.lockclock.action.SHOW_FORECAST";

}
