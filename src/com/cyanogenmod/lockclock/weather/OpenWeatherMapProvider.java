package com.cyanogenmod.lockclock.weather;

import java.util.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.location.Location;
import android.net.Uri;
import android.util.Log;

import com.cyanogenmod.lockclock.weather.WeatherInfo.DayForecast;
import com.cyanogenmod.lockclock.R;

public class OpenWeatherMapProvider implements WeatherProvider {
    private static final String TAG = "OpenWeatherMapProvider";

    private static final int FORECAST_DAYS = 5;
    private static final String SELECTION_LOCATION = "lat=%f&lon=%f";
    private static final String SELECTION_ID = "id=%s";
    private static final String APP_ID = "e2b075d68c39dc43e16995653fcd6fd0";

    private static final String URL_LOCATION =
            "http://api.openweathermap.org/data/2.5/find?q=%s&mode=json&lang=%s&appid="
            + APP_ID;
    private static final String URL_WEATHER =
            "http://api.openweathermap.org/data/2.5/weather?%s&mode=json&units=%s&lang=%s&appid="
            + APP_ID;
    private static final String URL_FORECAST =
            "http://api.openweathermap.org/data/2.5/forecast/daily?" +
            "%s&mode=json&units=%s&lang=%s&cnt=" + FORECAST_DAYS + "&appid=" + APP_ID;

    private Context mContext;

    public OpenWeatherMapProvider(Context context) {
        mContext = context;
    }

    @Override
    public int getNameResourceId() {
        return R.string.weather_source_openweathermap;
    }

    @Override
    public List<LocationResult> getLocations(String input) {
        String url = String.format(URL_LOCATION, Uri.encode(input), getLanguageCode());
        String response = HttpRetriever.retrieve(url);
        if (response == null) {
            return null;
        }

        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "URL = " + url + " returning a response of " + response);
        }

        try {
            JSONArray jsonResults = new JSONObject(response).getJSONArray("list");
            ArrayList<LocationResult> results = new ArrayList<LocationResult>();
            int count = jsonResults.length();

            for (int i = 0; i < count; i++) {
                JSONObject result = jsonResults.getJSONObject(i);
                LocationResult location = new LocationResult();

                location.id = result.getString("id");
                location.city = result.getString("name");
                location.countryId = result.getJSONObject("sys").getString("country");
                results.add(location);
            }

            return results;
        } catch (JSONException e) {
            Log.w(TAG, "Received malformed location data (input=" + input + ")", e);
        }

        return null;
    }

    public WeatherInfo getWeatherInfo(String id, String localizedCityName, boolean metric) {
        String selection = String.format(Locale.US, SELECTION_ID, id);
        return handleWeatherRequest(selection, localizedCityName, metric);
    }

    public WeatherInfo getWeatherInfo(Location location, boolean metric) {
        String selection = String.format(Locale.US, SELECTION_LOCATION,
                location.getLatitude(), location.getLongitude());
        return handleWeatherRequest(selection, null, metric);
    }

    private WeatherInfo handleWeatherRequest(String selection,
            String localizedCityName, boolean metric) {
        String units = metric ? "metric" : "imperial";
        String locale = getLanguageCode();
        String conditionUrl = String.format(Locale.US, URL_WEATHER, selection, units, locale);
        String conditionResponse = HttpRetriever.retrieve(conditionUrl);
        if (conditionResponse == null) {
            return null;
        }

        String forecastUrl = String.format(Locale.US, URL_FORECAST, selection, units, locale);
        String forecastResponse = HttpRetriever.retrieve(forecastUrl);
        if (forecastResponse == null) {
            return null;
        }

        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "URL = " + conditionUrl + " returning a response of " + conditionResponse);
        }

        try {
            JSONObject conditions = new JSONObject(conditionResponse);
            JSONObject weather = conditions.getJSONArray("weather").getJSONObject(0);
            JSONObject conditionData = conditions.getJSONObject("main");
            JSONObject windData = conditions.getJSONObject("wind");
            ArrayList<DayForecast> forecasts =
                    parseForecasts(new JSONObject(forecastResponse).getJSONArray("list"), metric);
            int speedUnitResId = metric ? R.string.weather_kph : R.string.weather_mph;
            if (localizedCityName == null) {
                localizedCityName = conditions.getString("name");
            }

            WeatherInfo w = new WeatherInfo(mContext, conditions.getString("id"), localizedCityName,
                    /* condition */ weather.getString("main"),
                    /* conditionCode */ mapConditionIconToCode(
                            weather.getString("icon"), weather.getInt("id")),
                    /* temperature */ sanitizeTemperature(conditionData.getDouble("temp"), metric),
                    /* tempUnit */ metric ? "C" : "F",
                    /* humidity */ (float) conditionData.getDouble("humidity"),
                    /* wind */ (float) windData.getDouble("speed"),
                    /* windDir */ windData.getInt("deg"),
                    /* speedUnit */ mContext.getString(speedUnitResId),
                    forecasts,
                    System.currentTimeMillis());

            Log.d(TAG, "Weather updated: " + w);
            return w;
        } catch (JSONException e) {
            Log.w(TAG, "Received malformed weather data (selection = " + selection
                    + ", lang = " + locale + ")", e);
        }

        return null;
    }

    private ArrayList<DayForecast> parseForecasts(JSONArray forecasts, boolean metric) throws JSONException {
        ArrayList<DayForecast> result = new ArrayList<DayForecast>();
        int count = forecasts.length();

        if (count == 0) {
            throw new JSONException("Empty forecasts array");
        }
        for (int i = 0; i < count; i++) {
            JSONObject forecast = forecasts.getJSONObject(i);
            JSONObject temperature = forecast.getJSONObject("temp");
            JSONObject data = forecast.getJSONArray("weather").getJSONObject(0);
            DayForecast item = new DayForecast(
                    /* low */ sanitizeTemperature(temperature.getDouble("min"), metric),
                    /* high */ sanitizeTemperature(temperature.getDouble("max"), metric),
                    /* condition */ data.getString("main"),
                    /* conditionCode */ mapConditionIconToCode(
                            data.getString("icon"), data.getInt("id")));
            result.add(item);
        }

        return result;
    }

    // OpenWeatherMap sometimes returns temperatures in Kelvin even if we ask it
    // for deg C or deg F. Detect this and convert accordingly.
    private static float sanitizeTemperature(double value, boolean metric) {
        // threshold chosen to work for both C and F. 170 deg F is hotter
        // than the hottest place on earth.
        if (value > 170) {
            // K -> deg C
            value -= 273.15;
            if (!metric) {
                // deg C -> deg F
                value = (value * 1.8) + 32;
            }
        }
        return (float) value;
    }

    private static final HashMap<String, Integer> ICON_MAPPING = new HashMap<String, Integer>();
    static {
        ICON_MAPPING.put("01d", 32);
        ICON_MAPPING.put("01n", 31);
        ICON_MAPPING.put("02d", 30);
        ICON_MAPPING.put("02n", 29);
        ICON_MAPPING.put("03d", 26);
        ICON_MAPPING.put("03n", 26);
        ICON_MAPPING.put("04d", 28);
        ICON_MAPPING.put("04n", 27);
        ICON_MAPPING.put("09d", 12);
        ICON_MAPPING.put("09n", 11);
        ICON_MAPPING.put("10d", 40);
        ICON_MAPPING.put("10n", 45);
        ICON_MAPPING.put("11d", 4);
        ICON_MAPPING.put("11n", 4);
        ICON_MAPPING.put("13d", 16);
        ICON_MAPPING.put("13n", 16);
        ICON_MAPPING.put("50d", 21);
        ICON_MAPPING.put("50n", 20);
    }

    private int mapConditionIconToCode(String icon, int conditionId) {

        // First, use condition ID for specific cases
        switch (conditionId) {
            // Thunderstorms
            case 202:	// thunderstorm with heavy rain
            case 232:	// thunderstorm with heavy drizzle
            case 211:	// thunderstorm
                return 4;
            case 212:	// heavy thunderstorm
                return 3;
            case 221:	// ragged thunderstorm
            case 231:	// thunderstorm with drizzle
            case 201:	// thunderstorm with rain
                return 38;
            case 230:	// thunderstorm with light drizzle
            case 200:	// thunderstorm with light rain
            case 210:	// light thunderstorm
                return 37;

            // Drizzle
            case 300:    // light intensity drizzle
            case 301:	 // drizzle
            case 302:	 // heavy intensity drizzle
            case 310:	 // light intensity drizzle rain
            case 311:	 // drizzle rain
            case 312:	 // heavy intensity drizzle rain
            case 313:	 // shower rain and drizzle
            case 314:	 // heavy shower rain and drizzle
            case 321:    // shower drizzle
                return 9;

            // Rain
            case 500:    // light rain
            case 501:    // moderate rain
            case 520:    // light intensity shower rain
            case 521:    // shower rain
            case 531:    // ragged shower rain
                return 11;
            case 502:    // heavy intensity rain
            case 503:    // very heavy rain
            case 504:    // extreme rain
            case 522:    // heavy intensity shower rain
                return 12;
            case 511:    // freezing rain
                return 10;

            // Snow
            case 600: case 620: return 14; // light snow
            case 601: case 621: return 16; // snow
            case 602: case 622: return 41; // heavy snow
            case 611: case 612:	return 18; // sleet
            case 615: case 616:	return 5;  // rain and snow

            // Atmosphere
            case 741:    // fog
                return 20;
            case 711:    // smoke
            case 762:    // volcanic ash
                return 22;
            case 701:    // mist
            case 721:    // haze
                return 21;
            case 731:    // sand/dust whirls
            case 751:    // sand
            case 761:    // dust
                return 19;
            case 771:    // squalls
                return 23;
            case 781:    // tornado
                return 0;

            // Extreme
            case 900: return 0;  // tornado
            case 901: return 1;  // tropical storm
            case 902: return 2;  // hurricane
            case 903: return 25; // cold
            case 904: return 36; // hot
            case 905: return 24; // windy
            case 906: return 17; // hail
        }

        // Not yet handled - Use generic icon mapping
        Integer condition = ICON_MAPPING.get(icon);
        if (condition != null) {
            return condition;
        }

        return -1;
    }

    private static final HashMap<String, String> LANGUAGE_CODE_MAPPING = new HashMap<String, String>();
    static {
        LANGUAGE_CODE_MAPPING.put("bg-", "bg");
        LANGUAGE_CODE_MAPPING.put("de-", "de");
        LANGUAGE_CODE_MAPPING.put("es-", "sp");
        LANGUAGE_CODE_MAPPING.put("fi-", "fi");
        LANGUAGE_CODE_MAPPING.put("fr-", "fr");
        LANGUAGE_CODE_MAPPING.put("it-", "it");
        LANGUAGE_CODE_MAPPING.put("nl-", "nl");
        LANGUAGE_CODE_MAPPING.put("pl-", "pl");
        LANGUAGE_CODE_MAPPING.put("pt-", "pt");
        LANGUAGE_CODE_MAPPING.put("ro-", "ro");
        LANGUAGE_CODE_MAPPING.put("ru-", "ru");
        LANGUAGE_CODE_MAPPING.put("se-", "se");
        LANGUAGE_CODE_MAPPING.put("tr-", "tr");
        LANGUAGE_CODE_MAPPING.put("uk-", "ua");
        LANGUAGE_CODE_MAPPING.put("zh-CN", "zh_cn");
        LANGUAGE_CODE_MAPPING.put("zh-TW", "zh_tw");
    }
    private String getLanguageCode() {
        Locale locale = mContext.getResources().getConfiguration().locale;
        String selector = locale.getLanguage() + "-" + locale.getCountry();

        for (Map.Entry<String, String> entry : LANGUAGE_CODE_MAPPING.entrySet()) {
            if (selector.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }

        return "en";
    }
}
