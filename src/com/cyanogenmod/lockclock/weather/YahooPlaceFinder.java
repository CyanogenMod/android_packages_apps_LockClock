/*
 * Copyright (C) 2013 The CyanogenMod Project (DvTonder)
 * Copyright (C) 2012 The AOKP Project
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

package com.cyanogenmod.lockclock.weather;

import static com.cyanogenmod.lockclock.misc.Constants.PREF_NAME;

import android.content.Context;
import android.content.SharedPreferences;

import com.cyanogenmod.lockclock.misc.Constants;

public class YahooPlaceFinder {

    private static final String YAHOO_API_BASE_REV_URL = "http://where.yahooapis.com/geocode?appid=EKvCnl4k&q=%1$s,+%2$s&gflags=R";
    private static final String YAHOO_API_BASE_URL = "http://where.yahooapis.com/geocode?appid=EKvCnl4k&q=%1$s";

    public static String reverseGeoCode(Context c, double latitude, double longitude, boolean cachedOk) {
        String url = String.format(YAHOO_API_BASE_REV_URL, String.valueOf(latitude),
                String.valueOf(longitude));
        String response = new HttpRetriever().retrieve(url);
        String woeid = new WeatherXmlParser(c).parsePlaceFinderResponse(response);

        // Process the result, storing it if it is valid and retrieving the previous one if not
        // This will allow for the retrieving of weather data even though the Yahoo placefinder
        // service API limit has been exceeded, leading to an invalid XML being returned
        SharedPreferences prefs = c.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if (woeid == null && cachedOk) {
            // Error getting location, see if we have a saved location and use it
            woeid = prefs.getString(Constants.WEATHER_WOEID, null);
        } else {
            // We have a new location, persist it for future use
            prefs.edit().putString(Constants.WEATHER_WOEID, woeid).apply();
        }
        return woeid;
    }

    public static String GeoCode(Context c, String location, boolean cachedOk) {
        String url = String.format(YAHOO_API_BASE_URL, location).replace(' ', '+');
        String response = new HttpRetriever().retrieve(url);
        String woeid = new WeatherXmlParser(c).parsePlaceFinderResponse(response);

        // Process the result, storing it if it is valid and retrieving the previous one if not
        // This will allow for the retrieving of weather data even though the Yahoo placefinder
        // service API limit has been exceeded, leading to an invalid XML being returned
        SharedPreferences prefs = c.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if (woeid == null && cachedOk) {
            // Error getting location, see if we have a saved location and use it
            woeid = prefs.getString(Constants.WEATHER_WOEID, null);
        } else {
            // We have a new location, persist it for future use
            prefs.edit().putString(Constants.WEATHER_WOEID, woeid).apply();
        }
        return woeid;
    }
}
