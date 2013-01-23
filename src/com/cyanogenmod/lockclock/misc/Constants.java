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

public class Constants {
    public static final boolean DEBUG = false;

    public static final String PREF_NAME = "LockClock";

    // Widget Settings
    public static final String CLOCK_DIGITAL = "clock_digital";
    public static final String CLOCK_FONT = "clock_font";
    public static final String CLOCK_FONT_MINUTES = "clock_font_minutes";
    public static final String CLOCK_FONT_DATE = "clock_font_date";
    public static final String CLOCK_SHOW_ALARM = "clock_show_alarm";

    public static final String SHOW_WEATHER = "show_weather";
    public static final String WEATHER_USE_CUSTOM_LOCATION = "weather_use_custom_location";
    public static final String WEATHER_CUSTOM_LOCATION_STRING = "weather_custom_location_string";
    public static final String WEATHER_SHOW_LOCATION = "weather_show_location";
    public static final String WEATHER_SHOW_TIMESTAMP = "weather_show_timestamp";
    public static final String WEATHER_USE_METRIC = "weather_use_metric";
    public static final String WEATHER_INVERT_LOWHIGH = "weather_invert_lowhigh";
    public static final String WEATHER_REFRESH_INTERVAL = "weather_refresh_interval";
    public static final String WEATHER_USE_ALTERNATE_ICONS = "weather_use_alternate_icons";
    public static final String WEATHER_WOEID = "weather_woeid";

    public static final String SHOW_CALENDAR = "show_calendar";
    public static final String CALENDAR_LIST = "calendar_list";
    public static final String CALENDAR_LOOKAHEAD = "calendar_lookahead";
    public static final String CALENDAR_REMINDERS_ONLY = "calendar_reminders_only";
    public static final String CALENDAR_HIDE_ALLDAY = "calendar_hide_allday";
    public static final String CALENDAR_SHOW_LOCATION = "calendar_show_location";
    public static final String CALENDAR_SHOW_DESCRIPTION = "calendar_show_description";

    // other shared pref entries
    public static final String WEATHER_LAST_UPDATE = "last_weather_update";
    public static final String WEATHER_DATA = "weather_data";

    public static final int MAX_CALENDAR_ITEMS = 3;
}
