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

import android.content.Context;
import com.cyanogenmod.lockclock.misc.Preferences;

public class YahooPlaceFinder {

    private static final String YAHOO_API_BASE_REV_URL = "http://where.yahooapis.com/geocode?appid=EKvCnl4k&q=%1$s,+%2$s&gflags=R";
    private static final String YAHOO_API_BASE_URL = "http://where.yahooapis.com/geocode?appid=EKvCnl4k&q=%1$s";

    public static String reverseGeoCode(Context c, double latitude, double longitude) {
        String url = String.format(YAHOO_API_BASE_REV_URL, String.valueOf(latitude),
                String.valueOf(longitude));
        String response = new HttpRetriever().retrieve(url);
        if (response == null) {
            return null;
        }

        String woeid = new WeatherXmlParser(c).parsePlaceFinderResponse(response);
        if (woeid != null) {
            // cache the result for potential reuse - the placefinder service API is rate limited
            Preferences.setCachedWoeid(c, woeid);
        }
        return woeid;
    }

    public static String geoCode(Context c, String location) {
        String url = String.format(YAHOO_API_BASE_URL, location).replace(' ', '+');
        String response = new HttpRetriever().retrieve(url);
        if (response == null) {
            return null;
        }

        String woeid = new WeatherXmlParser(c).parsePlaceFinderResponse(response);
        if (woeid != null) {
            // cache the result for potential reuse - the placefinder service API is rate limited
            Preferences.setCachedWoeid(c, woeid);
        }
        return woeid;
    }
}
