/*
 * Copyright (C) 2012 The CyanogenMod Project
 *
 * * Licensed under the GNU GPLv2 license
 *
 * The text of the license can be found in the LICENSE file
 * or at https://www.gnu.org/licenses/gpl-2.0.txt
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
    public static final String SHOW_WEATHER = "show_weather";
    public static final String WEATHER_USE_CUSTOM_LOCATION = "use_custom_location";
    public static final String WEATHER_CUSTOM_LOCATION_STRING = "custom_location_string";
    public static final String WEATHER_SHOW_LOCATION = "show_location";
    public static final String WEATHER_SHOW_TIMESTAMP = "show_timestamp";
    public static final String WEATHER_USE_METRIC = "use_metric";
    public static final String WEATHER_INVERT_LOWHIGH = "invert_low_high";
    public static final String WEATHER_UPDATE_INTERVAL = "update_interval";
}
