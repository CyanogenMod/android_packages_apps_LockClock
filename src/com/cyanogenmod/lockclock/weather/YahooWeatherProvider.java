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
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;

import com.cyanogenmod.lockclock.weather.WeatherInfo.DayForecast;
import com.cyanogenmod.lockclock.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class YahooWeatherProvider implements WeatherProvider {
    private static final String TAG = "YahooWeatherProvider";

    private static final String URL_WEATHER =
            "http://weather.yahooapis.com/forecastrss?w=%s&u=%s";
    private static final String URL_LOCATION =
            "http://query.yahooapis.com/v1/public/yql?format=json&q=" +
            Uri.encode("select woeid, postal, admin1, admin2, admin3, " +
                    "locality1, locality2, country from geo.places where " +
                    "(placetype = 7 or placetype = 8 or placetype = 9 " +
                    "or placetype = 10 or placetype = 11 or placetype = 20) and text =");
    private static final String URL_PLACEFINDER =
            "http://query.yahooapis.com/v1/public/yql?format=json&q=" +
            Uri.encode("select woeid, city, neighborhood, county from geo.placefinder where " +
                    "gflags=\"R\" and text =");

    private static final String[] LOCALITY_NAMES = new String[] {
        "locality1", "locality2", "admin3", "admin2", "admin1"
    };
    private static final String[] PLACE_NAMES = new String[] {
        "city", "neigborhood", "county"
    };

    private Context mContext;

    public YahooWeatherProvider(Context context) {
        mContext = context;
    }

    @Override
    public int getNameResourceId() {
        return R.string.weather_source_yahoo;
    }

    @Override
    public List<LocationResult> getLocations(String input) {
        String language = getLanguage();
        String params = "\"" + input + "\" and lang = \"" + language + "\"";
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
            Log.e(TAG, "Received malformed places data (input=" + input + ", lang=" + language + ")", e);
        }
        return null;
    }

    @Override
    public WeatherInfo getWeatherInfo(String id, String localizedCityName, boolean metric) {
        String url = String.format(URL_WEATHER, id, metric ? "c" : "f");
        String response = HttpRetriever.retrieve(url);

        if (response == null) {
            return null;
        }

        SAXParserFactory factory = SAXParserFactory.newInstance();
        try {
            SAXParser parser = factory.newSAXParser();
            StringReader reader = new StringReader(response);
            WeatherHandler handler = new WeatherHandler();
            parser.parse(new InputSource(reader), handler);

            if (handler.isComplete()) {
                // There are cases where the current condition is unknown, but the forecast
                // is not - using the (inaccurate) forecast is probably better than showing
                // the question mark
                if (handler.conditionCode == 3200) {
                    handler.condition = handler.forecasts.get(0).condition;
                    handler.conditionCode = handler.forecasts.get(0).conditionCode;
                }

                WeatherInfo w = new WeatherInfo(mContext, id,
                        localizedCityName != null ? localizedCityName : handler.city,
                        handler.condition, handler.conditionCode, handler.temperature,
                        handler.temperatureUnit, handler.humidity, handler.windSpeed,
                        handler.windDirection, handler.speedUnit, handler.forecasts,
                        System.currentTimeMillis());
                Log.d(TAG, "Weather updated: " + w);
                return w;
            } else {
                Log.w(TAG, "Received incomplete weather XML (id=" + id + ")");
            }
        } catch (ParserConfigurationException e) {
            Log.e(TAG, "Could not create XML parser", e);
        } catch (SAXException e) {
            Log.e(TAG, "Could not parse weather XML (id=" + id + ")", e);
        } catch (IOException e) {
            Log.e(TAG, "Could not parse weather XML (id=" + id + ")", e);
        }

        return null;
    }

    private static class WeatherHandler extends DefaultHandler {
        String city;
        String temperatureUnit, speedUnit;
        int windDirection, conditionCode;
        float humidity, temperature, windSpeed;
        String condition;
        ArrayList<DayForecast> forecasts = new ArrayList<DayForecast>();

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
                throws SAXException {
            if (qName.equals("yweather:location")) {
                city = attributes.getValue("city");
            } else if (qName.equals("yweather:units")) {
                temperatureUnit = attributes.getValue("temperature");
                speedUnit = attributes.getValue("speed");
            } else if (qName.equals("yweather:wind")) {
                windDirection = (int) stringToFloat(attributes.getValue("direction"), -1);
                windSpeed = stringToFloat(attributes.getValue("speed"), -1);
            } else if (qName.equals("yweather:atmosphere")) {
                humidity = stringToFloat(attributes.getValue("humidity"), -1);
            } else if (qName.equals("yweather:condition")) {
                condition = attributes.getValue("text");
                conditionCode = (int) stringToFloat(attributes.getValue("code"), -1);
                temperature = stringToFloat(attributes.getValue("temp"), Float.NaN);
            } else if (qName.equals("yweather:forecast")) {
                DayForecast day = new DayForecast(
                        /* low */ stringToFloat(attributes.getValue("low"), Float.NaN),
                        /* high */ stringToFloat(attributes.getValue("high"), Float.NaN),
                        /* condition */ attributes.getValue("text"),
                        /* conditionCode */ (int) stringToFloat(attributes.getValue("code"), -1));
                if (!Float.isNaN(day.low) && !Float.isNaN(day.high) && day.conditionCode >= 0) {
                    forecasts.add(day);
                }
            }
        }
        public boolean isComplete() {
            return temperatureUnit != null && speedUnit != null && conditionCode >= 0
                    && !Float.isNaN(temperature) && !forecasts.isEmpty();
        }
        private float stringToFloat(String value, float defaultValue) {
            try {
                if (value != null) {
                    return Float.parseFloat(value);
                }
            } catch (NumberFormatException e) {
                // fall through to the return line below
            }
            return defaultValue;
        }
    }

    @Override
    public WeatherInfo getWeatherInfo(Location location, boolean metric) {
        String language = getLanguage();
        String params = String.format(Locale.US, "\"%f %f\" and locale=\"%s\"",
                location.getLatitude(), location.getLongitude(), language);
        String url = URL_PLACEFINDER + Uri.encode(params);
        JSONObject results = fetchResults(url);
        if (results == null) {
            return null;
        }

        try {
            JSONObject result = results.getJSONObject("Result");
            String woeid = result.getString("woeid");

            String city = null;
            for (String name : PLACE_NAMES) {
                if (!result.isNull(name)) {
                    city = result.getString(name);
                    if (Log.isLoggable(TAG, Log.VERBOSE)) {
                        Log.v(TAG, String.format(Locale.US, "Placefinder for location %f %f " +
                                "matched %s using %s", location.getLatitude(),
                                location.getLongitude(), city, name));
                    }
                    break;
                }
            }

            // The city name in the placefinder result is HTML encoded :-(
            if (city != null) {
                city = Html.fromHtml(city).toString();
            } else {
                Log.w(TAG, "Can not resolve place name for " + location);
            }

            Log.d(TAG, "Resolved location " + location + " to " + city + " (" + woeid + ")");

            WeatherInfo info = getWeatherInfo(woeid, city, metric);
            if (info != null) {
                return info;
            }
        } catch (JSONException e) {
            Log.e(TAG, "Received malformed placefinder data (location="
                    + location + ", lang=" + language + ")", e);
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

        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "JSON data " + place.toString() + " -> id=" + result.id
                    + ", city=" + result.city + ", country=" + result.countryId);
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
            Log.w(TAG, "Received malformed places data (url=" + url + ")", e);
        }

        return null;
    }

    private String getLanguage() {
        Locale locale = mContext.getResources().getConfiguration().locale;
        String country = locale.getCountry();
        String language = locale.getLanguage();

        if (TextUtils.isEmpty(country)) {
            return language;
        }
        return language + "-" + country;
    }
}
