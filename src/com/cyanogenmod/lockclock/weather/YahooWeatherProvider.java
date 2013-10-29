/*
 * Copyright (C) 2013 The CyanogenMod Project
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
import android.location.Location;
import android.net.Uri;
import android.util.Log;

import com.cyanogenmod.lockclock.misc.Preferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class YahooWeatherProvider implements WeatherProvider {
    private static final String TAG = "YahooWeatherProvider";

    private static final String URL_WEATHER =
            "http://query.yahooapis.com/v1/public/yql?format=json&q=" +
            Uri.encode("select * from weather.forecast where woeid =");
    private static final String URL_LOCATION =
            "http://query.yahooapis.com/v1/public/yql?format=json&q=" +
            Uri.encode("select woeid, postal, admin1, admin2, admin3, " +
                    "locality1, locality2, country from geo.places where " +
                    "(placetype = 7 or placetype = 8 or placetype = 9 " +
                    "or placetype = 10 or placetype = 11) and text =");
    private static final String URL_PLACEFINDER =
            "http://query.yahooapis.com/v1/public/yql?format=json&q=" +
            Uri.encode("select woeid, city from geo.placefinder where gflags=\"R\" and text =");

    private static final String[] LOCALITY_NAMES = new String[] {
        "locality1", "locality2", "admin3", "admin2", "admin1"
    };

    private Context mContext;

    public YahooWeatherProvider(Context context) {
        mContext = context;
    }

    @Override
    public List<LocationResult> getLocations(String input) {
        String locale = mContext.getResources().getConfiguration().locale.getCountry();
        String params = "\"" + input + "\" and lang = \"" + locale + "\"";
        String url = URL_LOCATION + Uri.encode(params);
        JSONObject jsonResults = fetchResults(url);
        if (jsonResults == null) {
            return null;
        }

        try {
            JSONArray places = jsonResults.optJSONArray("place");
            if (places == null) {
                // Yahoo returns an object instead of an array when there's only one result
                places = new JSONArray();
                places.put(jsonResults.getJSONObject("place"));
            }

            ArrayList<LocationResult> results = new ArrayList<LocationResult>();
            for (int i = 0; i < places.length(); i++) {
                LocationResult result = parsePlace(places.getJSONObject(i));
                if (result != null) {
                    results.add(result);
                }
            }
            return results;
        } catch (JSONException e) {
            Log.e(TAG, "Received malformed places data", e);
        }
        return null;
    }

    public WeatherInfo getWeatherInfo(String id, String localizedCityName) {
        String unit = Preferences.useMetricUnits(mContext) ? "c" : "f";
        String params = "\"" + id + "\" and u = \"" + unit + "\"";
        String url = URL_WEATHER + Uri.encode(params);
        String response = HttpRetriever.retrieve(url);

        if (response == null) {
            return null;
        }

        try {
            JSONObject rootObject = new JSONObject(response);
            JSONObject results = rootObject.getJSONObject("query").getJSONObject("results");
            JSONObject data = results.getJSONObject("channel");

            String city = localizedCityName != null
                    ? localizedCityName : data.getJSONObject("location").getString("city");

            JSONObject wind = data.getJSONObject("wind");
            JSONObject units = data.getJSONObject("units");
            JSONObject item = data.getJSONObject("item");
            JSONObject conditions = item.getJSONObject("condition");
            JSONObject forecast = item.getJSONArray("forecast").getJSONObject(0);

            WeatherInfo w = new WeatherInfo(mContext, id, city,
                    /* forecastDate */ conditions.getString("date"),
                    /* condition */ conditions.getString("text"),
                    /* conditionCode */ conditions.getInt("code"),
                    /* temperature */ (float) conditions.getDouble("temp"),
                    /* low */ (float) forecast.getDouble("low"),
                    /* high */ (float) forecast.getDouble("high"),
                    /* tempUnit */ units.getString("temperature"),
                    /* humidity */ (float) data.getJSONObject("atmosphere").getDouble("humidity"),
                    /* wind */ (float) wind.optDouble("speed", -1),
                    /* windDir */ wind.optInt("direction", -1),
                    /* speedUnit */ units.getString("speed"),
                    System.currentTimeMillis());

            Log.d(TAG, "Weather updated: " + w);
            return w;
        } catch (JSONException e) {
            Log.w(TAG, "Weather condition data is invalid.", e);
        }

        return null;
    }

    public WeatherInfo getWeatherInfo(Location location) {
        String locale = mContext.getResources().getConfiguration().locale.getCountry();
        String params = String.format(Locale.US, "\"%f %f\" and lang=\"%s\"",
                location.getLatitude(), location.getLongitude(), locale);
        String url = URL_PLACEFINDER + Uri.encode(params);
        JSONObject results = fetchResults(url);
        if (results == null) {
            return null;
        }

        try {
            JSONObject result = results.getJSONObject("Result");
            String woeid = result.getString("woeid");
            String city = result.getString("city");

            Log.d(TAG, "Resolved location " + location + " to " + city + " (" + woeid + ")");

            WeatherInfo info = getWeatherInfo(woeid, city);
            if (info != null) {
                // cache the result for potential reuse
                // (the placefinder service API is rate limited)
                Preferences.setCachedLocationId(mContext, woeid);
                return info;
            }
        } catch (JSONException e) {
            Log.e(TAG, "Received malformed placefinder data", e);
        }

        return null;
    }

    private LocationResult parsePlace(JSONObject place) throws JSONException {
        LocationResult result = new LocationResult();
        JSONObject country = place.getJSONObject("country");

        result.id = place.getString("woeid");
        result.country = country.getString("content");
        result.countryId = country.getString("code");
        if (!place.isNull("postal")) {
            result.postal = place.getJSONObject("postal").getString("content");
        }

        for (String name : LOCALITY_NAMES) {
            if (!place.isNull(name)) {
                result.city = place.getJSONObject(name).getString("content");
                break;
            }
        }

        if (result.id == null || result.city == null || result.countryId == null) {
            return null;
        }

        return result;
    }

    private JSONObject fetchResults(String url) {
        String response = HttpRetriever.retrieve(url);
        if (response == null) {
            return null;
        }

        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "Request URL is " + url + ", response is " + response);
        }

        try {
            JSONObject rootObject = new JSONObject(response);
            return rootObject.getJSONObject("query").getJSONObject("results");
        } catch (JSONException e) {
            Log.w(TAG, "Received malformed places data", e);
        }

        return null;
    }
};
