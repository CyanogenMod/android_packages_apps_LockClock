/*
 * Copyright (C) 2013 The CyanogenMod Project
 * Copyright (C) 2016 The CyanogenMod Project
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

package com.cyanogenmod.yahooweatherprovider;

import android.content.Context;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import cyanogenmod.providers.WeatherContract;
import cyanogenmod.weather.RequestInfo;
import cyanogenmod.weather.WeatherInfo;
import cyanogenmod.weather.WeatherInfo.DayForecast;
import cyanogenmod.weather.WeatherLocation;
import cyanogenmod.weatherservice.ServiceRequest;
import cyanogenmod.weatherservice.ServiceRequestResult;
import cyanogenmod.weatherservice.WeatherProviderService;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class YahooWeatherProviderService extends WeatherProviderService {

    private static final String TAG = "YahooWeatherProvider";

    private static final String URL_WEATHER =
            "https://query.yahooapis.com/v1/public/yql?format=xml&q=" +
            Uri.encode("select * from weather.forecast where woeid=");

    private static final String URL_LOCATION =
            "https://query.yahooapis.com/v1/public/yql?format=json&q=" +
            Uri.encode("select woeid, postal, admin1, admin2, admin3, " +
                    "locality1, locality2, country from geo.places where " +
                    "(placetype = 7 or placetype = 8 or placetype = 9 " +
                    "or placetype = 10 or placetype = 11 or placetype = 20) and text =");
    private static final String URL_PLACEFINDER =
            "https://query.yahooapis.com/v1/public/yql?format=json&q=" +
            Uri.encode("select * from geo.places where " +
                    "text =");

    private static final String[] LOCALITY_NAMES = new String[] {
        "locality1", "locality2", "admin3", "admin2", "admin1"
    };

    private Context mContext;
    private Map<ServiceRequest,WeatherUpdateRequestTask> mWeatherUpdateRequestsMap
            = new HashMap<>();
    private Map<ServiceRequest,LookupCityNameRequestTask> mLookupCityRequestMap = new HashMap<>();

    @Override
    public void onCreate() {
        mContext = getApplicationContext();
    }


    private ArrayList<WeatherLocation> getLocations(String input) {
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

            ArrayList<WeatherLocation> results = new ArrayList<>();
            for (int i = 0; i < places.length(); i++) {
                WeatherLocation result = parsePlace(places.getJSONObject(i));
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
    public void onRequestSubmitted(ServiceRequest request) {
        Log.d(TAG, "New request submitted " + request);
        RequestInfo info = request.getRequestInfo();

        switch (info.getRequestType()) {
            case RequestInfo.TYPE_GEO_LOCATION_REQ:
            case RequestInfo.TYPE_WEATHER_LOCATION_REQ:
                synchronized (mWeatherUpdateRequestsMap) {
                    WeatherUpdateRequestTask weatherTask = new WeatherUpdateRequestTask(request);
                    mWeatherUpdateRequestsMap.put(request, weatherTask);
                    weatherTask.execute();
                }
                break;
            case RequestInfo.TYPE_LOOKUP_CITY_NAME_REQ:
                synchronized (mLookupCityRequestMap) {
                    LookupCityNameRequestTask lookupTask = new LookupCityNameRequestTask(request);
                    mLookupCityRequestMap.put(request, lookupTask);
                    lookupTask.execute();
                }
                break;
        }
    }

    private class WeatherUpdateRequestTask extends AsyncTask<Void, Void, WeatherInfo> {

        final ServiceRequest mRequest;
        public WeatherUpdateRequestTask(ServiceRequest request) {
            mRequest = request;
        }

        @Override
        protected WeatherInfo doInBackground(Void... v) {
            Log.d(TAG, "processing weather update request");
            final RequestInfo info = mRequest.getRequestInfo();
            if (info.getRequestType()
                    == RequestInfo.TYPE_GEO_LOCATION_REQ) {
                return getWeatherInfo(info.getLocation(), info.getTemperatureUnit());
            } else {
                WeatherLocation location = info.getWeatherLocation();
                return getWeatherInfo(location.getCityId(), location.getCity(),
                        info.getTemperatureUnit());
            }
        }

        @Override
        protected void onPostExecute(WeatherInfo weatherInfo) {
            if (weatherInfo == null) {
                mRequest.fail();
            } else {
                ServiceRequestResult result = new ServiceRequestResult.Builder()
                        .setWeatherInfo(weatherInfo)
                        .build();
                mRequest.complete(result);
            }
        }

        @Override
        protected void onCancelled(WeatherInfo weatherInfo) {
            Log.d(TAG, mRequest.toString() + " has been cancelled");
        }
    }

    private class LookupCityNameRequestTask
            extends AsyncTask<Void, Void, ArrayList<WeatherLocation>> {

        final ServiceRequest mRequest;
        public LookupCityNameRequestTask(ServiceRequest request) {
            mRequest = request;
        }

        @Override
        protected ArrayList<WeatherLocation> doInBackground(Void... v) {
            Log.d(TAG, "processing lookup city request ");
            ArrayList<WeatherLocation> locations
                    = getLocations(mRequest.getRequestInfo().getCityName());
            return locations;
        }

        @Override
        protected void onPostExecute(ArrayList<WeatherLocation> locations) {
            ServiceRequestResult request = new ServiceRequestResult.Builder()
                    .setLocationLookupList(locations)
                    .build();
            mRequest.complete(request);
        }

        @Override
        protected void onCancelled(ArrayList<WeatherLocation> result) {
            Log.d(TAG, mRequest.toString() + " has been cancelled");
        }

    }

    private WeatherInfo getWeatherInfo(String id, String localizedCityName, int unit) {
        //TODO Add the parameter to query the weather in the supplied temperature unit
        String url = URL_WEATHER + id;

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
                    handler.conditionCode = handler.forecasts.get(0).getConditionCode();
                }

                WeatherInfo w = new WeatherInfo.Builder(System.currentTimeMillis())
                        .setCity(id, localizedCityName != null ? localizedCityName : handler.city)
                        .setForecast(handler.forecasts)
                        .setHumidity(handler.humidity)
                        .setWeatherCondition(handler.conditionCode)
                        .setTemperature(handler.temperature, handler.temperatureUnit)
                        .setWind(handler.windSpeed, handler.windDirection, handler.windSpeedUnit)
                        .build();
                Log.d(TAG, "Weather successfully retrieved: " + w);
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

    private class WeatherHandler extends DefaultHandler {
        String city;
        int temperatureUnit = INVALID_VALUE;
        int windSpeedUnit = INVALID_VALUE;
        float windDirection = Float.NaN;
        int conditionCode = INVALID_VALUE;
        float humidity = INVALID_VALUE;
        float temperature = Float.NaN;
        float windSpeed = Float.NaN;
        ArrayList<DayForecast> forecasts = new ArrayList<>();
        private static final int INVALID_VALUE = -1;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
                throws SAXException {
            if (qName.equals("yweather:location")) {
                city = attributes.getValue("city");
            } else if (qName.equals("yweather:units")) {
                if (attributes.getValue("temperature").equalsIgnoreCase("f")) {
                    temperatureUnit = WeatherContract.WeatherColumns.TempUnit.FAHRENHEIT;
                } else if (attributes.getValue("temperature").equalsIgnoreCase("c")) {
                    temperatureUnit = WeatherContract.WeatherColumns.TempUnit.CELSIUS;
                } else {
                    temperatureUnit = INVALID_VALUE;
                }

                if (attributes.getValue("speed").equalsIgnoreCase("mph")) {
                    windSpeedUnit = WeatherContract.WeatherColumns.WindSpeedUnit.MPH;
                } else if (attributes.getValue("speed").equalsIgnoreCase("kph")) {
                    windSpeedUnit = WeatherContract.WeatherColumns.WindSpeedUnit.KPH;
                } else {
                    windSpeedUnit = INVALID_VALUE;
                }
            } else if (qName.equals("yweather:wind")) {
                windDirection = stringToFloat(attributes.getValue("direction"), Float.NaN);
                windSpeed = stringToFloat(attributes.getValue("speed"), Float.NaN);
            } else if (qName.equals("yweather:atmosphere")) {
                humidity = stringToFloat(attributes.getValue("humidity"), Float.NaN);
            } else if (qName.equals("yweather:condition")) {
                try {
                    conditionCode = Integer.parseInt(attributes.getValue("code"));
                } catch (NumberFormatException e) {
                    conditionCode = INVALID_VALUE;
                }
                temperature = stringToFloat(attributes.getValue("temp"), Float.NaN);
            } else if (qName.equals("yweather:forecast")) {
                float low;
                float high;
                int conditionCode;
                try {
                    low = Float.parseFloat(attributes.getValue("low"));
                    high = Float.parseFloat(attributes.getValue("high"));
                    conditionCode = Integer.parseInt(attributes.getValue("code"));
                    DayForecast day = new DayForecast.Builder()
                            .setLow(low)
                            .setHigh(high)
                            .setWeatherCondition(conditionCode)
                            .build();
                    forecasts.add(day);
                } catch (NumberFormatException e) {
                    //It's all or nothing. If we can't parse the whole data, won't add the forecast
                }
            }
        }
        public boolean isComplete() {
            return temperatureUnit != INVALID_VALUE && windSpeedUnit != INVALID_VALUE
                    && conditionCode != INVALID_VALUE && !Float.isNaN(temperature)
                        && !forecasts.isEmpty();
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

    private WeatherInfo getWeatherInfo(Location location, int unit) {
        String language = getLanguage();
        String params = String.format(Locale.US, "\"(%f,%f)\" and lang=\"%s\"",
                location.getLatitude(), location.getLongitude(), language);
        String url = URL_PLACEFINDER + Uri.encode(params);
        JSONObject results = fetchResults(url);
        if (results == null) {
            return null;
        }
        try {
            JSONObject place = results.getJSONObject("place");
            WeatherLocation result = parsePlace(place);
            String woeid = null;
            String city = null;
            if (result != null) {
                woeid = result.getCityId();
                city = result.getCity();
            }
            // The city name in the placefinder result is HTML encoded :-(
            if (city != null) {
                city = Html.fromHtml(city).toString();
            } else {
                Log.w(TAG, "Can not resolve place name for " + location);
            }

            Log.d(TAG, "Resolved location " + location + " to " + city + " (" + woeid + ")");

            WeatherInfo info = getWeatherInfo(woeid, city, unit);
            if (info != null) {
                return info;
            }
        } catch (JSONException e) {
            Log.e(TAG, "Received malformed placefinder data (location="
                    + location + ", lang=" + language + ")", e);
        }

        return null;
    }

    private WeatherLocation parsePlace(JSONObject place) throws JSONException {
        Log.d(TAG, "Parsing place");
        JSONObject country = place.getJSONObject("country");

        String cityId = place.getString("woeid");
        String countryName = country.getString("content");
        String countryId  = country.getString("code");
        String zip = null;
        String cityName = null;
        if (!place.isNull("postal")) {
            zip = place.getJSONObject("postal").getString("content");
        }

        for (String name : LOCALITY_NAMES) {
            if (!place.isNull(name)) {
                JSONObject localeObject = place.getJSONObject(name);
                cityName = localeObject.getString("content");
                if (localeObject.optString("woeid") != null) {
                    cityId = localeObject.getString("woeid");
                }
                break;
            }
        }

        Log.d(TAG, "JSON data " + place.toString() + " -> id=" + cityId
                    + ", city=" + cityName + ", country=" + countryId);

        if (cityId == null || cityName == null || countryId == null) {
            return null;
        }

        return new WeatherLocation.Builder(cityId, cityName)
                .setCountry(countryId, countryName)
                .setPostalCode(zip)
                .build();
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

    @Override
    public void onRequestCancelled(ServiceRequest request) {
        switch (request.getRequestInfo().getRequestType()) {
            case RequestInfo.TYPE_GEO_LOCATION_REQ:
            case RequestInfo.TYPE_WEATHER_LOCATION_REQ:
                synchronized (mWeatherUpdateRequestsMap) {
                    WeatherUpdateRequestTask task = mWeatherUpdateRequestsMap.remove(request);
                    if (task != null) {
                        task.cancel(true);
                    }
                }
                return;
            case RequestInfo.TYPE_LOOKUP_CITY_NAME_REQ:
                synchronized (mLookupCityRequestMap) {
                    LookupCityNameRequestTask task = mLookupCityRequestMap.remove(request);
                    if (task != null) {
                        task.cancel(true);
                    }
                }
                return;
        }

    }

    @Override
    public void onConnected() {
        Log.d(TAG, "On service connected!");
    }

    @Override
    public void onDisconnected() {
        Log.d(TAG, "On service disconnected!");
    }

}
