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
import com.cyanogenmod.lockclock.weather.Utils;
import cyanogenmod.weather.WeatherInfo;
import cyanogenmod.weather.WeatherLocation;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.Set;

public class Preferences {

    private static final String WEATHER_LOCATION_CITY_ID = "city_id";
    private static final String WEATHER_LOCATION_CITY_NAME = "city_name";
    private static final String WEATHER_LOCATION_STATE = "state";
    private static final String WEATHER_LOCATION_POSTAL_CODE = "postal_code";
    private static final String WEATHER_LOCATION_COUNTRY_ID = "country_id";
    private static final String WEATHER_LOCATION_COUNTRY_NAME = "country_name";

    private static final String WEATHER_INFO_CITY = "city";
    private static final String WEATHER_INFO_CONDITION_CODE = "condition_code";
    private static final String WEATHER_INFO_TEMPERATURE = "temperature";
    private static final String WEATHER_INFO_TEMPERATURE_UNIT = "temperature_unit";
    private static final String WEATHER_INFO_TIMESTAMP = "timestamp";
    private static final String WEATHER_INFO_HUMIDITY = "humidity";
    private static final String WEATHER_INFO_TODAYS_HIGH = "todays_high";
    private static final String WEATHER_INFO_TODAYS_LOW = "todays_low";
    private static final String WEATHER_INFO_WIND_SPEED = "wind_speed";
    private static final String WEATHER_INFO_WIND_SPEED_UNIT = "wind_speed_unit";
    private static final String WEATHER_INFO_WIND_SPEED_DIRECTION = "wind_speed_direction";
    private static final String WEATHER_INFO_FORECAST = "forecasts";

    private static final String DAY_FORECAST_CONDITION_CODE = "condition_code";
    private static final String DAY_FORECAST_LOW = "low";
    private static final String DAY_FORECAST_HIGH = "high";

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
        return getPrefs(context).getBoolean(Constants.SHOW_WEATHER, true)
                && Utils.isWeatherServiceAvailable(context);
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

    public static int clockBackgroundColor(Context context) {
        int color = Color.parseColor(getPrefs(context).getString(Constants.CLOCK_BACKGROUND_COLOR,
                Constants.DEFAULT_BACKGROUND_COLOR));
        return color;
    }

    public static int clockBackgroundTransparency(Context context) {
        int trans = getPrefs(context).getInt(Constants.CLOCK_BACKGROUND_TRANSPARENCY,
                Constants.DEFAULT_BACKGROUND_TRANSPARENCY);
        return trans;
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
        return Long.parseLong(value) * 60L * 1000L;
    }

    public static boolean useCustomWeatherLocation(Context context) {
        return getPrefs(context).getBoolean(Constants.WEATHER_USE_CUSTOM_LOCATION, false);
    }

    public static void setUseCustomWeatherLocation(Context context, boolean value) {
        getPrefs(context).edit().putBoolean(Constants.WEATHER_USE_CUSTOM_LOCATION, value).apply();
    }

    public static String getCustomWeatherLocationCity(Context context) {
        return getPrefs(context).getString(Constants.WEATHER_CUSTOM_LOCATION_CITY, null);
    }

    public static void setCustomWeatherLocationCity(Context context, String city) {
        getPrefs(context).edit().putString(Constants.WEATHER_CUSTOM_LOCATION_CITY, city).apply();
    }

    public static boolean setCustomWeatherLocation(Context context, WeatherLocation weatherLocation) {
        if (weatherLocation == null) {
            getPrefs(context).edit()
                    .remove(Constants.WEATHER_CUSTOM_LOCATION).apply();
            return true;
        }
        try {
            JSONObject jsonObject = weatherLocationToJSON(weatherLocation);
            getPrefs(context).edit()
                    .putString(Constants.WEATHER_CUSTOM_LOCATION, jsonObject.toString()).apply();
            return true;
        } catch (JSONException e) {
            // We're here because weatherLocationToJSON() or jsonObject.toString() failed.
            // Either way, it means the pref was not updated
            return false;
        }
    }

    public static WeatherLocation getCustomWeatherLocation(Context context) {
        String weatherLocation = getPrefs(context)
                .getString(Constants.WEATHER_CUSTOM_LOCATION, null);

        if (weatherLocation == null) {
            return null;
        }

        try {
            JSONObject jsonObject = new JSONObject(weatherLocation);
            return JSONToWeatherLocation(jsonObject);
        } catch (JSONException e) {
            return null;
        }
    }

    private static WeatherLocation JSONToWeatherLocation(JSONObject jsonObject)
            throws JSONException {
        String cityId;
        String cityName;
        String state;
        String postalCode;
        String countryId;
        String countryName;

        cityId = jsonObject.getString(WEATHER_LOCATION_CITY_ID);
        cityName = jsonObject.getString(WEATHER_LOCATION_CITY_NAME);
        state = jsonObject.getString(WEATHER_LOCATION_STATE);
        postalCode = jsonObject.getString(WEATHER_LOCATION_POSTAL_CODE);
        countryId = jsonObject.getString(WEATHER_LOCATION_COUNTRY_ID);
        countryName = jsonObject.getString(WEATHER_LOCATION_COUNTRY_NAME);

        //We need at least city id and city name to build a WeatherLocation
        if (cityId == null && cityName == null) {
            return null;
        }

        WeatherLocation.Builder location = new WeatherLocation.Builder(cityId, cityName);
        if (countryId != null) location.setCountryId(countryId);
        if (countryName != null) location.setCountry(countryName);
        if (state != null) location.setState(state);
        if (postalCode != null) location.setPostalCode(postalCode);

        return location.build();
    }

    private static JSONObject weatherLocationToJSON(WeatherLocation location) throws JSONException {
        return new JSONObject()
                .put(WEATHER_LOCATION_CITY_ID, location.getCityId())
                .put(WEATHER_LOCATION_CITY_NAME, location.getCity())
                .put(WEATHER_LOCATION_STATE, location.getState())
                .put(WEATHER_LOCATION_POSTAL_CODE, location.getPostalCode())
                .put(WEATHER_LOCATION_COUNTRY_ID, location.getCountryId())
                .put(WEATHER_LOCATION_COUNTRY_NAME, location.getCountry());
    }

    public static void setCachedWeatherInfo(Context context, long timestamp, WeatherInfo info) {
        SharedPreferences.Editor editor = getPrefs(context).edit();
        editor.putLong(Constants.WEATHER_LAST_UPDATE, timestamp);
        if (info != null) {
            // We now have valid weather data to display
            JSONObject jsonObject = new JSONObject();
            boolean serialized = false;
            try {
                //These members always return a value that can be parsed
                jsonObject
                    .put(WEATHER_INFO_CITY, info.getCity())
                    .put(WEATHER_INFO_CONDITION_CODE, info.getConditionCode())
                    .put(WEATHER_INFO_TEMPERATURE, info.getTemperature())
                    .put(WEATHER_INFO_TEMPERATURE_UNIT, info.getTemperatureUnit())
                    .put(WEATHER_INFO_TIMESTAMP, info.getTimestamp());

                // Handle special cases. JSONObject.put(key, double) does not allow
                // Double.NaN, so we store it as a string. JSONObject.getDouble() will parse the
                // "NaN" string and return Double.NaN, which is what we want
                double humidity = info.getHumidity();
                jsonObject.put(WEATHER_INFO_HUMIDITY, Double.isNaN(humidity) ? "NaN" : humidity);

                double todaysHigh = info.getTodaysHigh();
                jsonObject.put(WEATHER_INFO_TODAYS_HIGH, Double.isNaN(todaysHigh)
                        ? "NaN" : todaysHigh);

                double todaysLow = info.getTodaysLow();
                jsonObject.put(WEATHER_INFO_TODAYS_LOW, Double.isNaN(todaysLow)
                        ? "NaN" : todaysLow);

                double windSpeed = info.getWindSpeed();
                double windDirection = info.getWindDirection();
                jsonObject.put(WEATHER_INFO_WIND_SPEED, Double.isNaN(windSpeed) ? "NaN" : windSpeed)
                        .put(WEATHER_INFO_WIND_SPEED_UNIT, info.getWindSpeedUnit())
                        .put(WEATHER_INFO_WIND_SPEED_DIRECTION, Double.isNaN(windDirection)
                                ? "NaN" : windDirection);

                JSONArray forecastArray = new JSONArray();
                for (WeatherInfo.DayForecast forecast : info.getForecasts()) {
                    JSONObject jsonForecast = new JSONObject()
                        .put(DAY_FORECAST_CONDITION_CODE, forecast.getConditionCode());

                    double low = forecast.getLow();
                    jsonForecast.put(DAY_FORECAST_LOW, Double.isNaN(low) ? "NaN" : low);
                    double high = forecast.getHigh();
                    jsonForecast.put(DAY_FORECAST_HIGH, Double.isNaN(high) ? "NaN" : high);
                    forecastArray.put(jsonForecast);
                }
                jsonObject.put(WEATHER_INFO_FORECAST, forecastArray);
                serialized = true;
            } catch (JSONException e) {
                // We're here because something went wrong while creating the JSON object.
                // The code below will check for success and proceed accordingly
            }
            if (serialized) {
                editor.putString(Constants.WEATHER_DATA, jsonObject.toString());
                editor.putBoolean(Constants.WEATHER_FIRST_UPDATE, false);
            }
        } else {
            editor.remove(Constants.WEATHER_DATA);
        }
        editor.apply();
    }

    public static long lastWeatherUpdateTimestamp(Context context) {
        return getPrefs(context).getLong(Constants.WEATHER_LAST_UPDATE, 0);
    }

    public static void setLastWeatherUpadteTimestamp(Context context, long timestamp) {
        getPrefs(context).edit().putLong(Constants.WEATHER_LAST_UPDATE, timestamp).apply();
    }

    public static WeatherInfo getCachedWeatherInfo(Context context) {
        final String cachedInfo = getPrefs(context).getString(Constants.WEATHER_DATA, null);

        if (cachedInfo == null) return null;

        String city;
        int conditionCode;
        double temperature;
        int tempUnit;
        double humidity;
        double windSpeed;
        double windDirection;
        double todaysHigh;
        double todaysLow;
        int windSpeedUnit;
        long timestamp;
        ArrayList<WeatherInfo.DayForecast> forecastList = new ArrayList<>();

        try {
            JSONObject cached = new JSONObject(cachedInfo);
            city = cached.getString(WEATHER_INFO_CITY);
            conditionCode = cached.getInt(WEATHER_INFO_CONDITION_CODE);
            temperature = cached.getDouble(WEATHER_INFO_TEMPERATURE);
            tempUnit = cached.getInt(WEATHER_INFO_TEMPERATURE_UNIT);
            humidity = cached.getDouble(WEATHER_INFO_HUMIDITY);
            windSpeed = cached.getDouble(WEATHER_INFO_WIND_SPEED);
            windDirection = cached.getDouble(WEATHER_INFO_WIND_SPEED_DIRECTION);
            windSpeedUnit = cached.getInt(WEATHER_INFO_WIND_SPEED_UNIT);
            timestamp = cached.getLong(WEATHER_INFO_TIMESTAMP);
            todaysHigh = cached.getDouble(WEATHER_INFO_TODAYS_HIGH);
            todaysLow = cached.getDouble(WEATHER_INFO_TODAYS_LOW);
            JSONArray forecasts = cached.getJSONArray(WEATHER_INFO_FORECAST);
            for (int indx = 0; indx < forecasts.length(); indx++) {
                JSONObject forecast = forecasts.getJSONObject(indx);
                double low;
                double high;
                int code;
                low = forecast.getDouble(DAY_FORECAST_LOW);
                high = forecast.getDouble(DAY_FORECAST_HIGH);
                code = forecast.getInt(DAY_FORECAST_CONDITION_CODE);
                WeatherInfo.DayForecast.Builder f = new WeatherInfo.DayForecast.Builder(code);
                if (!Double.isNaN(low)) f.setLow(low);
                if (!Double.isNaN(high)) f.setHigh(high);
                forecastList.add(f.build());
            }
            WeatherInfo.Builder weatherInfo = new WeatherInfo.Builder(city, temperature, tempUnit)
                    .setWeatherCondition(conditionCode)
                    .setTimestamp(timestamp);

            if (!Double.isNaN(humidity)) weatherInfo.setHumidity(humidity);
            if (!Double.isNaN(windSpeed) && !Double.isNaN(windDirection)) {
                weatherInfo.setWind(windSpeed, windDirection, windSpeedUnit);
            }
            if (forecastList.size() > 0) weatherInfo.setForecast(forecastList);
            if (!Double.isNaN(todaysHigh)) weatherInfo.setTodaysHigh(todaysHigh);
            if (!Double.isNaN(todaysLow)) weatherInfo.setTodaysLow(todaysLow);
            return weatherInfo.build();
        } catch (JSONException e) {
        }
        return null;
    }

    public static void setWeatherSource(Context context, String source) {
        getPrefs(context).edit().putString(Constants.WEATHER_SOURCE, source).apply();
    }

    public static String getWeatherSource(Context context) {
        return getPrefs(context).getString(Constants.WEATHER_SOURCE, null);
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
