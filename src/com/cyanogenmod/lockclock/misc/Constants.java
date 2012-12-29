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

    public static final String UPDATE_CHECK_PREF = "pref_update_check";
    public static final String LAST_UPDATE_CHECK_PREF = "pref_last_update_check";

    // Activity start parameters
    public static final String CHECK_FOR_UPDATE = "check_for_update";
    public static final String MANUAL_UPDATE = "manual_update";

    // Update Check items
    public static final int UPDATE_FREQ_MANUAL  = 0;
    public static final int UPDATE_FREQ_DEFAULT = 15;   // Should be 60
    public static final int WIDGET_UPDATE_FREQ = 60000; // every 60 seconds

    // Widget Settings
    public static final String CLOCK_FONT = "clock_font";
    public static final String CLOCK_SHOW_ALARM = "clock_show_alarm";

    public static final String SHOW_WEATHER = "show_weather";
    public static final String WEATHER_USE_CUSTOM_LOCATION = "weather_use_custom_location";
    public static final String WEATHER_CUSTOM_LOCATION_STRING = "weather_custom_location_string";
    public static final String WEATHER_SHOW_LOCATION = "weather_show_location";
    public static final String WEATHER_SHOW_TIMESTAMP = "weather_show_timestamp";
    public static final String WEATHER_USE_METRIC = "weather_use_metric";
    public static final String WEATHER_INVERT_LOWHIGH = "weather_invert_low_high";
    public static final String WEATHER_REFRESH_INTERVAL = "weather_refresh_interval";

    public static final String SHOW_CALENDAR = "show_calendar";
    public static final String CALENDAR_LIST = "calendar_list";
    public static final String CALENDAR_REMINDERS_ONLY = "calendar_reminders_only";
    public static final String CALENDAR_LOOKAHEAD = "calendar_lookahead";
    public static final String CALENDAR_SHOW_LOCATION = "calendar_show_location";
    public static final String CALENDAR_SHOW_DESCRIPTION = "calendar_show_description";
}
