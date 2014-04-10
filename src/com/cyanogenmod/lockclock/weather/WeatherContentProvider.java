
package com.cyanogenmod.lockclock.weather;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import com.cyanogenmod.lockclock.misc.Preferences;
import com.cyanogenmod.lockclock.weather.WeatherInfo.DayForecast;

public class WeatherContentProvider extends ContentProvider {

    public static final String TAG = WeatherContentProvider.class.getSimpleName();
    private static final boolean DEBUG = false;

    static WeatherInfo sCachedWeatherInfo;

    private static final int URI_TYPE_EVERYTHING = 1;
    private static final int URI_TYPE_CURRENT = 2;
    private static final int URI_TYPE_FORECAST = 3;

    private static final String COLUMN_CURRENT_CITY_ID = "city_id";
    private static final String COLUMN_CURRENT_CITY = "city";
    private static final String COLUMN_CURRENT_CONDITION = "condition";
    private static final String COLUMN_CURRENT_TEMPERATURE = "temperature";
    private static final String COLUMN_CURRENT_HUMIDITY = "humidity";
    private static final String COLUMN_CURRENT_WIND = "wind";
    private static final String COLUMN_CURRENT_TIME_STAMP = "time_stamp";

    private static final String COLUMN_FORECAST_LOW = "forecast_low";
    private static final String COLUMN_FORECAST_HIGH = "forecast_high";
    private static final String COLUMN_FORECAST_CONDITION = "forecast_condition";

    private static final String[] PROJECTION_DEFAULT_CURRENT = new String[] {
            COLUMN_CURRENT_CITY_ID,
            COLUMN_CURRENT_CITY,
            COLUMN_CURRENT_CONDITION,
            COLUMN_CURRENT_TEMPERATURE,
            COLUMN_CURRENT_HUMIDITY,
            COLUMN_CURRENT_WIND,
            COLUMN_CURRENT_TIME_STAMP
    };

    private static final String[] PROJECTION_DEFAULT_FORECAST = new String[] {
            COLUMN_FORECAST_LOW,
            COLUMN_FORECAST_HIGH,
            COLUMN_FORECAST_CONDITION,
    };

    private static final String[] PROJECTION_DEFAULT_EVERYTHING = new String[] {
            COLUMN_CURRENT_CITY_ID,
            COLUMN_CURRENT_CITY,
            COLUMN_CURRENT_CONDITION,
            COLUMN_CURRENT_TEMPERATURE,
            COLUMN_CURRENT_HUMIDITY,
            COLUMN_CURRENT_WIND,
            COLUMN_CURRENT_TIME_STAMP,

            COLUMN_FORECAST_LOW,
            COLUMN_FORECAST_HIGH,
            COLUMN_FORECAST_CONDITION,
    };

    public static final String AUTHORITY = "com.cyanogenmod.lockclock.weather.provider";

    private static final UriMatcher sUriMatcher;
    static {
        sUriMatcher = new UriMatcher(URI_TYPE_EVERYTHING);
        sUriMatcher.addURI(AUTHORITY, "weather", URI_TYPE_EVERYTHING);
        sUriMatcher.addURI(AUTHORITY, "weather/current", URI_TYPE_CURRENT);
        sUriMatcher.addURI(AUTHORITY, "weather/forecast", URI_TYPE_FORECAST);
    }

    private Context mContext;

    @Override
    public boolean onCreate() {
        mContext = getContext();
        sCachedWeatherInfo = Preferences.getCachedWeatherInfo(mContext);
        return true;
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder) {

        final int projectionType = sUriMatcher.match(uri);
        final MatrixCursor result = new MatrixCursor(resolveProjection(projection, projectionType));

        WeatherInfo weather = sCachedWeatherInfo;
        if (weather != null) {
            // current
            result.newRow()
                    .add(COLUMN_CURRENT_CITY, weather.getCity())
                    .add(COLUMN_CURRENT_CITY_ID, weather.getId())
                    .add(COLUMN_CURRENT_CONDITION, weather.getCondition())
                    .add(COLUMN_CURRENT_HUMIDITY, weather.getFormattedHumidity())
                    .add(COLUMN_CURRENT_WIND, weather.getFormattedWindSpeed()
                            + " " + weather.getWindDirection())
                    .add(COLUMN_CURRENT_TEMPERATURE, weather.getFormattedTemperature())
                    .add(COLUMN_CURRENT_TIME_STAMP, weather.getTimestamp().toString());

            // forecast
            for (DayForecast day : weather.getForecasts()) {
                result.newRow()
                        .add(COLUMN_FORECAST_CONDITION, day.getCondition(mContext))
                        .add(COLUMN_FORECAST_LOW, day.getFormattedLow())
                        .add(COLUMN_FORECAST_HIGH, day.getFormattedHigh());
            }
            return result;
        } else {
            if (DEBUG) Log.e(TAG, "sCachedWeatherInfo is null");
        }
        return null;
    }

    private String[] resolveProjection(String[] projection, int uriType) {
        if (projection != null)
            return projection;
        switch (uriType) {
            default:
            case URI_TYPE_EVERYTHING:
                return PROJECTION_DEFAULT_EVERYTHING;

            case URI_TYPE_CURRENT:
                return PROJECTION_DEFAULT_CURRENT;

            case URI_TYPE_FORECAST:
                return PROJECTION_DEFAULT_FORECAST;
        }
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    public static void updateCachedWeatherInfo(Context context, WeatherInfo info) {
        if (DEBUG) Log.e(TAG, "updateCachedWeatherInfo()");
        if(info != null) {
            if (DEBUG) Log.e(TAG, "set new weather info");
            sCachedWeatherInfo = WeatherInfo.fromSerializedString(context, info.toSerializedString());
        } else {
            if(DEBUG) Log.e(TAG, "nulled out cached weather info");
            sCachedWeatherInfo = null;
        }
        context.getContentResolver().notifyChange(
                Uri.parse("content://" + WeatherContentProvider.AUTHORITY + "/weather"), null);
    }

}
